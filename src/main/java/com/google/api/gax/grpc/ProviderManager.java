/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * ProviderManager gives a way to manage the lazy creation of channels and executors that
 * are shared among multiple API wrappers.
 *
 * <pre>
 * <code>
 *   ProviderManager providerManager =
 *     ProviderManager.newBuilder()
 *       .setChannelProvider(SomeApiSettings.defaultChannelProviderBuilder().build())
 *       .setExecutorProvider(SomeApiSettings.defaultExecutorProviderBuilder().build())
 *       .build();
 *
 *   SomeApiSettings settingsA =
 *     SomeApiSettings.defaultBuilder()
 *       .setExecutorProvider(providerManager)
 *       .setChannelProvider(providerManager)
 *       .build();
 *   SomeApiSettings settingsB =
 *     SomeApiSettings.defaultBuilder()
 *       .setExecutorProvider(providerManager)
 *       .setChannelProvider(providerManager)
 *       .build();
 *
 *   providerManager.shutdown();
 * </code>
 * </pre>
 */
public class ProviderManager implements ChannelProvider, ExecutorProvider {
  private final InstantiatingChannelProvider channelProvider;
  private final InstantiatingExecutorProvider executorProvider;
  private ManagedChannel channel;
  private ScheduledExecutorService executor;

  private ProviderManager(
      InstantiatingChannelProvider channelProvider,
      InstantiatingExecutorProvider executorProvider) {
    this.channelProvider = channelProvider;
    this.executorProvider = executorProvider;
  }

  @Override
  public boolean shouldAutoClose() {
    return false;
  }

  @Override
  public ScheduledExecutorService getExecutor() {
    if (executor == null) {
      executor = executorProvider.getExecutor();
    }
    return executor;
  }

  @Override
  public boolean needsExecutor() {
    return false;
  }

  @Override
  public ManagedChannel getChannel() throws IOException {
    if (channel == null) {
      if (channelProvider.needsExecutor()) {
        channel = channelProvider.getChannel(getExecutor());
      } else {
        channel = channelProvider.getChannel();
      }
    }
    return channel;
  }

  @Override
  public ManagedChannel getChannel(Executor executor) {
    throw new IllegalStateException("getChannel(Executor) called when needsExecutor() is false");
  }

  public void shutdown() {
    if (channel != null) {
      channel.shutdown();
    }
    if (executor != null) {
      executor.shutdown();
    }
  }

  public void shutdownNow() {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private InstantiatingChannelProvider channelProvider;
    private InstantiatingExecutorProvider executorProvider;

    private Builder() {}

    public Builder setChannelProvider(InstantiatingChannelProvider channelProvider) {
      this.channelProvider = channelProvider;
      return this;
    }

    public Builder setExecutorProvider(InstantiatingExecutorProvider executorProvider) {
      this.executorProvider = executorProvider;
      return this;
    }

    public ProviderManager build() {
      return new ProviderManager(channelProvider, executorProvider);
    }
  }
}
