/*
 * Copyright 2017, Google Inc. All rights reserved.
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
package com.google.api.gax.httpjson;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.GenericData;
import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.core.BackgroundResource;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@BetaApi
public class ManagedHttpJsonChannel implements HttpJsonChannel, BackgroundResource {
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private final Executor executor;
  private final HttpTransport httpTransport;
  private final String endpoint;
  private final JsonFactory jsonFactory;
  private final ImmutableList<HttpJsonHeaderEnhancer> headerEnhancers;

  private ManagedHttpJsonChannel(
      Executor executor,
      HttpTransport httpTransport,
      String endpoint,
      JsonFactory jsonFactory,
      List<HttpJsonHeaderEnhancer> headerEnhancers) {
    this.executor = executor;
    this.httpTransport = httpTransport;
    this.endpoint = endpoint;
    this.jsonFactory = jsonFactory;
    this.headerEnhancers = ImmutableList.copyOf(headerEnhancers);
  }

  public <ResponseT, RequestT> ApiFuture<ResponseT> issueFutureUnaryCall(
      HttpJsonCallOptions callOptions, RequestT request) {

    final SettableApiFuture responseFuture = SettableApiFuture.create();

    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              GenericData tokenRequest = new GenericData();
              // TODO convert request to GenericData

              UrlEncodedContent content = new UrlEncodedContent(tokenRequest);
              HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
              HttpRequest request =
                  requestFactory.buildPostRequest(new GenericUrl(endpoint), content);
              for (HttpJsonHeaderEnhancer enhancer : headerEnhancers) {
                enhancer.enhance(request.getHeaders());
              }
              request.setParser(new JsonObjectParser(jsonFactory));
              HttpResponse response = request.execute();

              GenericData responseData = response.parseAs(GenericData.class);
              // TODO convert GenericData to response type

              responseFuture.set(responseData);

            } catch (Exception e) {
              responseFuture.setException(e);
            }
          }
        });

    return responseFuture;
  }

  // TODO implement the following

  @Override
  public void shutdown() {}

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public void shutdownNow() {}

  @Override
  public boolean awaitTermination(long duration, TimeUnit unit) throws InterruptedException {
    return false;
  }

  @Override
  public void close() throws Exception {}

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private Executor executor;
    private HttpTransport httpTransport;
    private String endpoint;
    private JsonFactory jsonFactory = JSON_FACTORY;
    private List<HttpJsonHeaderEnhancer> headerEnhancers;

    private Builder() {}

    public Builder setExecutor(Executor executor) {
      this.executor = executor;
      return this;
    }

    public Builder setHttpTransport(HttpTransport httpTransport) {
      this.httpTransport = httpTransport;
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

    public ManagedHttpJsonChannel build() {
      return new ManagedHttpJsonChannel(
          executor, httpTransport, endpoint, jsonFactory, headerEnhancers);
    }
  }
}
