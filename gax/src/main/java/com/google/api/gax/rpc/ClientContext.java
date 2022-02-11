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
import com.google.api.gax.rpc.internal.QuotaProjectIdHidingCredentials;
import com.google.api.gax.rpc.mtls.MtlsProvider;
import com.google.api.gax.tracing.ApiTracerFactory;
import com.google.api.gax.tracing.BaseApiTracerFactory;
import com.google.auth.Credentials;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private static final String QUOTA_PROJECT_ID_HEADER_KEY = "x-goog-user-project";

  /**
   * The objects that need to be closed in order to clean up the resources created in the process of
   * creating this ClientContext. This will include the closeables from the transport context.
   */
  public abstract List<BackgroundResource> getBackgroundResources();

  /**
   * Gets the executor to use for running scheduled API call logic (such as retries and long-running
   * operations).
   */
  public abstract ScheduledExecutorService getExecutor();

  @Nullable
  public abstract Credentials getCredentials();

  @Nullable
  public abstract TransportChannel getTransportChannel();

  public abstract Map<String, String> getHeaders();

  protected abstract Map<String, String> getInternalHeaders();

  public abstract ApiClock getClock();

  public abstract ApiCallContext getDefaultCallContext();

  @Nullable
  public abstract Watchdog getStreamWatchdog();

  @Nonnull
  public abstract Duration getStreamWatchdogCheckInterval();

  @Nullable
  public abstract String getEndpoint();

  @Nullable
  public abstract String getQuotaProjectId();

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
        .setTracerFactory(BaseApiTracerFactory.getInstance())
        .setQuotaProjectId(null);
  }

  public abstract Builder toBuilder();

  /**
   * Instantiates the executor, credentials, and transport context based on the given client
   * settings.
   */
  public static ClientContext create(ClientSettings settings) throws IOException {
    return create(settings.getStubSettings());
  }

  /** Returns the endpoint that should be used. See https://google.aip.dev/auth/4114. */
  static String getEndpoint(
      String endpoint,
      String mtlsEndpoint,
      boolean switchToMtlsEndpointAllowed,
      MtlsProvider mtlsProvider)
      throws IOException {
    if (switchToMtlsEndpointAllowed) {
      switch (mtlsProvider.getMtlsEndpointUsagePolicy()) {
        case ALWAYS:
          return mtlsEndpoint;
        case NEVER:
          return endpoint;
        default:
          if (mtlsProvider.useMtlsClientCertificate() && mtlsProvider.getKeyStore() != null) {
            return mtlsEndpoint;
          }
          return endpoint;
      }
    }
    return endpoint;
  }

  /**
   * Instantiates the executor, credentials, and transport context based on the given client
   * settings.
   */
  public static ClientContext create(StubSettings settings) throws IOException {
    ApiClock clock = settings.getClock();

    ExecutorProvider backgroundExecutorProvider = settings.getBackgroundExecutorProvider();
    final ScheduledExecutorService backgroundExecutor = backgroundExecutorProvider.getExecutor();

    Credentials credentials = settings.getCredentialsProvider().getCredentials();

    if (settings.getQuotaProjectId() != null) {
      // If the quotaProjectId is set, wrap original credentials with correct quotaProjectId as
      // QuotaProjectIdHidingCredentials.
      // Ensure that a custom set quota project id takes priority over one detected by credentials.
      // Avoid the backend receiving possibly conflict values of quotaProjectId
      credentials = new QuotaProjectIdHidingCredentials(credentials);
    }

    TransportChannelProvider transportChannelProvider = settings.getTransportChannelProvider();
    // After needsExecutor and StubSettings#setExecutorProvider are deprecated, transport channel
    // executor can only be set from TransportChannelProvider#withExecutor directly, and a provider
    // will have a default executor if it needs one.
    if (transportChannelProvider.needsExecutor() && settings.getExecutorProvider() != null) {
      transportChannelProvider = transportChannelProvider.withExecutor(backgroundExecutor);
    }
    Map<String, String> headers = getHeadersFromSettings(settings);
    if (transportChannelProvider.needsHeaders()) {
      transportChannelProvider = transportChannelProvider.withHeaders(headers);
    }
    if (transportChannelProvider.needsCredentials() && credentials != null) {
      transportChannelProvider = transportChannelProvider.withCredentials(credentials);
    }
    String endpoint =
        getEndpoint(
            settings.getEndpoint(),
            settings.getMtlsEndpoint(),
            settings.getSwitchToMtlsEndpointAllowed(),
            new MtlsProvider());
    if (transportChannelProvider.needsEndpoint()) {
      transportChannelProvider = transportChannelProvider.withEndpoint(endpoint);
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
        watchdogProvider = watchdogProvider.withExecutor(backgroundExecutor);
      }
      watchdog = watchdogProvider.getWatchdog();
    }

    ImmutableList.Builder<BackgroundResource> backgroundResources = ImmutableList.builder();

    if (transportChannelProvider.shouldAutoClose()) {
      backgroundResources.add(transportChannel);
    }
    if (backgroundExecutorProvider.shouldAutoClose()) {
      backgroundResources.add(new ExecutorAsBackgroundResource(backgroundExecutor));
    }
    if (watchdogProvider != null && watchdogProvider.shouldAutoClose()) {
      backgroundResources.add(watchdog);
    }

    return newBuilder()
        .setBackgroundResources(backgroundResources.build())
        .setExecutor(backgroundExecutor)
        .setCredentials(credentials)
        .setTransportChannel(transportChannel)
        .setHeaders(ImmutableMap.copyOf(settings.getHeaderProvider().getHeaders()))
        .setInternalHeaders(ImmutableMap.copyOf(settings.getInternalHeaderProvider().getHeaders()))
        .setClock(clock)
        .setDefaultCallContext(defaultCallContext)
        .setEndpoint(settings.getEndpoint())
        .setQuotaProjectId(settings.getQuotaProjectId())
        .setStreamWatchdog(watchdog)
        .setStreamWatchdogCheckInterval(settings.getStreamWatchdogCheckInterval())
        .setTracerFactory(settings.getTracerFactory())
        .build();
  }

  /**
   * Getting a header map from HeaderProvider and InternalHeaderProvider from settings with Quota
   * Project Id.
   */
  private static Map<String, String> getHeadersFromSettings(StubSettings settings) {
    // Resolve conflicts when merging headers from multiple sources
    Map<String, String> userHeaders = settings.getHeaderProvider().getHeaders();
    Map<String, String> internalHeaders = settings.getInternalHeaderProvider().getHeaders();
    Map<String, String> conflictResolution = new HashMap<>();

    Set<String> conflicts = Sets.intersection(userHeaders.keySet(), internalHeaders.keySet());
    for (String key : conflicts) {
      if ("user-agent".equals(key)) {
        conflictResolution.put(key, userHeaders.get(key) + " " + internalHeaders.get(key));
        continue;
      }
      // Backwards compat: quota project id can conflict if its overriden in settings
      if (QUOTA_PROJECT_ID_HEADER_KEY.equals(key) && settings.getQuotaProjectId() != null) {
        continue;
      }
      throw new IllegalArgumentException("Header provider can't override the header: " + key);
    }
    if (settings.getQuotaProjectId() != null) {
      conflictResolution.put(QUOTA_PROJECT_ID_HEADER_KEY, settings.getQuotaProjectId());
    }

    Map<String, String> effectiveHeaders = new HashMap<>();
    effectiveHeaders.putAll(internalHeaders);
    effectiveHeaders.putAll(userHeaders);
    effectiveHeaders.putAll(conflictResolution);

    return ImmutableMap.copyOf(effectiveHeaders);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBackgroundResources(List<BackgroundResource> backgroundResources);

    /**
     * Sets the executor to use for running scheduled API call logic (such as retries and
     * long-running operations).
     */
    public abstract Builder setExecutor(ScheduledExecutorService value);

    public abstract Builder setCredentials(Credentials value);

    public abstract Builder setTransportChannel(TransportChannel transportChannel);

    public abstract Builder setHeaders(Map<String, String> headers);

    protected abstract Builder setInternalHeaders(Map<String, String> headers);

    public abstract Builder setClock(ApiClock clock);

    public abstract Builder setDefaultCallContext(ApiCallContext defaultCallContext);

    public abstract Builder setEndpoint(String endpoint);

    public abstract Builder setQuotaProjectId(String QuotaProjectId);

    public abstract Builder setStreamWatchdog(Watchdog watchdog);

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
