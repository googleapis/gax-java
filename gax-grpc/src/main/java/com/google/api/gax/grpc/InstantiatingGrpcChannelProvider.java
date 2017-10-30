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

import com.google.api.core.BetaApi;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;

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
@BetaApi
public final class InstantiatingGrpcChannelProvider implements TransportChannelProvider {
  private final ExecutorProvider executorProvider;
  private final HeaderProvider headerProvider;
  private final String serviceAddress;
  private final int port;
  @Nullable private final Integer maxInboundMessageSize;

  private InstantiatingGrpcChannelProvider(
      ExecutorProvider executorProvider,
      HeaderProvider headerProvider,
      String serviceAddress,
      int port,
      Integer maxInboundMessageSize) {
    this.executorProvider = executorProvider;
    this.headerProvider = headerProvider;
    this.serviceAddress = serviceAddress;
    this.port = port;
    this.maxInboundMessageSize = maxInboundMessageSize;
  }

  @Override
  public boolean needsExecutor() {
    return executorProvider == null;
  }

  @Override
  public TransportChannelProvider withExecutor(ScheduledExecutorService executor) {
    return toBuilder().setExecutorProvider(FixedExecutorProvider.of(executor)).build();
  }

  @Override
  public boolean needsHeaders() {
    return headerProvider == null;
  }

  @Override
  public TransportChannelProvider withHeaders(Map<String, String> headers) {
    return toBuilder().setHeaderProvider(FixedHeaderProvider.of(headers)).build();
  }

  @Override
  public String getTransportName() {
    return GrpcTransportChannel.getGrpcTransportName();
  }

  @Override
  public TransportChannel getTransportChannel() throws IOException {
    if (needsExecutor()) {
      throw new IllegalStateException("getTransportChannel() called when needsExecutor() is true");
    } else if (needsHeaders()) {
      throw new IllegalStateException("getTransportChannel() called when needsHeaders() is true");
    } else {
      return createChannel();
    }
  }

  private TransportChannel createChannel() throws IOException {
    ScheduledExecutorService executor = executorProvider.getExecutor();
    Map<String, String> headers = headerProvider.getHeaders();

    List<ClientInterceptor> interceptors = new ArrayList<>();
    interceptors.add(new GrpcHeaderInterceptor(headers));

    ManagedChannelBuilder builder =
        ManagedChannelBuilder.forAddress(serviceAddress, port)
            .intercept(interceptors)
            .executor(executor);
    if (maxInboundMessageSize != null) {
      builder.maxInboundMessageSize(maxInboundMessageSize);
    }

    return GrpcTransportChannel.newBuilder().setManagedChannel(builder.build()).build();
  }

  /** The endpoint to be used for the channel. */
  public String getEndpoint() {
    return serviceAddress + ':' + port;
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
    private ExecutorProvider executorProvider;
    private HeaderProvider headerProvider;
    private String serviceAddress;
    private int port;
    private Integer maxInboundMessageSize;

    private Builder() {}

    private Builder(InstantiatingGrpcChannelProvider provider) {
      this.executorProvider = provider.executorProvider;
      this.headerProvider = provider.headerProvider;
      this.serviceAddress = provider.serviceAddress;
      this.port = provider.port;
      this.maxInboundMessageSize = provider.maxInboundMessageSize;
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
      int colon = endpoint.indexOf(':');
      if (colon < 0) {
        throw new IllegalArgumentException(
            String.format("invalid endpoint, expecting \"<host>:<port>\""));
      }
      this.port = Integer.parseInt(endpoint.substring(colon + 1));
      this.serviceAddress = endpoint.substring(0, colon);
      return this;
    }

    public String getEndpoint() {
      return serviceAddress + ':' + port;
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

    public InstantiatingGrpcChannelProvider build() {
      return new InstantiatingGrpcChannelProvider(
          executorProvider, headerProvider, serviceAddress, port, maxInboundMessageSize);
    }
  }
}
