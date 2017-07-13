/*
 * Copyright 2017, Google Inc. All rights reserved.
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
 *     * Neither the name of Google Inc. nor the names of its
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

import com.google.api.core.BetaApi;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.rpc.TransportProvider;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A TransportProvider for grpc.
 *
 * <p>This class provides instances of {@link GrpcTransport} using the given {@link
 * ChannelProvider}.
 *
 * <p>There are multiple ways to configure the channel that will be used:
 *
 * <ol>
 *   <li>Set ChannelProvider to an instance of InstantiatingChannelProvider, which will create a
 *       channel when the service API class is created from the settings class. In this case,
 *       close() should be called on the service API class to shut down the created channel.
 *   <li>Set ChannelProvider to an instance of FixedChannelProvider, which passes through an
 *       already-existing ManagedChannel to the API wrapper class. In this case, calling close() on
 *       the service API class will have no effect on the provided channel.
 * </ol>
 */
@BetaApi
public class GrpcTransportProvider implements TransportProvider {

  private final ChannelProvider channelProvider;

  /** Constructs an instance of GrpcTransportProvider. */
  protected GrpcTransportProvider(ChannelProvider channelProvider) {
    this.channelProvider = Preconditions.checkNotNull(channelProvider);
  }

  /**
   * The {@link ChannelProvider} which will provide instances of {@link ManagedChannel} when {@link
   * #getTransport()} or {@link #getTransport(ScheduledExecutorService)} is called.
   */
  public final ChannelProvider getChannelProvider() {
    return channelProvider;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("channelProvider", channelProvider).toString();
  }

  @Override
  public boolean needsExecutor() {
    return channelProvider.needsExecutor();
  }

  @Override
  public GrpcTransport getTransport() throws IOException {
    ManagedChannel channel = channelProvider.getChannel();
    return GrpcTransport.newBuilder()
        .setChannel(channel)
        .setBackgroundResources(getBackgroundResourcesFor(channel))
        .build();
  }

  @Override
  public GrpcTransport getTransport(ScheduledExecutorService executor) throws IOException {
    ManagedChannel channel = channelProvider.getChannel(executor);
    return GrpcTransport.newBuilder()
        .setChannel(channel)
        .setBackgroundResources(getBackgroundResourcesFor(channel))
        .build();
  }

  @Override
  public String getTransportName() {
    return GrpcTransport.getGrpcTransportName();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  private ImmutableList<BackgroundResource> getBackgroundResourcesFor(
      final ManagedChannel channel) {
    ImmutableList.Builder<BackgroundResource> backgroundResources = ImmutableList.builder();
    if (channelProvider.shouldAutoClose()) {
      backgroundResources.add(new ChannelAsBackgroundResource(channel));
    }
    return backgroundResources.build();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {

    private ChannelProvider channelProvider;

    protected Builder() {}

    /** Create a builder from a GrpcTransportProvider object. */
    protected Builder(GrpcTransportProvider transportProvider) {
      this.channelProvider = transportProvider.channelProvider;
    }

    /** Sets the ChannelProvider to use for getting the channel to make calls with. */
    public Builder setChannelProvider(ChannelProvider channelProvider) {
      this.channelProvider = channelProvider;
      return this;
    }

    /** Gets the ChannelProvider that was previously set on this Builder. */
    public ChannelProvider getChannelProvider() {
      return channelProvider;
    }

    public GrpcTransportProvider build() {
      return new GrpcTransportProvider(channelProvider);
    }
  }
}
