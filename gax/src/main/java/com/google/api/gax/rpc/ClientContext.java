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
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorAsBackgroundResource;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.api.gax.rpc.internal.QuotaProjectIdHidingCredentials;
import com.google.api.gax.tracing.ApiTracerFactory;
import com.google.api.gax.tracing.NoopApiTracerFactory;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.ServiceAccountJwtAccessCredentials;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.http.client.utils.URIBuilder;
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
        .setTracerFactory(NoopApiTracerFactory.getInstance())
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

  @VisibleForTesting
  public static Credentials determineSelfSignedJWTCredentials(
      CredentialsProvider provider, String endpoint, String defaultEndpoint) throws IOException {
    if (endpoint == null || defaultEndpoint == null || !endpoint.equals(defaultEndpoint)) {
      return provider.getCredentials();
    }

    Credentials credentials = null;

    // DIREGAPIC clients may set the default endpoint like
    // "https://compute.googleapis.com/compute/v1/projects/". For self signed jwt, we cannot use
    // this default endpoint directly as the audience, because the correct audience should be
    // "https://compute.googleapis.com/". Here we compute the audience.
    URI selfSignedJwtAudience = null;
    if (defaultEndpoint != null && defaultEndpoint.startsWith("https://")) {
      // "https://" indicates the client is DIREGAPIC.
      try {
        URI uri = new URI(defaultEndpoint);
        selfSignedJwtAudience =
            new URIBuilder().setScheme(uri.getScheme()).setHost(uri.getHost()).setPath("/").build();
      } catch (URISyntaxException e) {
        throw new IOException(
            String.format("Default endpoint {%s} cannot be parsed.", defaultEndpoint));
      }
    }

    // For service account credentials, if the endpoint is default endpoint, and user doesn't
    // provide scopes, then we create a ServiceAccountJwtAccessCredentials and use this credentials
    // instead. This credential uses self signed jwt, and it is more efficient since the network
    // call to token endpoint is avoided. See https://google.aip.dev/auth/4111.
    if (provider instanceof GoogleCredentialsProvider) {
      // GoogleCredentialsProvider has this logic implemented.
      credentials =
          ((GoogleCredentialsProvider) provider)
              .getCredentials(endpoint.equals(defaultEndpoint), selfSignedJwtAudience);
    } else if (provider instanceof FixedCredentialsProvider) {
      credentials = provider.getCredentials();
      if (credentials instanceof ServiceAccountCredentials
          && ((ServiceAccountCredentials) credentials).getScopes().isEmpty()
          && endpoint.equals(defaultEndpoint)) {
        // For FixedCredentialsProvider, we need to create a ServiceAccountJwtAccessCredentials by
        // ourselves.
        ServiceAccountCredentials serviceAccount = (ServiceAccountCredentials) credentials;

        credentials =
            ServiceAccountJwtAccessCredentials.newBuilder()
                .setClientEmail(serviceAccount.getClientEmail())
                .setClientId(serviceAccount.getClientId())
                .setPrivateKey(serviceAccount.getPrivateKey())
                .setPrivateKeyId(serviceAccount.getPrivateKeyId())
                .setQuotaProjectId(serviceAccount.getQuotaProjectId())
                .setDefaultAudience(selfSignedJwtAudience)
                .build();
      }
    }

    if (credentials == null) {
      credentials = provider.getCredentials();
    }

    return credentials;
  }

  /**
   * Instantiates the executor, credentials, and transport context based on the given client
   * settings.
   */
  public static ClientContext create(StubSettings settings) throws IOException {
    ApiClock clock = settings.getClock();

    ExecutorProvider executorProvider = settings.getExecutorProvider();
    final ScheduledExecutorService executor = executorProvider.getExecutor();

    Credentials credentials =
        determineSelfSignedJWTCredentials(
            settings.getCredentialsProvider(),
            settings.getEndpoint(),
            settings.getDefaultApiEndpoint());

    if (settings.getQuotaProjectId() != null) {
      // If the quotaProjectId is set, wrap original credentials with correct quotaProjectId as
      // QuotaProjectIdHidingCredentials.
      // Ensure that a custom set quota project id takes priority over one detected by credentials.
      // Avoid the backend receiving possibly conflict values of quotaProjectId
      credentials = new QuotaProjectIdHidingCredentials(credentials);
    }

    TransportChannelProvider transportChannelProvider = settings.getTransportChannelProvider();
    if (transportChannelProvider.needsExecutor()) {
      transportChannelProvider = transportChannelProvider.withExecutor((Executor) executor);
    }
    Map<String, String> headers = getHeadersFromSettings(settings);
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
    if (watchdogProvider != null && watchdogProvider.shouldAutoClose()) {
      backgroundResources.add(watchdog);
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

    public abstract Builder setQuotaProjectId(String QuotaProjectId);

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
