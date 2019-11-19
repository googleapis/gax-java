/*
 * Copyright 2019 Google LLC
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
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.threeten.bp.Duration;

/**
 * A {@link ManagedChannel} that will refresh the underlying channel by swapping the underlying
 * channel with a new one periodically
 *
 * <p>Package-private for internal use.
 *
 * <p>A note on the synchronization logic. refreshChannel is called periodically which updates
 * delegate and nextScheduledRefresh. lock is needed to provide atomic access and update of delegate
 * and nextScheduledRefresh. One example is newCall needs to be atomic to avoid context switching to
 * refreshChannel that shuts down delegate before newCall is completed.
 */
class RefreshingManagedChannel extends ManagedChannel {
  private static final Logger LOG = Logger.getLogger(RefreshingManagedChannel.class.getName());
  // refresh every 50 minutes with 15% jitter for a range of 42.5min to 57.5min
  private static final Duration refreshPeriod = Duration.ofMinutes(50);
  private static final double jitterPercentage = 0.15;
  private volatile SafeShutdownManagedChannel delegate;
  private volatile ScheduledFuture<?> nextScheduledRefresh;
  // Read: method calls on delegate and nextScheduledRefresh
  // Write: updating references of delegate and nextScheduledRefresh
  private final ReadWriteLock lock;
  private final ChannelFactory channelFactory;
  private final ScheduledExecutorService scheduledExecutorService;

  RefreshingManagedChannel(
      ChannelFactory channelFactory, ScheduledExecutorService scheduledExecutorService)
      throws IOException {
    this.delegate = new SafeShutdownManagedChannel(channelFactory.createSingleChannel());
    this.channelFactory = channelFactory;
    this.scheduledExecutorService = scheduledExecutorService;
    this.lock = new ReentrantReadWriteLock();
    this.nextScheduledRefresh = scheduleNextRefresh();
  }

  /**
   * Refresh the existing channel by swapping the current channel with a new channel and schedule
   * the next refresh
   *
   * <p>refreshChannel can only be called by scheduledExecutorService and not any other methods in
   * this class. This is important so no threads will try to acquire the write lock while holding
   * the read lock.
   */
  private void refreshChannel() {
    SafeShutdownManagedChannel newChannel;
    try {
      newChannel = new SafeShutdownManagedChannel(channelFactory.createSingleChannel());
    } catch (IOException ioException) {
      LOG.log(
          Level.WARNING,
          "Failed to create a new channel when refreshing channel. This has no effect on the "
              + "existing channels. The existing channel will continue to be used",
          ioException);
      return;
    }

    SafeShutdownManagedChannel oldChannel = delegate;
    lock.writeLock().lock();
    try {
      // This thread can be interrupted by invoking cancel on nextScheduledRefresh
      // Interrupt happens when this thread is blocked on acquiring the write lock because shutdown
      // was called and that thread holds the read lock.
      // When shutdown completes and releases the read lock and this thread acquires the write lock.
      // This thread should not continue because the channel has shutdown. This check ensures that
      // this thread terminates without swapping the channel and do not schedule the next refresh.
      if (Thread.currentThread().isInterrupted()) {
        newChannel.shutdownNow();
        return;
      }
      delegate = newChannel;
      nextScheduledRefresh = scheduleNextRefresh();
    } finally {
      lock.writeLock().unlock();
    }
    oldChannel.shutdownSafely();
  }

  /** Schedule the next instance of refreshing this channel */
  private ScheduledFuture<?> scheduleNextRefresh() {
    long delayPeriod = refreshPeriod.toMillis();
    long jitter = (long) ((Math.random() - 0.5) * jitterPercentage * delayPeriod);
    long delay = jitter + delayPeriod;
    return scheduledExecutorService.schedule(
        new Runnable() {
          @Override
          public void run() {
            refreshChannel();
          }
        },
        delay,
        TimeUnit.MILLISECONDS);
  }

  /** {@inheritDoc} */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
    lock.readLock().lock();
    try {
      return delegate.newCall(methodDescriptor, callOptions);
    } finally {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public String authority() {
    // no lock here because authority is constant across all channels
    return delegate.authority();
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdown() {
    lock.readLock().lock();
    try {
      nextScheduledRefresh.cancel(true);
      delegate.shutdown();
      return this;
    } finally {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdownNow() {
    lock.readLock().lock();
    try {
      nextScheduledRefresh.cancel(true);
      delegate.shutdownNow();
      return this;
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
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    lock.readLock().lock();
    try {
      return delegate.awaitTermination(timeout, unit);
    } finally {
      lock.readLock().unlock();
    }
  }
}
