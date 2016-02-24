/*
 * Copyright 2015, Google Inc.
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

import com.google.api.gax.core.ConnectionSettings;
import com.google.api.gax.core.RetryParams;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;

import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.Status;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.List;

// TODO(pongad): Don't close the channel if the user gives one to us
/**
 * A settings class to configure a service api class.
 *
 * A note on channels: whichever service API class that this instance of ServiceApiSettings
 * is passed to will call shutdown() on the channel provided by {@link getChannel}.
 * Setting a channel is intended for use by unit tests to override the channel,
 * and should not be used in production.
 */
@AutoValue
public abstract class ServiceApiSettings<MethodId> {

  interface ChannelProvider {
    ManagedChannel getChannel(Executor executor) throws IOException;
  }

  public static final int DEFAULT_EXECUTOR_THREADS = 4;
  private static final ScheduledExecutorService DEFAULT_EXECUTOR =
      MoreExecutors.getExitingScheduledExecutorService(
          new ScheduledThreadPoolExecutor(DEFAULT_EXECUTOR_THREADS));

  /**
   * Status codes that are considered to be retryable by the given methods
   */
  public abstract ImmutableMap<MethodId, ImmutableSet<Status.Code>> getRetryableCodes();

  /**
   * Retry/backoff configuration for each method
   */
  public abstract ImmutableMap<MethodId, RetryParams> getRetryParams();

  /**
   * The executor to be used by the client.
   *
   * If none is set by the corresponding method in {@link Builder},
   * a default {@link java.util.concurrent.ScheduledThreadPoolExecutor}
   * with {@link DEFAULT_EXECUTOR_THREADS} is used.
   * The default executor is guaranteed to not prevent JVM from normally exitting,
   * but may wait for up to 120 seconds after all non-daemon threads exit to give received tasks
   * time to complete.
   * If this behavior is not desirable, the user may specify a custom {@code Executor}.
   *
   * If a custom {@code Executor} is specified by the corresponding method,
   * it is up to the user to terminate the {@code Executor} when it is no longer needed.
   */
  public abstract ScheduledExecutorService getExecutor();

  /**
   * The channel used to send requests to the service.
   * See class documentation on channels.
   */
  public ManagedChannel getChannel() throws IOException {
    return getChannelProvider().getChannel(getExecutor());
  }

  abstract ChannelProvider getChannelProvider();

  public static <MethodId> Builder<MethodId> builder() {
    return new AutoValue_ServiceApiSettings.Builder<MethodId>()
        .setRetryableCodes(ImmutableMap.<MethodId, ImmutableSet<Status.Code>>of())
        .setRetryParams(ImmutableMap.<MethodId, RetryParams>of())
        .setExecutor(DEFAULT_EXECUTOR);
  }

  public Builder<MethodId> toBuilder() {
    return new AutoValue_ServiceApiSettings.Builder<MethodId>(this);
  }

  @AutoValue.Builder
  public abstract static class Builder<MethodId> {
    public abstract Builder<MethodId> setRetryableCodes(
        ImmutableMap<MethodId, ImmutableSet<Status.Code>> codes);

    public abstract Builder<MethodId> setRetryParams(
        ImmutableMap<MethodId, RetryParams> retryParams);

    public Builder<MethodId> provideChannelWith(final ManagedChannel channel) {
      ChannelProvider provider = new ChannelProvider() {
        @Override
        public ManagedChannel getChannel(Executor executor) {
          return channel;
        }
      };
      return setChannelProvider(provider);
    }

    public Builder<MethodId> provideChannelWith(final ConnectionSettings settings) {
      ChannelProvider provider = new ChannelProvider() {
        @Override
        public ManagedChannel getChannel(Executor executor) throws IOException {
          List<ClientInterceptor> interceptors = Lists.newArrayList();
          interceptors.add(new ClientAuthInterceptor(settings.getCredentials(), executor));

          return NettyChannelBuilder.forAddress(settings.getServiceAddress(), settings.getPort())
              .negotiationType(NegotiationType.TLS)
              .intercept(interceptors)
              .build();
        }
      };
      return setChannelProvider(provider);
    }

    abstract Builder<MethodId> setChannelProvider(ChannelProvider provider);

    public abstract Builder<MethodId> setExecutor(ScheduledExecutorService executor);

    public abstract ServiceApiSettings<MethodId> build();
  }
}
