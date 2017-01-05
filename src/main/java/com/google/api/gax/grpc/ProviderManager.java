/*
 * Copyright 2016, Google Inc. All rights reserved.
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
 * ProviderManager gives a way to manage the lazy creation of channels and executors that are shared
 * among multiple service API wrappers. The given ExecutorProvider is called no more than once and
 * cached, and same with the given ChannelProvider. After all of the service API wrappers are no
 * longer in use, shutdown() should be called to clean up the executor and channel.
 *
 * <pre>
 * <code>
 *   ProviderManager providerManager =
 *     ProviderManager.newBuilder()
 *       .setExecutorProvider(SomeApiSettings.defaultExecutorProviderBuilder().build())
 *       .setChannelProvider(SomeApiSettings.defaultChannelProviderBuilder().build())
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
 *   // ... create SomeApi, make calls, etc ...
 *
 *   providerManager.shutdown();
 * </code>
 * </pre>
 */
public class ProviderManager implements ExecutorProvider, ChannelProvider {
  private final ExecutorProvider executorProvider;
  private final ChannelProvider channelProvider;
  private ManagedChannel channel;
  private ScheduledExecutorService executor;
  private Object lock = new Object();

  private ProviderManager(ExecutorProvider executorProvider, ChannelProvider channelProvider) {
    this.executorProvider = executorProvider;
    this.channelProvider = channelProvider;
  }

  @Override
  public boolean shouldAutoClose() {
    return false;
  }

  @Override
  public ScheduledExecutorService getExecutor() {
    synchronized (lock) {
      if (executor == null) {
        executor = executorProvider.getExecutor();
      }
    }
    return executor;
  }

  @Override
  public boolean needsExecutor() {
    return false;
  }

  @Override
  public ManagedChannel getChannel() throws IOException {
    synchronized (lock) {
      if (channel == null) {
        if (channelProvider.needsExecutor()) {
          channel = channelProvider.getChannel(getExecutor());
        } else {
          channel = channelProvider.getChannel();
        }
      }
    }
    return channel;
  }

  @Override
  public ManagedChannel getChannel(Executor executor) {
    throw new IllegalStateException("getChannel(Executor) called when needsExecutor() is false");
  }

  public void shutdown() {
    if (channel != null && channelProvider.shouldAutoClose()) {
      channel.shutdown();
    }
    if (executor != null && executorProvider.shouldAutoClose()) {
      executor.shutdown();
    }
  }

  public void shutdownNow() {
    if (channel != null && channelProvider.shouldAutoClose()) {
      channel.shutdownNow();
    }
    if (executor != null && executorProvider.shouldAutoClose()) {
      executor.shutdownNow();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private ExecutorProvider executorProvider;
    private ChannelProvider channelProvider;

    private Builder() {}

    /**
     * Sets the InstantiatingExecutorProvider to create the executor the first time. It will only be
     * called once, and the result will be cached.
     */
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      this.executorProvider = executorProvider;
      return this;
    }

    /**
     * Sets the InstantiatingChannelProvider to create the channel the first time. It will only be
     * called once, and the result will be cached.
     */
    public Builder setChannelProvider(ChannelProvider channelProvider) {
      this.channelProvider = channelProvider;
      return this;
    }

    public ProviderManager build() {
      return new ProviderManager(executorProvider, channelProvider);
    }
  }
}
