/*
 * Copyright 2016, Google LLC All rights reserved.
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
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import java.io.IOException;
import javax.annotation.Nullable;

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
@AutoValue
public abstract class StubSettings<SettingsT extends StubSettings<SettingsT>> {

  public abstract ExecutorProvider getExecutorProvider();

  @Nullable
  public abstract TransportChannelProvider getTransportChannelProvider();

  public abstract CredentialsProvider getCredentialsProvider();

  @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
  public abstract HeaderProvider getHeaderProvider();

  @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
  protected abstract HeaderProvider getInternalHeaderProvider();

  public abstract ApiClock getClock();

  @Nullable
  public abstract String getEndpoint();

  public static Builder newBuilder() {
    return newBuilder((ClientContext) null);
  }

  public static Builder newBuilder(ClientContext clientContext) {
    if (clientContext == null) {
      return new AutoValue_StubSettings.Builder()
          .setExecutorProvider(InstantiatingExecutorProvider.newBuilder().build())
          .setCredentialsProvider(NoCredentialsProvider.create())
          .setHeaderProvider(new NoHeaderProvider())
          .setInternalHeaderProvider(new NoHeaderProvider())
          .setClock(NanoClock.getDefaultClock());
    } else {
      return new AutoValue_StubSettings.Builder()
          .setExecutorProvider(FixedExecutorProvider.create(clientContext.getExecutor()))
          .setTransportChannelProvider(
              FixedTransportChannelProvider.create(clientContext.getTransportChannel()))
          .setCredentialsProvider(FixedCredentialsProvider.create(clientContext.getCredentials()))
          .setHeaderProvider(FixedHeaderProvider.create(clientContext.getHeaders()))
          .setInternalHeaderProvider(FixedHeaderProvider.create(clientContext.getInternalHeaders()))
          .setClock(clientContext.getClock())
          .setEndpoint(clientContext.getEndpoint());
    }
  }

  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executorProvider", getExecutorProvider())
        .add("transportChannelProvider", getTransportChannelProvider())
        .add("credentialsProvider", getCredentialsProvider())
        .add("headerProvider", getHeaderProvider())
        .add("internalHeaderProvider", getInternalHeaderProvider())
        .add("clock", getClock())
        .add("endpoint", getEndpoint())
        .toString();
  }

  @AutoValue.Builder
  public abstract static class Builder<SettingsT extends StubSettings<SettingsT>> {

    /**
     * Sets the ExecutorProvider to use for getting the executor to use for running asynchronous API
     * call logic (such as retries and long-running operations), and also to pass to the transport
     * settings if an executor is needed for the transport and it doesn't have its own executor
     * provider.
     */
    public abstract Builder<SettingsT> setExecutorProvider(ExecutorProvider executorProvider);

    /** Sets the CredentialsProvider to use for getting the credentials to make calls with. */
    public abstract Builder<SettingsT> setCredentialsProvider(
        CredentialsProvider credentialsProvider);

    /**
     * Sets the HeaderProvider for getting custom static headers for http requests. The header
     * provider will be called during client construction only once. The headers returned by the
     * provider will be cached and supplied as is for each request issued by the constructed client.
     * Some reserved headers can be overridden (e.g. Content-Type) or merged with the default value
     * (e.g. User-Agent) by the underlying transport layer.
     */
    @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
    public abstract Builder<SettingsT> setHeaderProvider(HeaderProvider headerProvider);

    /**
     * Sets the HeaderProvider for getting internal (library-defined) static headers for http
     * requests. The header provider will be called during client construction only once. The
     * headers returned by the provider will be cached and supplied as is for each request issued by
     * the constructed client. Some reserved headers can be overridden (e.g. Content-Type) or merged
     * with the default value (e.g. User-Agent) by the underlying transport layer.
     */
    @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
    protected abstract Builder<SettingsT> setInternalHeaderProvider(
        HeaderProvider internalHeaderProvider);

    /**
     * Sets the TransportProvider to use for getting the transport-specific context to make calls
     * with.
     */
    public abstract Builder<SettingsT> setTransportChannelProvider(
        TransportChannelProvider transportChannelProvider);

    /**
     * Sets the clock to use for retry logic.
     *
     * <p>This will default to a system clock if it is not set.
     */
    public abstract Builder<SettingsT> setClock(ApiClock clock);

    public abstract Builder<SettingsT> setEndpoint(String endpoint);

    /** Applies the given settings updater function to the given method settings builders. */
    protected static void applyToAllUnaryMethods(
        Iterable<UnaryCallSettings.Builder<?, ?>> methodSettingsBuilders,
        ApiFunction<UnaryCallSettings.Builder<?, ?>, Void> settingsUpdater)
        throws Exception {
      for (UnaryCallSettings.Builder<?, ?> settingsBuilder : methodSettingsBuilders) {
        settingsUpdater.apply(settingsBuilder);
      }
    }

    public abstract StubSettings<SettingsT> build() throws IOException;

    public String toString() {
      try {
        return this.build().toString();
      } catch (IOException e) {
        return "StubSettings";
      }
    }
  }
}
