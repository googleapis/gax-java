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
package com.google.api.gax.rpc;

import com.google.api.core.ApiClock;
import com.google.api.core.ApiFunction;
import com.google.api.core.BetaApi;
import com.google.api.core.NanoClock;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.io.IOException;

/**
 * A base settings class to configure a service API class.
 *
 * <p>This base class includes settings that are applicable to all services, which includes things
 * like settings for creating an executor, credentials, transport-specific settings, and identifiers
 * for http headers.
 *
 * <p>If no ExecutorProvider is set, then InstantiatingExecutorProvider will be used, which creates
 * a default executor.
 */
@BetaApi
public abstract class ClientSettings {

  private final ExecutorProvider executorProvider;
  private final CredentialsProvider credentialsProvider;
  private final HeaderProvider headerProvider;
  private final TransportChannelProvider transportChannelProvider;
  private final ApiClock clock;

  /** Constructs an instance of ClientSettings. */
  protected ClientSettings(
      ExecutorProvider executorProvider,
      TransportChannelProvider transportChannelProvider,
      CredentialsProvider credentialsProvider,
      HeaderProvider headerProvider,
      ApiClock clock) {
    this.executorProvider = executorProvider;
    this.transportChannelProvider = transportChannelProvider;
    this.credentialsProvider = credentialsProvider;
    this.headerProvider = headerProvider;
    this.clock = clock;
  }

  public final ExecutorProvider getExecutorProvider() {
    return executorProvider;
  }

  public final TransportChannelProvider getTransportChannelProvider() {
    return transportChannelProvider;
  }

  public final CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  public final HeaderProvider getHeaderProvider() {
    return headerProvider;
  }

  public final ApiClock getClock() {
    return clock;
  }

  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executorProvider", executorProvider)
        .add("transportChannelProvider", transportChannelProvider)
        .add("credentialsProvider", credentialsProvider)
        .add("headerProvider", headerProvider)
        .add("clock", clock)
        .toString();
  }

  public abstract static class Builder {

    private ExecutorProvider executorProvider;
    private CredentialsProvider credentialsProvider;
    private HeaderProvider headerProvider;
    private TransportChannelProvider transportChannelProvider;
    private ApiClock clock;

    /** Create a builder from a ClientSettings object. */
    protected Builder(ClientSettings settings) {
      this.executorProvider = settings.executorProvider;
      this.transportChannelProvider = settings.transportChannelProvider;
      this.credentialsProvider = settings.credentialsProvider;
      this.headerProvider = settings.headerProvider;
      this.clock = settings.clock;
    }

    protected Builder(ClientContext clientContext) {
      if (clientContext == null) {
        this.executorProvider = InstantiatingExecutorProvider.newBuilder().build();
        this.transportChannelProvider = null;
        this.credentialsProvider = new NoCredentialsProvider();
        this.headerProvider = new NoHeaderProvider();
        this.clock = NanoClock.getDefaultClock();
      } else {
        this.executorProvider = FixedExecutorProvider.of(clientContext.getExecutor());
        this.transportChannelProvider =
            FixedTransportChannelProvider.of(clientContext.getTransportChannel());
        this.credentialsProvider = FixedCredentialsProvider.of(clientContext.getCredentials());
        this.headerProvider = FixedHeaderProvider.of(clientContext.getHeaders());
        this.clock = clientContext.getClock();
      }
    }

    protected Builder() {
      this((ClientContext) null);
    }

    /**
     * Sets the ExecutorProvider to use for getting the executor to use for running asynchronous API
     * call logic (such as retries and long-running operations), and also to pass to the transport
     * settings if an executor is needed for the transport and it doesn't have its own executor
     * provider.
     */
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      this.executorProvider = executorProvider;
      return this;
    }

    /** Sets the CredentialsProvider to use for getting the credentials to make calls with. */
    public Builder setCredentialsProvider(CredentialsProvider credentialsProvider) {
      this.credentialsProvider = Preconditions.checkNotNull(credentialsProvider);
      return this;
    }

    /** Sets the HeaderProvider to use for getting headers to put on http requests. */
    public Builder setHeaderProvider(HeaderProvider headerProvider) {
      this.headerProvider = headerProvider;
      return this;
    }

    /**
     * Sets the TransportProvider to use for getting the transport-specific context to make calls
     * with.
     */
    public Builder setTransportChannelProvider(TransportChannelProvider transportChannelProvider) {
      this.transportChannelProvider = transportChannelProvider;
      return this;
    }

    /**
     * Sets the clock to use for retry logic.
     *
     * <p>This will default to a system clock if it is not set.
     */
    public Builder setClock(ApiClock clock) {
      this.clock = clock;
      return this;
    }

    /** Gets the ExecutorProvider that was previously set on this Builder. */
    public ExecutorProvider getExecutorProvider() {
      return executorProvider;
    }

    /** Gets the TransportProvider that was previously set on this Builder. */
    public TransportChannelProvider getTransportChannelProvider() {
      return transportChannelProvider;
    }

    /** Gets the CredentialsProvider that was previously set on this Builder. */
    public CredentialsProvider getCredentialsProvider() {
      return credentialsProvider;
    }

    /** Gets the HeaderProvider that was previously set on this Builder. */
    public HeaderProvider getHeaderProvider() {
      return headerProvider;
    }

    /** Gets the ApiClock that was previously set on this Builder. */
    public ApiClock getClock() {
      return clock;
    }

    /** Applies the given settings updater function to the given method settings builders. */
    protected Builder applyToAllUnaryMethods(
        Iterable<UnaryCallSettings.Builder<?, ?>> methodSettingsBuilders,
        ApiFunction<UnaryCallSettings.Builder<?, ?>, Void> settingsUpdater)
        throws Exception {
      for (UnaryCallSettings.Builder<?, ?> settingsBuilder : methodSettingsBuilders) {
        settingsUpdater.apply(settingsBuilder);
      }
      return this;
    }

    public abstract ClientSettings build() throws IOException;

    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("executorProvider", executorProvider)
          .add("transportChannelProvider", transportChannelProvider)
          .add("credentialsProvider", credentialsProvider)
          .add("headerProvider", headerProvider)
          .add("clock", clock)
          .toString();
    }
  }
}
