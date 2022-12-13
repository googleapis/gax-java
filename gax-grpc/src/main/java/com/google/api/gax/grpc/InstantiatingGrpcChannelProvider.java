/*
 * Copyright 2016 Google LLC
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
package com.google.api.gax.grpc;

import com.google.api.core.ApiFunction;
import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.core.InternalExtensionOnly;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.rpc.internal.EnvironmentProvider;
import com.google.api.gax.rpc.mtls.MtlsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.grpc.CallCredentials;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.TlsChannelCredentials;
import io.grpc.alts.GoogleDefaultChannelCredentials;
import io.grpc.auth.MoreCallCredentials;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import org.threeten.bp.Duration;

/**
 * InstantiatingGrpcChannelProvider is a TransportChannelProvider which constructs a gRPC
 * ManagedChannel with a number of configured inputs every time getChannel(...) is called. These
 * inputs include a port, a service address, and credentials.
 *
 * <p>The credentials can either be supplied directly (by providing a FixedCredentialsProvider to
 * Builder.setCredentialsProvider()) or acquired implicitly from Application Default Credentials (by
 * providing a GoogleCredentialsProvider to Builder.setCredentialsProvider()).
 *
 * <p>The client lib header and generator header values are used to form a value that goes into the
 * http header of requests to the service.
 */
@InternalExtensionOnly
public final class InstantiatingGrpcChannelProvider implements TransportChannelProvider {
  static final String DIRECT_PATH_ENV_VAR = "GOOGLE_CLOUD_ENABLE_DIRECT_PATH";
  private static final String DIRECT_PATH_ENV_DISABLE_DIRECT_PATH =
      "GOOGLE_CLOUD_DISABLE_DIRECT_PATH";
  private static final String DIRECT_PATH_ENV_ENABLE_XDS = "GOOGLE_CLOUD_ENABLE_DIRECT_PATH_XDS";
  static final long DIRECT_PATH_KEEP_ALIVE_TIME_SECONDS = 3600;
  static final long DIRECT_PATH_KEEP_ALIVE_TIMEOUT_SECONDS = 20;
  static final String GCE_PRODUCTION_NAME_PRIOR_2016 = "Google";
  static final String GCE_PRODUCTION_NAME_AFTER_2016 = "Google Compute Engine";

  private final int processorCount;
  private final Executor executor;
  private final HeaderProvider headerProvider;
  private final String endpoint;
  // TODO: remove. envProvider currently provides DirectPath environment variable, and is only used
  // during initial rollout for DirectPath. This provider will be removed once the DirectPath
  // environment is not used.
  private final EnvironmentProvider envProvider;
  @Nullable private final GrpcInterceptorProvider interceptorProvider;
  @Nullable private final Integer maxInboundMessageSize;
  @Nullable private final Integer maxInboundMetadataSize;
  @Nullable private final Duration keepAliveTime;
  @Nullable private final Duration keepAliveTimeout;
  @Nullable private final Boolean keepAliveWithoutCalls;
  private final ChannelPoolSettings channelPoolSettings;
  @Nullable private final Credentials credentials;
  @Nullable private final ChannelPrimer channelPrimer;
  @Nullable private final Boolean attemptDirectPath;
  @Nullable private final Boolean allowNonDefaultServiceAccount;
  @VisibleForTesting final ImmutableMap<String, ?> directPathServiceConfig;
  @Nullable private final MtlsProvider mtlsProvider;

  @Nullable
  private final ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator;

  private InstantiatingGrpcChannelProvider(Builder builder) {
    this.processorCount = builder.processorCount;
    this.executor = builder.executor;
    this.headerProvider = builder.headerProvider;
    this.endpoint = builder.endpoint;
    this.mtlsProvider = builder.mtlsProvider;
    this.envProvider = builder.envProvider;
    this.interceptorProvider = builder.interceptorProvider;
    this.maxInboundMessageSize = builder.maxInboundMessageSize;
    this.maxInboundMetadataSize = builder.maxInboundMetadataSize;
    this.keepAliveTime = builder.keepAliveTime;
    this.keepAliveTimeout = builder.keepAliveTimeout;
    this.keepAliveWithoutCalls = builder.keepAliveWithoutCalls;
    this.channelPoolSettings = builder.channelPoolSettings;
    this.channelConfigurator = builder.channelConfigurator;
    this.credentials = builder.credentials;
    this.channelPrimer = builder.channelPrimer;
    this.attemptDirectPath = builder.attemptDirectPath;
    this.allowNonDefaultServiceAccount = builder.allowNonDefaultServiceAccount;
    this.directPathServiceConfig =
        builder.directPathServiceConfig == null
            ? getDefaultDirectPathServiceConfig()
            : builder.directPathServiceConfig;
  }

  /**
   * @deprecated If executor is not set, this channel provider will create channels with default
   *     grpc executor.
   */
  @Deprecated
  @Override
  public boolean needsExecutor() {
    return executor == null;
  }

  @Deprecated
  @Override
  public TransportChannelProvider withExecutor(ScheduledExecutorService executor) {
    return withExecutor((Executor) executor);
  }

  @Override
  public TransportChannelProvider withExecutor(Executor executor) {
    return toBuilder().setExecutor(executor).build();
  }

  @Override
  public boolean needsHeaders() {
    return headerProvider == null;
  }

  @Override
  public TransportChannelProvider withHeaders(Map<String, String> headers) {
    return toBuilder().setHeaderProvider(FixedHeaderProvider.create(headers)).build();
  }

  @Override
  public String getTransportName() {
    return GrpcTransportChannel.getGrpcTransportName();
  }

  @Override
  public boolean needsEndpoint() {
    return endpoint == null;
  }

  /**
   * Specify the endpoint the channel should connect to.
   *
   * <p>The value of {@code endpoint} must be of the form {@code host:port}.
   *
   * @param endpoint The endpoint to connect to
   * @return A new {@link InstantiatingGrpcChannelProvider} with the specified endpoint configured
   */
  @Override
  public TransportChannelProvider withEndpoint(String endpoint) {
    validateEndpoint(endpoint);
    return toBuilder().setEndpoint(endpoint).build();
  }

  /** @deprecated Please modify pool settings via {@link #toBuilder()} */
  @Deprecated
  @Override
  public boolean acceptsPoolSize() {
    return true;
  }

  /** @deprecated Please modify pool settings via {@link #toBuilder()} */
  @Deprecated
  @Override
  public TransportChannelProvider withPoolSize(int size) {
    return toBuilder().setPoolSize(size).build();
  }

  @Override
  public boolean needsCredentials() {
    return credentials == null;
  }

  @Override
  public TransportChannelProvider withCredentials(Credentials credentials) {
    return toBuilder().setCredentials(credentials).build();
  }

  @Override
  public TransportChannel getTransportChannel() throws IOException {
    if (needsHeaders()) {
      throw new IllegalStateException("getTransportChannel() called when needsHeaders() is true");
    } else if (needsEndpoint()) {
      throw new IllegalStateException("getTransportChannel() called when needsEndpoint() is true");
    } else {
      return createChannel();
    }
  }

  private TransportChannel createChannel() throws IOException {
    return GrpcTransportChannel.create(
        ChannelPool.create(
            channelPoolSettings, InstantiatingGrpcChannelProvider.this::createSingleChannel));
  }

  // TODO(mohanli): Use attemptDirectPath as the only indicator once setAttemptDirectPath is adapted
  //                and the env var is removed from client environment.
  private boolean isDirectPathEnabled(String serviceAddress) {
    String disableDirectPathEnv = envProvider.getenv(DIRECT_PATH_ENV_DISABLE_DIRECT_PATH);
    boolean isDirectPathDisabled = Boolean.parseBoolean(disableDirectPathEnv);
    if (isDirectPathDisabled) {
      return false;
    }
    // Only check attemptDirectPath when DIRECT_PATH_ENV_DISABLE_DIRECT_PATH is not set.
    if (attemptDirectPath != null) {
      return attemptDirectPath;
    }
    // Only check DIRECT_PATH_ENV_VAR when attemptDirectPath is not set.
    String whiteList = envProvider.getenv(DIRECT_PATH_ENV_VAR);
    if (whiteList == null) {
      return false;
    }
    for (String service : whiteList.split(",")) {
      if (!service.isEmpty() && serviceAddress.contains(service)) {
        return true;
      }
    }
    return false;
  }

  private boolean isNonDefaultServiceAccountAllowed() {
    if (allowNonDefaultServiceAccount != null && allowNonDefaultServiceAccount) {
      return true;
    }
    return credentials instanceof ComputeEngineCredentials;
  }

  // DirectPath should only be used on Compute Engine.
  // Notice Windows is supported for now.
  static boolean isOnComputeEngine() {
    String osName = System.getProperty("os.name");
    if ("Linux".equals(osName)) {
      try {
        String result =
            Files.asCharSource(new File("/sys/class/dmi/id/product_name"), StandardCharsets.UTF_8)
                .readFirstLine();
        return result.contains(GCE_PRODUCTION_NAME_PRIOR_2016)
            || result.contains(GCE_PRODUCTION_NAME_AFTER_2016);
      } catch (IOException ignored) {
        return false;
      }
    }
    return false;
  }

  @VisibleForTesting
  ChannelCredentials createMtlsChannelCredentials() throws IOException, GeneralSecurityException {
    if (mtlsProvider.useMtlsClientCertificate()) {
      KeyStore mtlsKeyStore = mtlsProvider.getKeyStore();
      if (mtlsKeyStore != null) {
        KeyManagerFactory factory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(mtlsKeyStore, new char[] {});
        return TlsChannelCredentials.newBuilder().keyManager(factory.getKeyManagers()).build();
      }
    }
    return null;
  }

  private ManagedChannel createSingleChannel() throws IOException {
    GrpcHeaderInterceptor headerInterceptor =
        new GrpcHeaderInterceptor(headerProvider.getHeaders());
    GrpcMetadataHandlerInterceptor metadataHandlerInterceptor =
        new GrpcMetadataHandlerInterceptor();

    int colon = endpoint.lastIndexOf(':');
    if (colon < 0) {
      throw new IllegalStateException("invalid endpoint - should have been validated: " + endpoint);
    }
    int port = Integer.parseInt(endpoint.substring(colon + 1));
    String serviceAddress = endpoint.substring(0, colon);

    ManagedChannelBuilder<?> builder;

    // Check DirectPath traffic.
    boolean isDirectPathXdsEnabled = false;
    if (isDirectPathEnabled(serviceAddress)
        && isNonDefaultServiceAccountAllowed()
        && isOnComputeEngine()) {
      CallCredentials callCreds = MoreCallCredentials.from(credentials);
      ChannelCredentials channelCreds =
          GoogleDefaultChannelCredentials.newBuilder().callCredentials(callCreds).build();
      isDirectPathXdsEnabled = Boolean.parseBoolean(envProvider.getenv(DIRECT_PATH_ENV_ENABLE_XDS));
      if (isDirectPathXdsEnabled) {
        // google-c2p: CloudToProd(C2P) Directpath. This scheme is defined in
        // io.grpc.googleapis.GoogleCloudToProdNameResolverProvider.
        // This resolver target must not have a port number.
        builder = Grpc.newChannelBuilder("google-c2p:///" + serviceAddress, channelCreds);
      } else {
        builder = Grpc.newChannelBuilderForAddress(serviceAddress, port, channelCreds);
        builder.defaultServiceConfig(directPathServiceConfig);
      }
      // Set default keepAliveTime and keepAliveTimeout when directpath environment is enabled.
      // Will be overridden by user defined values if any.
      builder.keepAliveTime(DIRECT_PATH_KEEP_ALIVE_TIME_SECONDS, TimeUnit.SECONDS);
      builder.keepAliveTimeout(DIRECT_PATH_KEEP_ALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } else {
      ChannelCredentials channelCredentials;
      try {
        channelCredentials = createMtlsChannelCredentials();
      } catch (GeneralSecurityException e) {
        throw new IOException(e);
      }
      if (channelCredentials != null) {
        builder = Grpc.newChannelBuilder(endpoint, channelCredentials);
      } else {
        builder = ManagedChannelBuilder.forAddress(serviceAddress, port);
      }
    }
    // google-c2p resolver requires service config lookup
    if (!isDirectPathXdsEnabled) {
      // See https://github.com/googleapis/gapic-generator/issues/2816
      builder.disableServiceConfigLookUp();
    }
    builder =
        builder
            .intercept(new GrpcChannelUUIDInterceptor())
            .intercept(headerInterceptor)
            .intercept(metadataHandlerInterceptor)
            .userAgent(headerInterceptor.getUserAgentHeader())
            .executor(executor);

    if (maxInboundMetadataSize != null) {
      builder.maxInboundMetadataSize(maxInboundMetadataSize);
    }
    if (maxInboundMessageSize != null) {
      builder.maxInboundMessageSize(maxInboundMessageSize);
    }
    if (keepAliveTime != null) {
      builder.keepAliveTime(keepAliveTime.toMillis(), TimeUnit.MILLISECONDS);
    }
    if (keepAliveTimeout != null) {
      builder.keepAliveTimeout(keepAliveTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    if (keepAliveWithoutCalls != null) {
      builder.keepAliveWithoutCalls(keepAliveWithoutCalls);
    }
    if (interceptorProvider != null) {
      builder.intercept(interceptorProvider.getInterceptors());
    }
    if (channelConfigurator != null) {
      builder = channelConfigurator.apply(builder);
    }

    ManagedChannel managedChannel = builder.build();
    if (channelPrimer != null) {
      channelPrimer.primeChannel(managedChannel);
    }
    return managedChannel;
  }

  /** The endpoint to be used for the channel. */
  public String getEndpoint() {
    return endpoint;
  }

  /** The time without read activity before sending a keepalive ping. */
  public Duration getKeepAliveTime() {
    return keepAliveTime;
  }

  /** The time without read activity after sending a keepalive ping. */
  public Duration getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /** Whether keepalive will be performed when there are no outstanding RPCs. */
  public Boolean getKeepAliveWithoutCalls() {
    return keepAliveWithoutCalls;
  }

  /** The maximum metadata size allowed to be received on the channel. */
  @BetaApi("The surface for maximum metadata size is not stable yet and may change in the future.")
  public Integer getMaxInboundMetadataSize() {
    return maxInboundMetadataSize;
  }

  @Override
  public boolean shouldAutoClose() {
    return true;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    @Deprecated private int processorCount;
    private Executor executor;
    private HeaderProvider headerProvider;
    private String endpoint;
    private EnvironmentProvider envProvider;
    private MtlsProvider mtlsProvider = new MtlsProvider();
    @Nullable private GrpcInterceptorProvider interceptorProvider;
    @Nullable private Integer maxInboundMessageSize;
    @Nullable private Integer maxInboundMetadataSize;
    @Nullable private Duration keepAliveTime;
    @Nullable private Duration keepAliveTimeout;
    @Nullable private Boolean keepAliveWithoutCalls;
    @Nullable private ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator;
    @Nullable private Credentials credentials;
    @Nullable private ChannelPrimer channelPrimer;
    private ChannelPoolSettings channelPoolSettings;
    @Nullable private Boolean attemptDirectPath;
    @Nullable private Boolean allowNonDefaultServiceAccount;
    @Nullable private ImmutableMap<String, ?> directPathServiceConfig;

    private Builder() {
      processorCount = Runtime.getRuntime().availableProcessors();
      envProvider = System::getenv;
      channelPoolSettings = ChannelPoolSettings.staticallySized(1);
    }

    private Builder(InstantiatingGrpcChannelProvider provider) {
      this.processorCount = provider.processorCount;
      this.executor = provider.executor;
      this.headerProvider = provider.headerProvider;
      this.endpoint = provider.endpoint;
      this.envProvider = provider.envProvider;
      this.interceptorProvider = provider.interceptorProvider;
      this.maxInboundMessageSize = provider.maxInboundMessageSize;
      this.maxInboundMetadataSize = provider.maxInboundMetadataSize;
      this.keepAliveTime = provider.keepAliveTime;
      this.keepAliveTimeout = provider.keepAliveTimeout;
      this.keepAliveWithoutCalls = provider.keepAliveWithoutCalls;
      this.channelConfigurator = provider.channelConfigurator;
      this.credentials = provider.credentials;
      this.channelPrimer = provider.channelPrimer;
      this.channelPoolSettings = provider.channelPoolSettings;
      this.attemptDirectPath = provider.attemptDirectPath;
      this.allowNonDefaultServiceAccount = provider.allowNonDefaultServiceAccount;
      this.directPathServiceConfig = provider.directPathServiceConfig;
      this.mtlsProvider = provider.mtlsProvider;
    }

    /**
     * Sets the number of available CPUs, used internally for testing.
     *
     * @deprecated CPU based channel scaling is deprecated, please use RPC based scaling instead via
     *     {@link Builder#setChannelPoolSettings(ChannelPoolSettings)}
     */
    @Deprecated
    Builder setProcessorCount(int processorCount) {
      this.processorCount = processorCount;
      return this;
    }

    /**
     * Sets the Executor for this TransportChannelProvider.
     *
     * <p>This is optional; if it is not provided, needsExecutor() will return true, meaning that an
     * Executor must be provided when getChannel is called on the constructed
     * TransportChannelProvider instance. Note: GrpcTransportProvider will automatically provide its
     * own Executor in this circumstance when it calls getChannel.
     */
    public Builder setExecutor(Executor executor) {
      this.executor = executor;
      return this;
    }

    /** @deprecated Please use {@link #setExecutor(Executor)}. */
    @Deprecated
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      return setExecutor((Executor) executorProvider.getExecutor());
    }

    /**
     * Sets the HeaderProvider for this TransportChannelProvider.
     *
     * <p>This is optional; if it is not provided, needsHeaders() will return true, meaning that
     * headers must be provided when getChannel is called on the constructed
     * TransportChannelProvider instance.
     */
    public Builder setHeaderProvider(HeaderProvider headerProvider) {
      this.headerProvider = headerProvider;
      return this;
    }

    /** Sets the endpoint used to reach the service, eg "localhost:8080". */
    public Builder setEndpoint(String endpoint) {
      validateEndpoint(endpoint);
      this.endpoint = endpoint;
      return this;
    }

    @VisibleForTesting
    Builder setMtlsProvider(MtlsProvider mtlsProvider) {
      this.mtlsProvider = mtlsProvider;
      return this;
    }

    /**
     * Sets the GrpcInterceptorProvider for this TransportChannelProvider.
     *
     * <p>The provider will be called once for each underlying gRPC ManagedChannel that is created.
     * It is recommended to return a new list of new interceptors on each call so that interceptors
     * are not shared among channels, but this is not required.
     */
    public Builder setInterceptorProvider(GrpcInterceptorProvider interceptorProvider) {
      this.interceptorProvider = interceptorProvider;
      return this;
    }

    public String getEndpoint() {
      return endpoint;
    }

    /** The maximum message size allowed to be received on the channel. */
    public Builder setMaxInboundMessageSize(Integer max) {
      this.maxInboundMessageSize = max;
      return this;
    }

    /** The maximum message size allowed to be received on the channel. */
    public Integer getMaxInboundMessageSize() {
      return maxInboundMessageSize;
    }

    /** The maximum metadata size allowed to be received on the channel. */
    @BetaApi(
        "The surface for maximum metadata size is not stable yet and may change in the future.")
    public Builder setMaxInboundMetadataSize(Integer max) {
      this.maxInboundMetadataSize = max;
      return this;
    }

    /** The maximum metadata size allowed to be received on the channel. */
    @BetaApi(
        "The surface for maximum metadata size is not stable yet and may change in the future.")
    public Integer getMaxInboundMetadataSize() {
      return maxInboundMetadataSize;
    }

    /** The time without read activity before sending a keepalive ping. */
    public Builder setKeepAliveTime(Duration duration) {
      this.keepAliveTime = duration;
      return this;
    }

    /** The time without read activity before sending a keepalive ping. */
    public Duration getKeepAliveTime() {
      return keepAliveTime;
    }

    /** The time without read activity after sending a keepalive ping. */
    public Builder setKeepAliveTimeout(Duration duration) {
      this.keepAliveTimeout = duration;
      return this;
    }

    /** The time without read activity after sending a keepalive ping. */
    public Duration getKeepAliveTimeout() {
      return keepAliveTimeout;
    }

    /** Whether keepalive will be performed when there are no outstanding RPCs. */
    public Builder setKeepAliveWithoutCalls(Boolean keepalive) {
      this.keepAliveWithoutCalls = keepalive;
      return this;
    }

    /** Whether keepalive will be performed when there are no outstanding RPCs. */
    public Boolean getKeepAliveWithoutCalls() {
      return keepAliveWithoutCalls;
    }

    /** @deprecated Please use {@link #setChannelPoolSettings(ChannelPoolSettings)} */
    @Deprecated
    public int getPoolSize() {
      return channelPoolSettings.getInitialChannelCount();
    }

    /** @deprecated Please use {@link #setChannelPoolSettings(ChannelPoolSettings)} */
    @Deprecated
    public Builder setPoolSize(int poolSize) {
      channelPoolSettings = ChannelPoolSettings.staticallySized(poolSize);
      return this;
    }

    /** @deprecated Please use {@link #setChannelPoolSettings(ChannelPoolSettings)} */
    @Deprecated
    public Builder setChannelsPerCpu(double multiplier) {
      return setChannelsPerCpu(multiplier, 100);
    }

    /** @deprecated Please use {@link #setChannelPoolSettings(ChannelPoolSettings)} */
    @Deprecated
    public Builder setChannelsPerCpu(double multiplier, int maxChannels) {
      Preconditions.checkArgument(multiplier > 0, "multiplier must be positive");
      Preconditions.checkArgument(maxChannels > 0, "maxChannels must be positive");

      int channelCount = (int) Math.ceil(processorCount * multiplier);
      if (channelCount > maxChannels) {
        channelCount = maxChannels;
      }
      return setChannelPoolSettings(ChannelPoolSettings.staticallySized(channelCount));
    }

    @BetaApi("Channel pool sizing api is not yet stable")
    public Builder setChannelPoolSettings(ChannelPoolSettings settings) {
      this.channelPoolSettings = settings;
      return this;
    }

    public Builder setCredentials(Credentials credentials) {
      this.credentials = credentials;
      return this;
    }

    /**
     * By setting a channelPrimer, the ChannelPool created by the provider will be refreshing
     * ChannelPool. channelPrimer will be invoked periodically when the channels are refreshed
     *
     * <p>This is public only for technical reasons, for advanced usage.
     *
     * @param channelPrimer invoked when the channels are refreshed
     * @return builder for the provider
     */
    @InternalApi("For internal use by google-cloud-java clients only")
    public Builder setChannelPrimer(ChannelPrimer channelPrimer) {
      this.channelPrimer = channelPrimer;
      return this;
    }

    /** Whether attempt DirectPath. */
    @InternalApi("For internal use by google-cloud-java clients only")
    public Builder setAttemptDirectPath(boolean attemptDirectPath) {
      this.attemptDirectPath = attemptDirectPath;
      return this;
    }

    /** Whether allow non-default service account for DirectPath. */
    @InternalApi("For internal use by google-cloud-java clients only")
    public Builder setAllowNonDefaultServiceAccount(boolean allowNonDefaultServiceAccount) {
      this.allowNonDefaultServiceAccount = allowNonDefaultServiceAccount;
      return this;
    }

    /**
     * Sets a service config for direct path. If direct path is not enabled, the provided service
     * config will be ignored.
     *
     * <p>See <a href=
     * "https://github.com/grpc/grpc-proto/blob/master/grpc/service_config/service_config.proto">
     * the service config proto definition</a> for more details.
     */
    @InternalApi("For internal use by google-cloud-java clients only")
    public Builder setDirectPathServiceConfig(Map<String, ?> serviceConfig) {
      Preconditions.checkNotNull(serviceConfig, "serviceConfig");
      this.directPathServiceConfig = ImmutableMap.copyOf(serviceConfig);
      return this;
    }

    public InstantiatingGrpcChannelProvider build() {
      return new InstantiatingGrpcChannelProvider(this);
    }

    /**
     * Add a callback that can intercept channel creation.
     *
     * <p>This can be used for advanced configuration like setting the netty event loop. The
     * callback will be invoked with a fully configured channel builder, which the callback can
     * augment or replace.
     */
    @BetaApi("Surface for advanced channel configuration is not yet stable")
    public Builder setChannelConfigurator(
        @Nullable ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator) {
      this.channelConfigurator = channelConfigurator;
      return this;
    }

    @Nullable
    public ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> getChannelConfigurator() {
      return channelConfigurator;
    }
  }

  private static ImmutableMap<String, ?> getDefaultDirectPathServiceConfig() {
    // When channel pooling is enabled, force the pick_first grpclb strategy.
    // This is necessary to avoid the multiplicative effect of creating channel pool with
    // `poolSize` number of `ManagedChannel`s, each with a `subSetting` number of number of
    // subchannels.
    // See the service config proto definition for more details:
    // https://github.com/grpc/grpc-proto/blob/master/grpc/service_config/service_config.proto
    ImmutableMap<String, Object> pickFirstStrategy =
        ImmutableMap.<String, Object>of("pick_first", ImmutableMap.of());

    ImmutableMap<String, Object> childPolicy =
        ImmutableMap.<String, Object>of("childPolicy", ImmutableList.of(pickFirstStrategy));

    ImmutableMap<String, Object> grpcLbPolicy =
        ImmutableMap.<String, Object>of("grpclb", childPolicy);

    return ImmutableMap.<String, Object>of("loadBalancingConfig", ImmutableList.of(grpcLbPolicy));
  }

  private static void validateEndpoint(String endpoint) {
    int colon = endpoint.lastIndexOf(':');
    if (colon < 0) {
      throw new IllegalArgumentException(
          String.format("invalid endpoint, expecting \"<host>:<port>\""));
    }
    Integer.parseInt(endpoint.substring(colon + 1));
  }
}
