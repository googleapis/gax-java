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
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.threeten.bp.Duration;

/**
 * A {@link ManagedChannel} that will send requests round robin via a set of channels.
 *
 * <p>Package-private for internal use.
 */
class ChannelPool extends ManagedChannel {
  private static final Logger LOG = Logger.getLogger(ChannelPool.class.getName());
  // refresh every 55 minutes
  private static Duration refreshPeriod = Duration.ofMinutes(55);
  // maximum seconds to wait for old channel to terminate
  static Duration terminationWait = Duration.ofMinutes(1);

  private final List<ManagedChannel> channels;
  private final List<AtomicInteger> requestCounters;
  private final AtomicInteger indexTicker = new AtomicInteger();
  private final List<ReadWriteLock> locks;
  private final String authority;
  private final boolean isRefreshing;

  /**
   * Initializes the channel pool. Assumes that all channels have the same authority.
   *
   * @param poolSize number of channels in the pool.
   * @param channelFactory method to create the channels.
   * @param executorService if set, schedule periodically refresh the channels.
   */
  ChannelPool(
      int poolSize, final ChannelFactory channelFactory, ScheduledExecutorService executorService)
      throws IOException {
    channels = new Vector<>(poolSize);
    requestCounters = new Vector<>(poolSize);
    locks = new Vector<>(poolSize);
    for (int i = 0; i < poolSize; i++) {
      channels.add(channelFactory.createSingleChannel());
      requestCounters.add(new AtomicInteger(0));
      locks.add(new ReentrantReadWriteLock());
    }
    // if executorService is available, schedule channels to be refreshed
    isRefreshing = executorService != null;

    if (isRefreshing) {
      // stagger the refresh to not overload GFE
      // spread out the refresh between channels
      Duration refreshDelay = refreshPeriod.dividedBy(poolSize);

      // runnable inner class to refresh a channel in the list of channels
      class RefreshSingleChannel implements Runnable {
        private final int index;
        private final List<ManagedChannel> channels;

        private RefreshSingleChannel(List<ManagedChannel> channels, int index) {
          this.channels = channels;
          this.index = index;
        }

        @Override
        public void run() {
          ManagedChannel newChannel;
          try {
            newChannel = channelFactory.createSingleChannel();
          } catch (IOException ioException) {
            String message =
                String.format(
                    "Failed to create a new channel when refreshing channel %d. This has no effect "
                        + "on the existing channels. The existing channel will continue to be used",
                    index);
            LOG.log(Level.WARNING, message, ioException);
            return;
          }

          ManagedChannel oldChannel = channels.get(index);
          AtomicInteger oldOutstandingRequestCounter = requestCounters.get(index);
          // Atomically update the channel and counter so that new requests use the new channel and
          // counter
          locks.get(index).writeLock().lock();
          try {
            channels.set(index, newChannel);
            requestCounters.set(index, new AtomicInteger(0));
          } finally {
            locks.get(index).writeLock().unlock();
          }

          // Wait for there to be no more request on the old channel
          // Note that once the counter reaches 0, it should not increase because all new request
          // are sent to the new channel
          try {
            while (oldOutstandingRequestCounter.get() != 0) {
              TimeUnit.MILLISECONDS.sleep(terminationWait.toMillis());
            }
          } catch (InterruptedException e) {
            return;
          }
          oldChannel.shutdown();
        }
      }

      for (int index = 0; index < poolSize; index++) {
        executorService.scheduleAtFixedRate(
            new RefreshSingleChannel(channels, index),
            addJitter(
                refreshPeriod.minus(refreshDelay.multipliedBy(index)).getSeconds(),
                refreshPeriod.getSeconds()),
            refreshPeriod.getSeconds(),
            TimeUnit.SECONDS);
      }
    }
    authority = channels.get(0).authority();
  }

  /**
   * Add up to 20% jitter to val with the upper limit of max
   *
   * @param val starting value to add jitter to
   * @param max the maximum total value the result can be
   * @return the value with jitter
   */
  private long addJitter(long val, long max) {
    long maxJitter = (long) Math.ceil(val * 0.2);
    return Math.min(max, (long) ((Math.random() - 0.5) * maxJitter) + val);
  }

  /** {@inheritDoc} */
  @Override
  public String authority() {
    return authority;
  }

  /** To decrement the requestCounter when the request completes */
  class DecrementOutstandingRequestInterceptor implements ClientInterceptor {
    AtomicInteger requestCounter;

    DecrementOutstandingRequestInterceptor(AtomicInteger requestCounter) {
      this.requestCounter = requestCounter;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions, Channel next) {

      ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);

      return new SimpleForwardingClientCall<ReqT, RespT>(call) {
        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
          Listener<RespT> forwardingResponseListener =
              new SimpleForwardingClientCallListener<RespT>(responseListener) {

                @Override
                public void onClose(Status status, Metadata trailers) {
                  super.onClose(status, trailers);
                  requestCounter.decrementAndGet();
                }
              };
          super.start(forwardingResponseListener, headers);
        }
      };
    }
  }

  /**
   * Create a {@link ClientCall} on a Channel from the pool chosen in a round-robin fashion to the
   * remote operation specified by the given {@link MethodDescriptor}. The returned {@link
   * ClientCall} does not trigger any remote behavior until {@link
   * ClientCall#start(ClientCall.Listener, io.grpc.Metadata)} is invoked.
   */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {

    final int nextChannelIndex = getNextChannelIndex();
    if (!isRefreshing) {
      return getChannel(nextChannelIndex).newCall(methodDescriptor, callOptions);
    }

    // atomically create a new call on the channel and increment the request count for that channel
    locks.get(nextChannelIndex).readLock().lock();
    try {
      final AtomicInteger outstandingRequest = requestCounters.get(nextChannelIndex);
      outstandingRequest.incrementAndGet();
      return ClientInterceptors.intercept(
              getChannel(nextChannelIndex),
              new DecrementOutstandingRequestInterceptor(outstandingRequest))
          .newCall(methodDescriptor, callOptions);
    } finally {
      locks.get(nextChannelIndex).readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdown() {
    for (ManagedChannel channelWrapper : channels) {
      channelWrapper.shutdown();
    }

    return this;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isShutdown() {
    for (ManagedChannel channel : channels) {
      if (!channel.isShutdown()) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTerminated() {
    for (ManagedChannel channel : channels) {
      if (!channel.isTerminated()) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdownNow() {
    for (ManagedChannel channel : channels) {
      channel.shutdownNow();
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long endTimeNanos = System.nanoTime() + unit.toNanos(timeout);
    for (ManagedChannel channel : channels) {
      long awaitTimeNanos = endTimeNanos - System.nanoTime();
      if (awaitTimeNanos <= 0) {
        break;
      }
      channel.awaitTermination(awaitTimeNanos, TimeUnit.NANOSECONDS);
    }

    return isTerminated();
  }

  /**
   * Performs a simple round robin on the list of {@link ManagedChannel}s in the {@code channels}
   * list.
   *
   * @return index of the {@link ManagedChannel} that can be used for a single RPC call.
   */
  private int getNextChannelIndex() {
    return getChannelIndex(indexTicker.getAndIncrement());
  }

  /**
   * Returns one of the channels managed by this pool. The pool continues to "own" the channel, and
   * the caller should not shut it down.
   *
   * @param affinity Two calls to this method with the same affinity returns the same channel. The
   *     reverse is not true: Two calls with different affinities might return the same channel.
   *     However, the implementation should attempt to spread load evenly.
   */
  private int getChannelIndex(int affinity) {
    int index = affinity % channels.size();
    index = Math.abs(index);
    // If index is the most negative int, abs(index) is still negative.
    if (index < 0) {
      index = 0;
    }
    return index;
  }

  /**
   * Returns one of the channels managed by this pool. The pool continues to "own" the channel, and
   * the caller should not shut it down.
   *
   * @param affinity Two calls to this method with the same affinity returns the same channel. The
   *     reverse is not true: Two calls with different affinities might return the same channel.
   *     However, the implementation should attempt to spread load evenly.
   */
  ManagedChannel getChannel(int affinity) {
    return channels.get(getChannelIndex(affinity));
  }
}
