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
package com.google.api.gax.httpjson;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.core.BetaApi;
import com.google.api.core.InternalExtensionOnly;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.rpc.mtls.MtlsProvider;
import com.google.auth.Credentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * InstantiatingHttpJsonChannelProvider is a TransportChannelProvider which constructs a {@link
 * ManagedHttpJsonChannel} with a number of configured inputs every time getChannel(...) is called.
 * These inputs include a port, a service address, and credentials.
 *
 * <p>The credentials can either be supplied directly (by providing a FixedCredentialsProvider to
 * Builder.setCredentialsProvider()) or acquired implicitly from Application Default Credentials (by
 * providing a GoogleCredentialsProvider to Builder.setCredentialsProvider()).
 *
 * <p>The client lib header and generator header values are used to form a value that goes into the
 * http header of requests to the service.
 */
@BetaApi
@InternalExtensionOnly
public final class InstantiatingHttpJsonChannelProvider implements TransportChannelProvider {
  private final Executor executor;
  private final HeaderProvider headerProvider;
  private final String endpoint;
  private final HttpTransport httpTransport;
  private final MtlsProvider mtlsProvider;

  private InstantiatingHttpJsonChannelProvider(
      Executor executor, HeaderProvider headerProvider, String endpoint) {
    this.executor = executor;
    this.headerProvider = headerProvider;
    this.endpoint = endpoint;
    this.httpTransport = null;
    this.mtlsProvider = new MtlsProvider();
  }

  private InstantiatingHttpJsonChannelProvider(
      Executor executor,
      HeaderProvider headerProvider,
      String endpoint,
      HttpTransport httpTransport,
      MtlsProvider mtlsProvider) {
    this.executor = executor;
    this.headerProvider = headerProvider;
    this.endpoint = endpoint;
    this.httpTransport = httpTransport;
    this.mtlsProvider = mtlsProvider;
  }

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
  public boolean needsEndpoint() {
    return endpoint == null;
  }

  @Override
  public TransportChannelProvider withEndpoint(String endpoint) {
    return toBuilder().setEndpoint(endpoint).build();
  }

  @Override
  @BetaApi("The surface for customizing pool size is not stable yet and may change in the future.")
  public boolean acceptsPoolSize() {
    return false;
  }

  @Override
  @BetaApi("The surface for customizing pool size is not stable yet and may change in the future.")
  public TransportChannelProvider withPoolSize(int size) {
    throw new UnsupportedOperationException(
        "InstantiatingHttpJsonChannelProvider doesn't allow pool size customization");
  }

  @Override
  public String getTransportName() {
    return HttpJsonTransportChannel.getHttpJsonTransportName();
  }

  @Override
  public TransportChannel getTransportChannel() throws IOException {
    if (needsExecutor()) {
      throw new IllegalStateException("getTransportChannel() called when needsExecutor() is true");
    } else if (needsHeaders()) {
      throw new IllegalStateException("getTransportChannel() called when needsHeaders() is true");
    } else {
      try {
        return createChannel();
      } catch (GeneralSecurityException e) {
        throw new IOException(e);
      }
    }
  }

  @Override
  public boolean needsCredentials() {
    return false;
  }

  @Override
  public TransportChannelProvider withCredentials(Credentials credentials) {
    throw new UnsupportedOperationException(
        "InstantiatingHttpJsonChannelProvider doesn't need credentials");
  }

  HttpTransport createHttpTransport() throws IOException, GeneralSecurityException {
    if (mtlsProvider.useMtlsClientCertificate()) {
      KeyStore mtlsKeyStore = mtlsProvider.getKeyStore();
      if (mtlsKeyStore != null) {
        return new NetHttpTransport.Builder().trustCertificates(null, mtlsKeyStore, "").build();
      }
    }
    return null;
  }

  private TransportChannel createChannel() throws IOException, GeneralSecurityException {
    Map<String, String> headers = headerProvider.getHeaders();

    List<HttpJsonHeaderEnhancer> headerEnhancers = Lists.newArrayList();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      headerEnhancers.add(HttpJsonHeaderEnhancers.create(header.getKey(), header.getValue()));
    }

    HttpTransport httpTransportToUse = httpTransport;
    if (httpTransportToUse == null) {
      httpTransportToUse = createHttpTransport();
    }

    ManagedHttpJsonChannel channel =
        ManagedHttpJsonChannel.newBuilder()
            .setEndpoint(endpoint)
            .setHeaderEnhancers(headerEnhancers)
            .setExecutor(executor)
            .setHttpTransport(httpTransportToUse)
            .build();

    return HttpJsonTransportChannel.newBuilder().setManagedChannel(channel).build();
  }

  /** The endpoint to be used for the channel. */
  public String getEndpoint() {
    return endpoint;
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
    private Executor executor;
    private HeaderProvider headerProvider;
    private String endpoint;
    private HttpTransport httpTransport;
    private MtlsProvider mtlsProvider = new MtlsProvider();

    private Builder() {}

    private Builder(InstantiatingHttpJsonChannelProvider provider) {
      this.executor = provider.executor;
      this.headerProvider = provider.headerProvider;
      this.endpoint = provider.endpoint;
      this.httpTransport = provider.httpTransport;
      this.mtlsProvider = provider.mtlsProvider;
    }

    /**
     * Sets the Executor for this TransportChannelProvider.
     *
     * <p>This is optional; if it is not provided, needsExecutor() will return true, meaning that an
     * Executor must be provided when getChannel is called on the constructed
     * TransportChannelProvider instance. Note: InstantiatingHttpJsonChannelProvider will
     * automatically provide its own Executor in this circumstance when it calls getChannel.
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
      this.endpoint = endpoint;
      return this;
    }

    /** Sets the HTTP transport to be used. */
    public Builder setHttpTransport(HttpTransport httpTransport) {
      this.httpTransport = httpTransport;
      return this;
    }

    public String getEndpoint() {
      return endpoint;
    }

    @VisibleForTesting
    Builder setMtlsProvider(MtlsProvider mtlsProvider) {
      this.mtlsProvider = mtlsProvider;
      return this;
    }

    public InstantiatingHttpJsonChannelProvider build() {
      return new InstantiatingHttpJsonChannelProvider(
          executor, headerProvider, endpoint, httpTransport, mtlsProvider);
    }
  }
}
