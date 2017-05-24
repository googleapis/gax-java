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
import com.google.auth.Credentials;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;

/**
 * Encapsulates client state, including channel, executor, and credentials.
 *
 * <p>Unlike {@link ClientSettings} which allows users to configure the client, {@code
 * ClientContext} is intended to be used in generated code. Most users will not need to use it.
 */
@BetaApi
@AutoValue
public abstract class ClientContext {
  public abstract Collection<AutoCloseable> getCloseables();

  public abstract Channel getChannel();

  public abstract ScheduledExecutorService getExecutor();

  @Nullable
  public abstract Credentials getCredentials();

  static Builder newBuilder() {
    return new AutoValue_ClientContext.Builder()
        .setCloseables(Collections.<AutoCloseable>emptyList());
  }

  public static ClientContext create(ClientSettings settings) throws IOException {
    ImmutableList.Builder<AutoCloseable> closeables = ImmutableList.builder();

    ExecutorProvider executorProvider = settings.getExecutorProvider();
    final ScheduledExecutorService executor = executorProvider.getExecutor();
    if (executorProvider.shouldAutoClose()) {
      closeables.add(
          new AutoCloseable() {
            @Override
            public void close() {
              executor.shutdown();
            }
          });
    }

    final ManagedChannel channel;
    ChannelProvider channelProvider = settings.getChannelProvider();
    if (channelProvider.needsExecutor()) {
      channel = channelProvider.getChannel(executor);
    } else {
      channel = channelProvider.getChannel();
    }
    if (channelProvider.shouldAutoClose()) {
      closeables.add(
          new AutoCloseable() {
            @Override
            public void close() {
              channel.shutdown();
            }
          });
    }
    return newBuilder()
        .setCloseables(closeables.build())
        .setChannel(channel)
        .setExecutor(executor)
        .setCredentials(settings.getCredentialsProvider().getCredentials())
        .build();
  }

  @AutoValue.Builder
  abstract static class Builder {
    public abstract Builder setCloseables(Collection<AutoCloseable> value);

    public abstract Builder setChannel(Channel value);

    public abstract Builder setExecutor(ScheduledExecutorService value);

    public abstract Builder setCredentials(Credentials value);

    public abstract ClientContext build();
  }
}
