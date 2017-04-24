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

import com.google.api.gax.retrying.RetrySettings;
import io.grpc.Status;
import java.io.IOException;
import java.util.Set;

/**
 * A base settings class to configure a service API class.
 *
 * <p>This base class includes settings that are applicable to all services, which includes things
 * like connection settings for creating a channel, executor, and identifiers for http headers.
 *
 * <p>If no ExecutorProvider is set, then InstantiatingExecutorProvider will be used, which creates
 * a default executor.
 *
 * <p>There are several ways to configure the channel that will be used:
 *
 * <p>
 *
 * <ol>
 *   <li>Set ChannelProvider to an instance of InstantiatingChannelProvider, which will create a
 *       channel when the service API class is created from the settings class. In this case,
 *       close() should be called on the service API class to shut down the created channel.
 *   <li>Set ChannelProvider to an instance of FixedChannelProvider, which passes through an
 *       already-existing ManagedChannel to the API wrapper class. In this case, calling close() on
 *       the service API class will have no effect on the provided channel.
 *   <li>Create an instance of ProviderManager using the default ChannelProvider and
 *       ExecutorProvider for the given service API settings class. In this case, close() should be
 *       called on the ProviderManager once all of the service API objects are no longer in use.
 * </ol>
 */
public abstract class ClientSettings {

  private final ExecutorProvider executorProvider;
  private final ChannelProvider channelProvider;

  /** Constructs an instance of ClientSettings. */
  protected ClientSettings(ExecutorProvider executorProvider, ChannelProvider channelProvider) {
    this.executorProvider = executorProvider;
    this.channelProvider = channelProvider;
  }

  /** Gets a channel and an executor for making calls. */
  public final ChannelAndExecutor getChannelAndExecutor() throws IOException {
    return ChannelAndExecutor.create(executorProvider, channelProvider);
  }

  public final ExecutorProvider getExecutorProvider() {
    return executorProvider;
  }

  public final ChannelProvider getChannelProvider() {
    return channelProvider;
  }

  public abstract static class Builder {

    private ExecutorProvider executorProvider;
    private ChannelProvider channelProvider;

    /** Create a builder from a ClientSettings object. */
    protected Builder(ClientSettings settings) {
      this.executorProvider = settings.executorProvider;
      this.channelProvider = settings.channelProvider;
    }

    protected Builder(InstantiatingChannelProvider channelProvider) {
      this.executorProvider = InstantiatingExecutorProvider.newBuilder().build();
      this.channelProvider = channelProvider;
    }

    /**
     * Sets the ExecutorProvider to use for getting the executor to use for running asynchronous API
     * call logic (such as retries and long-running operations), and also to pass to the
     * ChannelProvider (if the ChannelProvider needs an executor to create a new channel and it
     * doesn't have its own ExecutorProvider).
     */
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      this.executorProvider = executorProvider;
      return this;
    }

    /** Sets the ChannelProvider to use for getting the channel to make calls with. */
    public Builder setChannelProvider(ChannelProvider channelProvider) {
      this.channelProvider = channelProvider;
      return this;
    }

    /** Gets the ExecutorProvider that was previously set on this Builder. */
    public ExecutorProvider getExecutorProvider() {
      return executorProvider;
    }

    /** Gets the ChannelProvider that was previously set on this Builder. */
    public ChannelProvider getChannelProvider() {
      return channelProvider;
    }

    /** Performs a merge, using only non-null fields */
    protected Builder applyToAllUnaryMethods(
        Iterable<UnaryCallSettings.Builder> methodSettingsBuilders,
        UnaryCallSettings.Builder newSettingsBuilder)
        throws Exception {
      Set<Status.Code> newRetryableCodes = newSettingsBuilder.getRetryableCodes();
      RetrySettings.Builder newRetrySettingsBuilder = newSettingsBuilder.getRetrySettingsBuilder();
      for (UnaryCallSettings.Builder settingsBuilder : methodSettingsBuilders) {
        if (newRetryableCodes != null) {
          settingsBuilder.setRetryableCodes(newRetryableCodes);
        }
        if (newRetrySettingsBuilder != null) {
          settingsBuilder.getRetrySettingsBuilder().merge(newRetrySettingsBuilder);
        }
        // TODO(shinfan): Investigate on batching and paged settings.
      }
      return this;
    }

    public abstract ClientSettings build() throws IOException;
  }
}
