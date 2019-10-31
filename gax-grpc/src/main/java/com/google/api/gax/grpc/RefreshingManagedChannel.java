/*
 * Copyright 2017 Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google LLC nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.api.gax.grpc;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.threeten.bp.Duration;

class RefreshingManagedChannel extends ManagedChannel {
  private static final Logger LOG = Logger.getLogger(RefreshingManagedChannel.class.getName());
  // refresh every 50 minutes with 10% jitter for a range of 45min to 55min
  private static Duration refreshPeriod = Duration.ofMinutes(50);
  private static double jitterPercentage = 0.15;
  private volatile ManagedChannel delegate;
  private volatile AtomicInteger requestCounter;
  private final ReadWriteLock lock;
  private final ChannelFactory channelFactory;
  private final ScheduledExecutorService scheduledExecutorService;
  private ScheduledFuture<?> nextScheduledRefresh;
  private AtomicBoolean isShutdown;

  RefreshingManagedChannel(
      ChannelFactory channelFactory, ScheduledExecutorService scheduledExecutorService)
      throws IOException {
    this.delegate = channelFactory.createSingleChannel();
    this.channelFactory = channelFactory;
    this.scheduledExecutorService = scheduledExecutorService;
    this.requestCounter = new AtomicInteger(0);
    this.lock = new ReentrantReadWriteLock();
    this.nextScheduledRefresh = scheduleNextRefresh();
    this.isShutdown = new AtomicBoolean(false);
  }

  private void refreshChannel() {
    ManagedChannel newChannel;
    try {
      newChannel = channelFactory.createSingleChannel();
    } catch (IOException ioException) {
      LOG.log(
          Level.WARNING,
          "Failed to create a new channel when refreshing channel. This has no effect on the "
              + "existing channels. The existing channel will continue to be used",
          ioException);
      return;
    }

    ManagedChannel oldChannel = delegate;
    AtomicInteger oldRequestCounter = requestCounter;
    AtomicBoolean oldIsShutdown = isShutdown;
    // Atomically update the channel and counter so that new requests use the new channel and
    // counter
    lock.writeLock().lock();
    try {
      delegate = newChannel;
      requestCounter = new AtomicInteger(0);
      isShutdown = new AtomicBoolean(false);
      nextScheduledRefresh = scheduleNextRefresh();
    } finally {
      lock.writeLock().unlock();
    }
    // the ordering of setting oldIsShutdown to true before checking oldRequestCounter is important.
    // This is the opposite ordering from clientCallWrapper. This ensures shutdown on oldChannel is
    // called at least once
    oldIsShutdown.set(true);
    if (oldRequestCounter.get() == 0) {
      oldChannel.shutdownNow();
    }
  }

  /**
   * Add up to jitter to val
   *
   * @param val starting value to add jitter to
   * @return value with jitter
   */
  private long addJitter(long val) {
    return (long) ((Math.random() - 0.5) * val * jitterPercentage) + val;
  }

  /** Schedule the next instance of refreshing this channel */
  private ScheduledFuture<?> scheduleNextRefresh() {
    return scheduledExecutorService.schedule(
        new Runnable() {
          @Override
          public void run() {
            refreshChannel();
          }
        },
        addJitter(refreshPeriod.toMillis()),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Wrap client call to decrement oustandingRequest counter when the call completes
   *
   * @param call call to be wrapped
   * @param requestCounter counter to decrement when the call completes
   * @return the wrapped call
   */
  private <ReqT, RespT> ClientCall<ReqT, RespT> clientCallWrapper(
      ClientCall<ReqT, RespT> call,
      final AtomicInteger requestCounter,
      final AtomicBoolean isShutdown,
      final ManagedChannel channel) {
    return new SimpleForwardingClientCall<ReqT, RespT>(call) {
      public void start(Listener<RespT> responseListener, Metadata headers) {
        Listener<RespT> forwardingResponseListener =
            new SimpleForwardingClientCallListener<RespT>(responseListener) {

              @Override
              public void onClose(Status status, Metadata trailers) {
                super.onClose(status, trailers);
                // the ordering of decrementing and checking requestCounter before checking
                // isShutdown is important. This is the opposite ordering from refreshChannel. This
                // ensures shutdown on channel is called at least once
                if (requestCounter.decrementAndGet() == 0 && isShutdown.get()) {
                  channel.shutdownNow();
                }
              }
            };
        super.start(forwardingResponseListener, headers);
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
    lock.readLock().lock();
    try {
      requestCounter.incrementAndGet();
      ClientCall<ReqT, RespT> call = delegate.newCall(methodDescriptor, callOptions);
      // return a forwarding client call with a listener that will decrement the requestCounter
      // when the request completes
      return clientCallWrapper(call, requestCounter, isShutdown, delegate);
    } finally {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public String authority() {
    lock.readLock().lock();
    try {
      return delegate.authority();
    } finally {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdown() {
    lock.readLock().lock();
    try {
      isShutdown.set(true);
      nextScheduledRefresh.cancel(true);
      return delegate.shutdown();
    } finally {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isShutdown() {
    lock.readLock().lock();
    try {
      return delegate.isShutdown();
    } finally {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTerminated() {
    lock.readLock().lock();
    try {
      return delegate.isTerminated();
    } finally {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdownNow() {
    lock.readLock().lock();
    try {
      isShutdown.set(true);
      nextScheduledRefresh.cancel(true);
      return delegate.shutdownNow();
    } finally {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    lock.readLock().lock();
    try {
      return delegate.awaitTermination(timeout, unit);
    } finally {
      lock.readLock().unlock();
    }
  }
}
