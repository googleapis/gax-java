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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * A {@link ManagedChannel} that will send requests round robin via a set of channels.
 *
 * <p>Package-private for internal use.
 */
class ChannelPool extends ManagedChannel {
  // size greater than 1 to allow multiple channel to refresh at the same time
  // size not too large so refreshing channels doesn't use too many threads
  private static final int CHANNEL_REFRESH_EXECUTOR_SIZE = 2;
  private final ImmutableList<ManagedChannel> channels;
  private final AtomicInteger indexTicker = new AtomicInteger();
  private final String authority;
  // if set, ChannelPool will manage the life cycle of channelRefreshExecutorService
  @Nullable private ScheduledExecutorService channelRefreshExecutorService;

  /**
   * Factory method to create a non-refreshing channel pool
   *
   * @param poolSize number of channels in the pool
   * @param channelFactory method to create the channels
   * @return ChannelPool of non refreshing channels
   */
  static ChannelPool create(int poolSize, final ChannelFactory channelFactory) throws IOException {
    List<ManagedChannel> channels = new ArrayList<>();
    for (int i = 0; i < poolSize; i++) {
      channels.add(channelFactory.createSingleChannel());
    }
    return new ChannelPool(channels, null);
  }

  /**
   * Factory method to create a refreshing channel pool
   *
   * <p>Package-private for testing purposes only
   *
   * @param poolSize number of channels in the pool
   * @param channelFactory method to create the channels
   * @param channelRefreshExecutorService periodically refreshes the channels; its life cycle will
   *     be managed by ChannelPool
   * @return ChannelPool of refreshing channels
   */
  @VisibleForTesting
  static ChannelPool createRefreshing(
      int poolSize,
      final ChannelFactory channelFactory,
      ScheduledExecutorService channelRefreshExecutorService)
      throws IOException {
    List<ManagedChannel> channels = new ArrayList<>();
    for (int i = 0; i < poolSize; i++) {
      channels.add(new RefreshingManagedChannel(channelFactory, channelRefreshExecutorService));
    }
    return new ChannelPool(channels, channelRefreshExecutorService);
  }

  /**
   * Factory method to create a refreshing channel pool
   *
   * @param poolSize number of channels in the pool
   * @param channelFactory method to create the channels
   * @return ChannelPool of refreshing channels
   */
  static ChannelPool createRefreshing(int poolSize, final ChannelFactory channelFactory)
      throws IOException {
    return createRefreshing(
        poolSize, channelFactory, Executors.newScheduledThreadPool(CHANNEL_REFRESH_EXECUTOR_SIZE));
  }

  /**
   * Initializes the channel pool. Assumes that all channels have the same authority.
   *
   * @param channels a List of channels to pool.
   * @param channelRefreshExecutorService periodically refreshes the channels
   */
  private ChannelPool(
      List<ManagedChannel> channels,
      @Nullable ScheduledExecutorService channelRefreshExecutorService) {
    this.channels = ImmutableList.copyOf(channels);
    authority = channels.get(0).authority();
    this.channelRefreshExecutorService = channelRefreshExecutorService;
  }

  /** {@inheritDoc} */
  @Override
  public String authority() {
    return authority;
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
    return getNextChannel().newCall(methodDescriptor, callOptions);
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdown() {
    for (ManagedChannel channelWrapper : channels) {
      channelWrapper.shutdown();
    }
    if (channelRefreshExecutorService != null) {
      channelRefreshExecutorService.shutdown();
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
    if (channelRefreshExecutorService != null && !channelRefreshExecutorService.isShutdown()) {
      return false;
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
    if (channelRefreshExecutorService != null && !channelRefreshExecutorService.isTerminated()) {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdownNow() {
    for (ManagedChannel channel : channels) {
      channel.shutdownNow();
    }
    if (channelRefreshExecutorService != null) {
      channelRefreshExecutorService.shutdownNow();
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
    if (channelRefreshExecutorService != null) {
      long awaitTimeNanos = endTimeNanos - System.nanoTime();
      channelRefreshExecutorService.awaitTermination(awaitTimeNanos, TimeUnit.NANOSECONDS);
    }
    return isTerminated();
  }

  /**
   * Performs a simple round robin on the list of {@link ManagedChannel}s in the {@code channels}
   * list.
   *
   * @return A {@link ManagedChannel} that can be used for a single RPC call.
   */
  private ManagedChannel getNextChannel() {
    return getChannel(indexTicker.getAndIncrement());
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
    int index = affinity % channels.size();
    index = Math.abs(index);
    // If index is the most negative int, abs(index) is still negative.
    if (index < 0) {
      index = 0;
    }
    return channels.get(index);
  }
}
