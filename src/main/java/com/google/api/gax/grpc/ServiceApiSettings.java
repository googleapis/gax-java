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

import com.google.api.gax.core.RetrySettings;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A base settings class to configure a service API class.
 *
 * <p>This base class includes settings that are applicable to all services, which includes
 * things like connection settings (or channel), executor, and identifiers for http
 * headers.
 *
 * <p>If no executor is provided, then a default one will be created.
 *
 * <p>There are two ways to configure the channel that will be used:
 *
 * <p><ol>
 * <li>Provide an instance of ConnectionSettings. In this case, an instance of
 *   ManagedChannel will be created automatically. The value of shouldAutoCloseChannel
 *   will then be true, indicating to the service API class that it should call shutdown()
 *   on the channel when it is close()'d. When this channel is created, it will use
 *   the executor of this settings class, which will either be a provided one or a default
 *   one.
 * <li>Provide a ManagedChannel directly, specifying the value of shouldAutoClose
 *   explicitly. When shouldAutoClose is true, the service API class will close the
 *   passed-in channel; when false, the service API class will not close it. Since the
 *   ManagedChannel is passed in, its executor may or might not have any relation
 *   to the executor in this settings class.
 * </ol>
 *
 * <p>The client lib header and generator header values are used to form a value that
 * goes into the http header of requests to the service.
 */
public abstract class ServiceApiSettings {

  private final ChannelProvider channelProvider;
  private final ExecutorProvider executorProvider;

  /**
   * Constructs an instance of ServiceApiSettings.
   */
  protected ServiceApiSettings(ChannelProvider channelProvider, ExecutorProvider executorProvider) {
    this.channelProvider = channelProvider;
    this.executorProvider = executorProvider;
  }

  /**
   * Gets a channel and an executor for making calls.
   */
  public final ChannelAndExecutor getChannelAndExecutor() throws IOException {
    return ChannelAndExecutor.create(channelProvider, executorProvider);
  }

  public final ChannelProvider getChannelProvider() {
    return channelProvider;
  }

  public final ExecutorProvider getExecutorProvider() {
    return executorProvider;
  }

  public abstract static class Builder {

    private ChannelProvider channelProvider;
    private ExecutorProvider executorProvider;

    /**
     * Create a builder from a ServiceApiSettings object.
     */
    protected Builder(ServiceApiSettings settings) {
      this.channelProvider = settings.channelProvider;
      this.executorProvider = settings.executorProvider;
    }

    protected Builder(InstantiatingChannelProvider channelProvider) {
      this.channelProvider = channelProvider;
      this.executorProvider = InstantiatingExecutorProvider.newBuilder().build();
    }

    public Builder setChannelProvider(ChannelProvider channelProvider) {
      this.channelProvider = channelProvider;
      return this;
    }

    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      this.executorProvider = executorProvider;
      return this;
    }

    public ChannelProvider getChannelProvider() {
      return channelProvider;
    }

    public ExecutorProvider getExecutorProvider() {
      return executorProvider;
    }

    /**
     *  Performs a merge, using only non-null fields
     */
    protected Builder applyToAllApiMethods(
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
        // TODO(shinfan): Investigate on bundling and paged settings.
      }
      return this;
    }

    public abstract ServiceApiSettings build() throws IOException;
  }
}
