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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.GenericData;
import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.pathtemplate.PathTemplate;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

@BetaApi
public class ManagedHttpJsonChannel implements HttpJsonChannel, BackgroundResource {
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private final Executor executor;
  private final String endpoint;
  private final JsonFactory jsonFactory;
  private final ImmutableList<HttpJsonHeaderEnhancer> headerEnhancers;
  private final GoogleCredentials googleCredentials;
  private final HttpTransport httpTransport;
  private final HttpRequestFactory requestFactory;

  private ManagedHttpJsonChannel(
      Executor executor,
      String endpoint,
      JsonFactory jsonFactory,
      List<HttpJsonHeaderEnhancer> headerEnhancers,
      @Nullable GoogleCredentials googleCredentials) {
    this.executor = executor;
    this.endpoint = endpoint;
    this.jsonFactory = jsonFactory;
    this.headerEnhancers = ImmutableList.copyOf(headerEnhancers);

    HttpRequestFactory requestFactory;
    HttpTransport httpTransport;
    GoogleCredentials defaultCredentials = null;
    if (googleCredentials == null) {
      try {
        defaultCredentials = GoogleCredentials.getApplicationDefault();
      } catch (IOException e) {
        System.err.print(
            "No credentials given and default credentials not found. Continuing without credentials.");
      }
    }
    this.googleCredentials = googleCredentials == null ? defaultCredentials : googleCredentials;

    if (this.googleCredentials != null) {
      try {
        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        requestFactory =
            httpTransport.createRequestFactory(new HttpCredentialsAdapter(this.googleCredentials));
      } catch (GeneralSecurityException | IOException e) {
        System.err.print(
            "Failed to create HttpTransport with credentials. Creating HttpTransport without credentials.");
        httpTransport = new NetHttpTransport();
        requestFactory = httpTransport.createRequestFactory();
      }
    } else {
      httpTransport = new NetHttpTransport();
      requestFactory = httpTransport.createRequestFactory();
    }
    this.requestFactory = requestFactory;
    this.httpTransport = httpTransport;
  }

  public <ResponseT, RequestT> ApiFuture<ResponseT> issueFutureUnaryCall(
      HttpJsonCallOptions callOptions,
      final RequestT request,
      final ApiMethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    final SettableApiFuture responseFuture = SettableApiFuture.create();

    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              try (Writer stringWriter = new StringWriter()) {
                GenericData tokenRequest = new GenericData();

                HttpRequestFormatter requestBuilder = methodDescriptor.httpRequestBuilder();
                methodDescriptor.writeRequestBody(request, stringWriter);
                stringWriter.close();
                JsonHttpContent jsonHttpContent = null;
                if (!Strings.isNullOrEmpty(stringWriter.toString())) {
                  jsonFactory.createJsonParser(stringWriter.toString()).parse(tokenRequest);
                  new JsonHttpContent(jsonFactory, tokenRequest)
                      .setMediaType((new HttpMediaType("application/json")));
                }

                Map<String, String> pathParams =
                    requestBuilder.getPathParams(request, methodDescriptor.pathParams());
                PathTemplate pathPattern =
                    PathTemplate.create(methodDescriptor.endpointPathTemplate());
                String relativePath = pathPattern.instantiate(pathParams);
                GenericUrl url = new GenericUrl(endpoint + relativePath);
                Map<String, List<String>> queryParams =
                    requestBuilder.getQueryParams(request, methodDescriptor.queryParams());
                for (String queryParam : methodDescriptor.queryParams()) {
                  if (queryParams.containsKey(queryParam) && queryParams.get(queryParam) != null) {
                    url.set(queryParam, queryParams.get(queryParam));
                  }
                }

                HttpRequest httpRequest =
                    requestFactory.buildRequest(
                        methodDescriptor.httpMethod().name(), url, jsonHttpContent);

                for (HttpJsonHeaderEnhancer enhancer : headerEnhancers) {
                  enhancer.enhance(httpRequest.getHeaders());
                }

                httpRequest.setParser(new JsonObjectParser(jsonFactory));

                HttpResponse httpResponse = httpRequest.execute();

                ResponseT response =
                    methodDescriptor.parseResponse(
                        new InputStreamReader(httpResponse.getContent()));
                responseFuture.set(response);

              } catch (IOException e) {
                e.printStackTrace(System.err);
              }
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
    private String endpoint;
    private JsonFactory jsonFactory = JSON_FACTORY;
    private List<HttpJsonHeaderEnhancer> headerEnhancers;
    private GoogleCredentials googleCredentials;

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

    public Builder setCredentials(GoogleCredentials googleCredentials) {
      this.googleCredentials = googleCredentials;
      return this;
    }

    public ManagedHttpJsonChannel build() {
      return new ManagedHttpJsonChannel(
          executor, endpoint, jsonFactory, headerEnhancers, googleCredentials);
    }
  }
}
