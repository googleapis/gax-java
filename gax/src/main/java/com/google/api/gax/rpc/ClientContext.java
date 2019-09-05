/*
 * Copyright 2017 Google LLC
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
import com.google.api.core.BetaApi;
import com.google.api.core.NanoClock;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.ExecutorAsBackgroundResource;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.tracing.ApiTracerFactory;
import com.google.api.gax.tracing.NoopApiTracerFactory;
import com.google.auth.Credentials;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/**
 * Encapsulates client state, including executor, credentials, and transport channel.
 *
 * <p>Unlike {@link ClientSettings} which allows users to configure the client, {@code
 * ClientContext} is intended to be used in generated code. Most users will not need to use it.
 */
@AutoValue
public abstract class ClientContext {

  /**
   * The objects that need to be closed in order to clean up the resources created in the process of
   * creating this ClientContext. This will include the closeables from the transport context.
   */
  public abstract List<BackgroundResource> getBackgroundResources();

  public abstract ScheduledExecutorService getExecutor();

  @Nullable
  public abstract Credentials getCredentials();

  @Nullable
  public abstract TransportChannel getTransportChannel();

  @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
  public abstract Map<String, String> getHeaders();

  @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
  protected abstract Map<String, String> getInternalHeaders();

  public abstract ApiClock getClock();

  public abstract ApiCallContext getDefaultCallContext();

  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  @Nullable
  public abstract Watchdog getStreamWatchdog();

  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  @Nonnull
  public abstract Duration getStreamWatchdogCheckInterval();

  @Nullable
  public abstract String getEndpoint();

  /** Gets the {@link ApiTracerFactory} that will be used to generate traces for operations. */
  @BetaApi("The surface for tracing is not stable yet and may change in the future.")
  @Nonnull
  public abstract ApiTracerFactory getTracerFactory();

  public static Builder newBuilder() {
    return new AutoValue_ClientContext.Builder()
        .setBackgroundResources(Collections.<BackgroundResource>emptyList())
        .setExecutor(Executors.newScheduledThreadPool(0))
        .setHeaders(Collections.<String, String>emptyMap())
        .setInternalHeaders(Collections.<String, String>emptyMap())
        .setClock(NanoClock.getDefaultClock())
        .setStreamWatchdog(null)
        .setStreamWatchdogCheckInterval(Duration.ZERO)
        .setTracerFactory(NoopApiTracerFactory.getInstance());
  }

  public abstract Builder toBuilder();

  /**
   * Instantiates the executor, credentials, and transport context based on the given client
   * settings.
   */
  public static ClientContext create(ClientSettings settings) throws IOException {
    return create(settings.getStubSettings());
  }

  /**
   * Instantiates the executor, credentials, and transport context based on the given client
   * settings.
   */
  public static ClientContext create(StubSettings settings) throws IOException {
    ApiClock clock = settings.getClock();

    ExecutorProvider executorProvider = settings.getExecutorProvider();
    final ScheduledExecutorService executor = executorProvider.getExecutor();

    Credentials credentials = settings.getCredentialsProvider().getCredentials();

    TransportChannelProvider transportChannelProvider = settings.getTransportChannelProvider();
    if (transportChannelProvider.needsExecutor()) {
      transportChannelProvider = transportChannelProvider.withExecutor(executor);
    }
    Map<String, String> headers =
        ImmutableMap.<String, String>builder()
            .putAll(settings.getHeaderProvider().getHeaders())
            .putAll(settings.getInternalHeaderProvider().getHeaders())
            .build();
    if (transportChannelProvider.needsHeaders()) {
      transportChannelProvider = transportChannelProvider.withHeaders(headers);
    }
    if (transportChannelProvider.needsEndpoint()) {
      transportChannelProvider = transportChannelProvider.withEndpoint(settings.getEndpoint());
    }
    if (transportChannelProvider.needsCredentials() && credentials != null) {
      transportChannelProvider = transportChannelProvider.withCredentials(credentials);
    }
    TransportChannel transportChannel = transportChannelProvider.getTransportChannel();

    ApiCallContext defaultCallContext =
        transportChannel.getEmptyCallContext().withTransportChannel(transportChannel);
    if (credentials != null) {
      defaultCallContext = defaultCallContext.withCredentials(credentials);
    }

    WatchdogProvider watchdogProvider = settings.getStreamWatchdogProvider();
    @Nullable Watchdog watchdog = null;

    if (watchdogProvider != null) {
      if (watchdogProvider.needsCheckInterval()) {
        watchdogProvider =
            watchdogProvider.withCheckInterval(settings.getStreamWatchdogCheckInterval());
      }
      if (watchdogProvider.needsClock()) {
        watchdogProvider = watchdogProvider.withClock(clock);
      }
      if (watchdogProvider.needsExecutor()) {
        watchdogProvider = watchdogProvider.withExecutor(executor);
      }
      watchdog = watchdogProvider.getWatchdog();
    }

    ImmutableList.Builder<BackgroundResource> backgroundResources = ImmutableList.builder();

    if (transportChannelProvider.shouldAutoClose()) {
      backgroundResources.add(transportChannel);
    }
    if (executorProvider.shouldAutoClose()) {
      backgroundResources.add(new ExecutorAsBackgroundResource(executor));
    }

    return newBuilder()
        .setBackgroundResources(backgroundResources.build())
        .setExecutor(executor)
        .setCredentials(credentials)
        .setTransportChannel(transportChannel)
        .setHeaders(ImmutableMap.copyOf(settings.getHeaderProvider().getHeaders()))
        .setInternalHeaders(ImmutableMap.copyOf(settings.getInternalHeaderProvider().getHeaders()))
        .setClock(clock)
        .setDefaultCallContext(defaultCallContext)
        .setEndpoint(settings.getEndpoint())
        .setStreamWatchdog(watchdog)
        .setStreamWatchdogCheckInterval(settings.getStreamWatchdogCheckInterval())
        .setTracerFactory(settings.getTracerFactory())
        .build();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBackgroundResources(List<BackgroundResource> backgroundResources);

    public abstract Builder setExecutor(ScheduledExecutorService value);

    public abstract Builder setCredentials(Credentials value);

    public abstract Builder setTransportChannel(TransportChannel transportChannel);

    @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
    public abstract Builder setHeaders(Map<String, String> headers);

    @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
    protected abstract Builder setInternalHeaders(Map<String, String> headers);

    public abstract Builder setClock(ApiClock clock);

    public abstract Builder setDefaultCallContext(ApiCallContext defaultCallContext);

    public abstract Builder setEndpoint(String endpoint);

    @BetaApi("The surface for streaming is not stable yet and may change in the future.")
    public abstract Builder setStreamWatchdog(Watchdog watchdog);

    @BetaApi("The surface for streaming is not stable yet and may change in the future.")
    public abstract Builder setStreamWatchdogCheckInterval(Duration duration);

    /**
     * Set the {@link ApiTracerFactory} that will be used to generate traces for operations.
     *
     * @param tracerFactory an instance {@link ApiTracerFactory}.
     */
    @BetaApi("The surface for tracing is not stable yet and may change in the future.")
    public abstract Builder setTracerFactory(ApiTracerFactory tracerFactory);

    public abstract ClientContext build();
  }
}
