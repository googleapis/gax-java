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
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.core.BackgroundResource;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/** Implementation of HttpJsonChannel which can issue http-json calls. */
@BetaApi
public class ManagedHttpJsonChannel implements HttpJsonChannel, BackgroundResource {
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private final Executor executor;
  private final String endpoint;
  private final JsonFactory jsonFactory;
  private final ImmutableList<HttpJsonHeaderEnhancer> headerEnhancers;
  private final HttpTransport httpTransport;

  private boolean isTransportShutdown;

  private ManagedHttpJsonChannel(
      Executor executor,
      String endpoint,
      JsonFactory jsonFactory,
      List<HttpJsonHeaderEnhancer> headerEnhancers,
      @Nullable HttpTransport httpTransport) {
    this.executor = executor;
    this.endpoint = endpoint;
    this.jsonFactory = jsonFactory;
    this.headerEnhancers = ImmutableList.copyOf(headerEnhancers);
    this.httpTransport = httpTransport == null ? new NetHttpTransport() : httpTransport;
  }

  @Override
  public <ResponseT, RequestT> ApiFuture<ResponseT> issueFutureUnaryCall(
      HttpJsonCallOptions callOptions,
      RequestT request,
      ApiMethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    final SettableApiFuture<ResponseT> responseFuture = SettableApiFuture.create();

    HttpRequestRunnable<RequestT, ResponseT> runnable =
        HttpRequestRunnable.<RequestT, ResponseT>newBuilder()
            .setResponseFuture(responseFuture)
            .setApiMethodDescriptor(methodDescriptor)
            .setHeaderEnhancers(headerEnhancers)
            .setHttpJsonCallOptions(callOptions)
            .setHttpTransport(httpTransport)
            .setJsonFactory(jsonFactory)
            .setRequest(request)
            .setEndpoint(endpoint)
            .build();

    executor.execute(runnable);

    return responseFuture;
  }

  @Override
  public synchronized void shutdown() {
    if (isTransportShutdown) {
      return;
    }
    try {
      httpTransport.shutdown();
      isTransportShutdown = true;
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public boolean isShutdown() {
    return isTransportShutdown;
  }

  @Override
  public boolean isTerminated() {
    return isTransportShutdown;
  }

  @Override
  public void shutdownNow() {
    shutdown();
  }

  @Override
  public boolean awaitTermination(long duration, TimeUnit unit) throws InterruptedException {
    // TODO
    return false;
  }

  @Override
  public void close() {}

  public static Builder newBuilder() {
    return new Builder().setHeaderEnhancers(new LinkedList<HttpJsonHeaderEnhancer>());
  }

  public static class Builder {
    private Executor executor;
    private String endpoint;
    private JsonFactory jsonFactory = JSON_FACTORY;
    private List<HttpJsonHeaderEnhancer> headerEnhancers;
    private HttpTransport httpTransport;

    private Builder() {}

    public Builder setExecutor(Executor executor) {
      this.executor = executor;
      return this;
    }

    public Builder setEndpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    public Builder setHeaderEnhancers(List<HttpJsonHeaderEnhancer> headerEnhancers) {
      this.headerEnhancers = headerEnhancers;
      return this;
    }

    public Builder setHttpTransport(HttpTransport httpTransport) {
      this.httpTransport = httpTransport;
      return this;
    }

    public ManagedHttpJsonChannel build() {
      Preconditions.checkNotNull(executor);
      Preconditions.checkNotNull(endpoint);
      return new ManagedHttpJsonChannel(
          executor, endpoint, jsonFactory, headerEnhancers, httpTransport);
    }
  }
}
