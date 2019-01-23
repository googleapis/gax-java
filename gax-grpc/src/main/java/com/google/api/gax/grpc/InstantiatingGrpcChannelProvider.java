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
import com.google.api.core.InternalExtensionOnly;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.alts.GoogleDefaultChannelBuilder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
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
  private final int processorCount;
  private final ExecutorProvider executorProvider;
  private final HeaderProvider headerProvider;
  private final String endpoint;
  @Nullable private final GrpcInterceptorProvider interceptorProvider;
  @Nullable private final Integer maxInboundMessageSize;
  @Nullable private final Integer maxInboundMetadataSize;
  @Nullable private final Duration keepAliveTime;
  @Nullable private final Duration keepAliveTimeout;
  @Nullable private final Boolean keepAliveWithoutCalls;
  @Nullable private final Integer poolSize;

  @Nullable
  private final ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator;

  private InstantiatingGrpcChannelProvider(Builder builder) {
    this.processorCount = builder.processorCount;
    this.executorProvider = builder.executorProvider;
    this.headerProvider = builder.headerProvider;
    this.endpoint = builder.endpoint;
    this.interceptorProvider = builder.interceptorProvider;
    this.maxInboundMessageSize = builder.maxInboundMessageSize;
    this.maxInboundMetadataSize = builder.maxInboundMetadataSize;
    this.keepAliveTime = builder.keepAliveTime;
    this.keepAliveTimeout = builder.keepAliveTimeout;
    this.keepAliveWithoutCalls = builder.keepAliveWithoutCalls;
    this.poolSize = builder.poolSize;
    this.channelConfigurator = builder.channelConfigurator;
  }

  @Override
  public boolean needsExecutor() {
    return executorProvider == null;
  }

  @Override
  public TransportChannelProvider withExecutor(ScheduledExecutorService executor) {
    return toBuilder().setExecutorProvider(FixedExecutorProvider.create(executor)).build();
  }

  @Override
  @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
  public boolean needsHeaders() {
    return headerProvider == null;
  }

  @Override
  @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
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

  @Override
  public TransportChannelProvider withEndpoint(String endpoint) {
    validateEndpoint(endpoint);
    return toBuilder().setEndpoint(endpoint).build();
  }

  @Override
  @BetaApi("The surface for customizing pool size is not stable yet and may change in the future.")
  public boolean acceptsPoolSize() {
    return poolSize == null;
  }

  @Override
  @BetaApi("The surface for customizing pool size is not stable yet and may change in the future.")
  public TransportChannelProvider withPoolSize(int size) {
    Preconditions.checkState(acceptsPoolSize(), "pool size already set to %s", poolSize);
    return toBuilder().setPoolSize(size).build();
  }

  @Override
  public TransportChannel getTransportChannel() throws IOException {
    if (needsExecutor()) {
      throw new IllegalStateException("getTransportChannel() called when needsExecutor() is true");
    } else if (needsHeaders()) {
      throw new IllegalStateException("getTransportChannel() called when needsHeaders() is true");
    } else if (needsEndpoint()) {
      throw new IllegalStateException("getTransportChannel() called when needsEndpoint() is true");
    } else {
      return createChannel();
    }
  }

  private TransportChannel createChannel() throws IOException {
    ManagedChannel outerChannel;

    if (poolSize == null || poolSize == 1) {
      outerChannel = createSingleChannel();
    } else {
      ImmutableList.Builder<ManagedChannel> channels = ImmutableList.builder();

      for (int i = 0; i < poolSize; i++) {
        channels.add(createSingleChannel());
      }
      outerChannel = new ChannelPool(channels.build());
    }

    return GrpcTransportChannel.create(outerChannel);
  }

  private ManagedChannel createSingleChannel() throws IOException {
    ScheduledExecutorService executor = executorProvider.getExecutor();
    GrpcHeaderInterceptor headerInterceptor =
        new GrpcHeaderInterceptor(headerProvider.getHeaders());
    GrpcMetadataHandlerInterceptor metadataHandlerInterceptor =
        new GrpcMetadataHandlerInterceptor();

    int colon = endpoint.indexOf(':');
    if (colon < 0) {
      throw new IllegalStateException("invalid endpoint - should have been validated: " + endpoint);
    }
    int port = Integer.parseInt(endpoint.substring(colon + 1));
    String serviceAddress = endpoint.substring(0, colon);

    ManagedChannelBuilder builder =
        GoogleDefaultChannelBuilder.forAddress(serviceAddress, port)
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

    return builder.build();
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
    private int processorCount;
    private ExecutorProvider executorProvider;
    private HeaderProvider headerProvider;
    private String endpoint;
    @Nullable private GrpcInterceptorProvider interceptorProvider;
    @Nullable private Integer maxInboundMessageSize;
    @Nullable private Integer maxInboundMetadataSize;
    @Nullable private Duration keepAliveTime;
    @Nullable private Duration keepAliveTimeout;
    @Nullable private Boolean keepAliveWithoutCalls;
    @Nullable private Integer poolSize;
    @Nullable private ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator;

    private Builder() {
      processorCount = Runtime.getRuntime().availableProcessors();
    }

    private Builder(InstantiatingGrpcChannelProvider provider) {
      this.processorCount = provider.processorCount;
      this.executorProvider = provider.executorProvider;
      this.headerProvider = provider.headerProvider;
      this.endpoint = provider.endpoint;
      this.interceptorProvider = provider.interceptorProvider;
      this.maxInboundMessageSize = provider.maxInboundMessageSize;
      this.maxInboundMetadataSize = provider.maxInboundMetadataSize;
      this.keepAliveTime = provider.keepAliveTime;
      this.keepAliveTimeout = provider.keepAliveTimeout;
      this.keepAliveWithoutCalls = provider.keepAliveWithoutCalls;
      this.poolSize = provider.poolSize;
      this.channelConfigurator = provider.channelConfigurator;
    }

    /** Sets the number of available CPUs, used internally for testing. */
    Builder setProcessorCount(int processorCount) {
      this.processorCount = processorCount;
      return this;
    }

    /**
     * Sets the ExecutorProvider for this TransportChannelProvider.
     *
     * <p>This is optional; if it is not provided, needsExecutor() will return true, meaning that an
     * Executor must be provided when getChannel is called on the constructed
     * TransportChannelProvider instance. Note: GrpcTransportProvider will automatically provide its
     * own Executor in this circumstance when it calls getChannel.
     */
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      this.executorProvider = executorProvider;
      return this;
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

    /**
     * Number of underlying grpc channels to open. Calls will be load balanced round robin across
     * them.
     */
    public int getPoolSize() {
      if (poolSize == null) {
        return 1;
      }
      return poolSize;
    }

    /**
     * Number of underlying grpc channels to open. Calls will be load balanced round robin across
     * them
     */
    public Builder setPoolSize(int poolSize) {
      Preconditions.checkArgument(poolSize > 0, "Pool size must be positive");
      this.poolSize = poolSize;
      return this;
    }

    /** Sets the number of channels relative to the available CPUs. */
    public Builder setChannelsPerCpu(double multiplier) {
      return setChannelsPerCpu(multiplier, 100);
    }

    public Builder setChannelsPerCpu(double multiplier, int maxChannels) {
      Preconditions.checkArgument(multiplier > 0, "multiplier must be positive");
      Preconditions.checkArgument(maxChannels > 0, "maxChannels must be positive");

      int channelCount = (int) Math.ceil(processorCount * multiplier);
      if (channelCount > maxChannels) {
        channelCount = maxChannels;
      }
      return setPoolSize(channelCount);
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

  private static void validateEndpoint(String endpoint) {
    int colon = endpoint.indexOf(':');
    if (colon < 0) {
      throw new IllegalArgumentException(
          String.format("invalid endpoint, expecting \"<host>:<port>\""));
    }
    Integer.parseInt(endpoint.substring(colon + 1));
  }
}
