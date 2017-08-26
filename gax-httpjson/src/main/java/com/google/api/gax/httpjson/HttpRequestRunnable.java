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
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.util.GenericData;
import com.google.api.core.SettableApiFuture;
import com.google.api.pathtemplate.PathTemplate;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** A runnable object that creates and executes an HTTP request. */
class HttpRequestRunnable<RequestT, ResponseT> implements Runnable {
  private final HttpJsonCallOptions callOptions;
  private final RequestT request;
  private final ApiMethodDescriptor<RequestT, ResponseT> methodDescriptor;
  private final HttpTransport httpTransport;
  private final String endpoint;
  private final JsonFactory jsonFactory;
  private final ImmutableList<HttpJsonHeaderEnhancer> headerEnhancers;
  private final SettableApiFuture<ResponseT> responseFuture;

  private HttpRequestRunnable(
      final HttpJsonCallOptions callOptions,
      final RequestT request,
      final ApiMethodDescriptor<RequestT, ResponseT> methodDescriptor,
      final HttpTransport httpTransport,
      String endpoint,
      JsonFactory jsonFactory,
      List<HttpJsonHeaderEnhancer> headerEnhancers,
      SettableApiFuture responseFuture) {
    this.endpoint = endpoint;
    this.jsonFactory = jsonFactory;
    this.headerEnhancers = ImmutableList.copyOf(headerEnhancers);
    this.callOptions = callOptions;
    this.request = request;
    this.methodDescriptor = methodDescriptor;
    this.httpTransport = httpTransport;
    this.responseFuture = responseFuture;
  }

  @Override
  public void run() {
    try (Writer stringWriter = new StringWriter()) {
      GenericData tokenRequest = new GenericData();

      HttpRequestFactory requestFactory;
      GoogleCredentials credentials = (GoogleCredentials) callOptions.getCredentials();
      if (credentials != null) {
        requestFactory =
            httpTransport.createRequestFactory(new HttpCredentialsAdapter(credentials));
      } else {
        requestFactory = httpTransport.createRequestFactory();
      }

      // Create HTTP request body.
      HttpRequestFormatter requestBuilder = methodDescriptor.getHttpRequestBuilder();
      methodDescriptor.writeRequestBody(request, stringWriter);
      stringWriter.close();
      JsonHttpContent jsonHttpContent = null;
      if (!Strings.isNullOrEmpty(stringWriter.toString())) {
        jsonFactory.createJsonParser(stringWriter.toString()).parse(tokenRequest);
        jsonHttpContent =
            new JsonHttpContent(jsonFactory, tokenRequest)
                .setMediaType((new HttpMediaType("application/json")));
      }

      // Populate HTTP path and query parameters.
      Map<String, String> pathParams =
          requestBuilder.getPathParams(request, methodDescriptor.getPathParams());
      PathTemplate pathPattern = PathTemplate.create(methodDescriptor.endpointPathTemplate());
      String relativePath = pathPattern.instantiate(pathParams);
      GenericUrl url = new GenericUrl(endpoint + relativePath);
      Map<String, List<String>> queryParams =
          requestBuilder.getQueryParams(request, methodDescriptor.getQueryParams());
      for (String queryParam : methodDescriptor.getQueryParams()) {
        if (queryParams.containsKey(queryParam) && queryParams.get(queryParam) != null) {
          url.set(queryParam, queryParams.get(queryParam));
        }
      }

      HttpRequest httpRequest =
          requestFactory.buildRequest(methodDescriptor.getHttpMethod(), url, jsonHttpContent);
      for (HttpJsonHeaderEnhancer enhancer : headerEnhancers) {
        enhancer.enhance(httpRequest.getHeaders());
      }
      httpRequest.setParser(new JsonObjectParser(jsonFactory));

      HttpResponse httpResponse = httpRequest.execute();

      ResponseT response =
          methodDescriptor.parseResponse(new InputStreamReader(httpResponse.getContent()));
      responseFuture.set(response);
    } catch (Exception e) {
      responseFuture.setException(e);
    }
  }

  static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<RequestT, ResponseT>()
        .setHeaderEnhancers(new LinkedList<HttpJsonHeaderEnhancer>());
  }

  static class Builder<RequestT, ResponseT> {
    private HttpJsonCallOptions callOptions;
    private RequestT request;
    private ApiMethodDescriptor<RequestT, ResponseT> methodDescriptor;
    private HttpTransport httpTransport;
    private String endpoint;
    private JsonFactory jsonFactory;
    private List<HttpJsonHeaderEnhancer> headerEnhancers;
    private SettableApiFuture responseFuture;

    private Builder() {}

    Builder<RequestT, ResponseT> setHttpJsonCallOptions(HttpJsonCallOptions callOptions) {
      this.callOptions = callOptions;
      return this;
    }

    Builder<RequestT, ResponseT> setRequest(RequestT request) {
      this.request = request;
      return this;
    }

    Builder<RequestT, ResponseT> setApiMethodDescriptor(
        ApiMethodDescriptor<RequestT, ResponseT> methodDescriptor) {
      this.methodDescriptor = methodDescriptor;
      return this;
    }

    Builder<RequestT, ResponseT> setHttpTransport(HttpTransport httpTransport) {
      this.httpTransport = httpTransport;
      return this;
    }

    Builder<RequestT, ResponseT> setEndpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    Builder<RequestT, ResponseT> setJsonFactory(JsonFactory jsonFactory) {
      this.jsonFactory = jsonFactory;
      return this;
    }

    Builder<RequestT, ResponseT> setHeaderEnhancers(List<HttpJsonHeaderEnhancer> headerEnhancers) {
      this.headerEnhancers = headerEnhancers;
      return this;
    }

    Builder<RequestT, ResponseT> setApiFuture(SettableApiFuture responseFuture) {
      this.responseFuture = responseFuture;
      return this;
    }

    HttpRequestRunnable<RequestT, ResponseT> build() {
      return new HttpRequestRunnable<RequestT, ResponseT>(
          callOptions,
          request,
          methodDescriptor,
          httpTransport,
          endpoint,
          jsonFactory,
          headerEnhancers,
          responseFuture);
    }
  }
}
