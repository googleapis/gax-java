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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

// TODO(pongad): Don't close the channel if the user gives one to us
/**
 * A settings class to configure API method calls for either a single method or a
 * whole service.
 *
 * A note on channels: whichever service API class that this instance of ServiceApiSettings
 * is passed to will call shutdown() on the channel provided by {@link getChannel}.
 * Setting a channel is intended for use by unit tests to override the channel,
 * and should not be used in production.
 */
public class ApiCallSettings {

  private ChannelProvider channelProvider;
  private ExecutorProvider executorProvider;

  private Set<Status.Code> defaultRetryableCodes = null;
  private Set<Status.Code> retryableCodes = null;

  private RetryParams defaultRetryParams = null;
  private RetryParams retryParams = null;

  public static final int DEFAULT_EXECUTOR_THREADS = 4;

  /**
   * Constructs an instance of ApiCallSettings.
   */
  public ApiCallSettings() {
    channelProvider = new ChannelProvider() {
      @Override
      public ManagedChannel getChannel(Executor executor) {
        throw new RuntimeException("No Channel or ConnectionSettings provided.");
      }

      @Override
      public boolean isOverridden() {
        return false;
      }
    };
    executorProvider = new ExecutorProvider() {
      private ScheduledExecutorService executor = null;
      @Override
      public ScheduledExecutorService getExecutor() {
        if (executor != null) {
          return executor;
        }
        executor = MoreExecutors.getExitingScheduledExecutorService(
            new ScheduledThreadPoolExecutor(DEFAULT_EXECUTOR_THREADS));
        return executor;
      }

      @Override
      public boolean isOverridden() {
        return false;
      }
    };
    retryableCodes = new HashSet<>();
  }

  private interface ChannelProvider {
    ManagedChannel getChannel(Executor executor) throws IOException;
    boolean isOverridden();
  }

  /**
   * Sets a channel for this ApiCallSettings to use. This prevents a channel
   * from being created.
   *
   * See class documentation for more details on channels.
   */
  public ApiCallSettings provideChannelWith(final ManagedChannel channel) {
    channelProvider = new ChannelProvider() {
      @Override
      public ManagedChannel getChannel(Executor executor) {
        return channel;
      }

      @Override
      public boolean isOverridden() {
        return true;
      }
    };
    return this;
  }

  /**
   * Provides the connection settings necessary to create a channel.
   */
  public ApiCallSettings provideChannelWith(final ConnectionSettings settings) {
    channelProvider = new ChannelProvider() {
      private ManagedChannel channel = null;
      @Override
      public ManagedChannel getChannel(Executor executor) throws IOException {
        if (channel != null) {
          return channel;
        }

        List<ClientInterceptor> interceptors = Lists.newArrayList();
        interceptors.add(new ClientAuthInterceptor(settings.getCredentials(), executor));

        channel = NettyChannelBuilder.forAddress(settings.getServiceAddress(), settings.getPort())
            .negotiationType(NegotiationType.TLS)
            .intercept(interceptors)
            .build();
        return channel;
      }

      @Override
      public boolean isOverridden() {
        return true;
      }
    };
    return this;
  }

  /**
   * The channel used to send requests to the service.
   *
   * If no channel was set, a default channel will be instantiated, using
   * the connection settings provided.
   *
   * See class documentation for more details on channels.
   */
  public ManagedChannel getChannel() throws IOException {
    return channelProvider.getChannel(getExecutor());
  }

  /**
   * Returns true if either a channel was set or connection settings were
   * provided to create a channel.
   */
  public boolean isChannelOverridden() {
    return channelProvider.isOverridden();
  }

  private interface ExecutorProvider {
    ScheduledExecutorService getExecutor();
    boolean isOverridden();
  }

  /**
   * Sets the executor to use for channels, retries, and bundling.
   *
   * It is up to the user to terminate the {@code Executor} when it is no longer needed.
   */
  public ApiCallSettings setExecutor(final ScheduledExecutorService executor) {
    executorProvider = new ExecutorProvider() {
      @Override
      public ScheduledExecutorService getExecutor() {
        return executor;
      }

      @Override
      public boolean isOverridden() {
        return true;
      }
    };
    return this;
  }

  /**
   * The executor to be used by the client.
   *
   * If no executor was set, a default {@link java.util.concurrent.ScheduledThreadPoolExecutor}
   * with {@link DEFAULT_EXECUTOR_THREADS} will be instantiated.
   *
   * The default executor is guaranteed to not prevent JVM from normally exiting,
   * but may wait for up to 120 seconds after all non-daemon threads exit to give received tasks
   * time to complete.
   *
   * If this behavior is not desirable, the user may specify a custom {@code Executor}.
   */
  public ScheduledExecutorService getExecutor() {
    return executorProvider.getExecutor();
  }

  /**
   * Returns true if an executor was set.
   */
  public boolean isExecutorOverridden() {
    return executorProvider.isOverridden();
  }

  /**
   * Sets the defaults to use for retryable codes and retry params.
   *
   * If setRetryableCodes is not called, the default retryableCodes provided here will be returned
   * from getRetryableCodes. Likewise, if setRetryParams is not called, the default retryParams
   * provided here will be returned from getRetryParams.
   */
  public void setRetryDefaults(Set<Status.Code> retryableCodes, RetryParams retryParams) {
    this.defaultRetryableCodes = retryableCodes;
    this.retryParams = retryParams;
  }

  /**
   * Sets the retryable codes.
   */
  public ApiCallSettings setRetryableCodes(Set<Status.Code> retryableCodes) {
    this.retryableCodes = retryableCodes;
    return this;
  }

  /**
   * Sets the retryable codes.
   */
  public ApiCallSettings setRetryableCodes(Status.Code... codes) {
    this.retryableCodes = Sets.newHashSet(codes);
    return this;
  }

  /**
   * Gets the retryable codes that were set previously, or if they were not, then returns
   * the default retryable codes provided previously.
   */
  public Set<Status.Code> getRetryableCodes() {
    if (isRetryableCodesOverridden()) {
      return retryableCodes;
    } else {
      return defaultRetryableCodes;
    }
  }

  /**
   * Returns true if the retryable codes were set.
   */
  public boolean isRetryableCodesOverridden() {
    return retryableCodes != null;
  }

  /**
   * Sets the retry params.
   */
  public ApiCallSettings setRetryParams(RetryParams retryParams) {
    this.retryParams = retryParams;
    return this;
  }

  /**
   * Returns the retry params that were set previously, or if they were not, then returns
   * the default retry params provided previously.
   */
  public RetryParams getRetryParams() {
    if (isRetryParamsOverridden()) {
      return retryParams;
    } else {
      return defaultRetryParams;
    }
  }

  /**
   * Returns true if the retry params were set.
   */
  public boolean isRetryParamsOverridden() {
    return retryParams != null;
  }
}
