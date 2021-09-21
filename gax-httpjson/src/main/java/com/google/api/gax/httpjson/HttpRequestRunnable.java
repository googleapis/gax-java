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

import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.util.GenericData;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/** A runnable object that creates and executes an HTTP request. */
@AutoValue
abstract class HttpRequestRunnable<RequestT, ResponseT> implements Runnable {
  abstract HttpJsonCallOptions getHttpJsonCallOptions();

  abstract RequestT getRequest();

  abstract ApiMethodDescriptor<RequestT, ResponseT> getApiMethodDescriptor();

  abstract HttpTransport getHttpTransport();

  abstract String getEndpoint();

  abstract JsonFactory getJsonFactory();

  abstract ImmutableList<HttpJsonHeaderEnhancer> getHeaderEnhancers();

  abstract SettableApiFuture<ResponseT> getResponseFuture();

  HttpRequest createHttpRequest() throws IOException {
    GenericData tokenRequest = new GenericData();

    HttpRequestFormatter<RequestT> requestFormatter =
        getApiMethodDescriptor().getRequestFormatter();

    HttpRequestFactory requestFactory;
    Credentials credentials = getHttpJsonCallOptions().getCredentials();
    if (credentials != null) {
      requestFactory =
          getHttpTransport().createRequestFactory(new HttpCredentialsAdapter(credentials));
    } else {
      requestFactory = getHttpTransport().createRequestFactory();
    }

    // Create HTTP request body.
    String requestBody = requestFormatter.getRequestBody(getRequest());
    HttpContent jsonHttpContent;
    if (!Strings.isNullOrEmpty(requestBody)) {
      getJsonFactory().createJsonParser(requestBody).parse(tokenRequest);
      jsonHttpContent =
          new JsonHttpContent(getJsonFactory(), tokenRequest)
              .setMediaType((new HttpMediaType("application/json")));
    } else {
      // Force underlying HTTP lib to set Content-Length header to avoid 411s.
      // See EmptyContent.java.
      jsonHttpContent = new EmptyContent();
    }

    // Populate URL path and query parameters.
    String endpoint = normalizeEndpoint(getEndpoint());
    GenericUrl url = new GenericUrl(endpoint + requestFormatter.getPath(getRequest()));
    Map<String, List<String>> queryParams = requestFormatter.getQueryParamNames(getRequest());
    for (Entry<String, List<String>> queryParam : queryParams.entrySet()) {
      if (queryParam.getValue() != null) {
        url.set(queryParam.getKey(), queryParam.getValue());
      }
    }

    HttpRequest httpRequest = buildRequest(requestFactory, url, jsonHttpContent);

    Instant deadline = getHttpJsonCallOptions().getDeadline();
    if (deadline != null) {
      long readTimeout = Duration.between(Instant.now(), deadline).toMillis();
      if (httpRequest.getReadTimeout() > 0
          && httpRequest.getReadTimeout() < readTimeout
          && readTimeout < Integer.MAX_VALUE) {
        httpRequest.setReadTimeout((int) readTimeout);
      }
    }

    for (HttpJsonHeaderEnhancer enhancer : getHeaderEnhancers()) {
      enhancer.enhance(httpRequest.getHeaders());
    }
    httpRequest.setParser(new JsonObjectParser(getJsonFactory()));
    return httpRequest;
  }

  private HttpRequest buildRequest(
      HttpRequestFactory requestFactory, GenericUrl url, HttpContent jsonHttpContent)
      throws IOException {
    // A workaround to support PATCH request. This assumes support of "X-HTTP-Method-Override"
    // header on the server side, which GCP services usually do.
    //
    // Long story short, the problems is as follows: gax-httpjson depends on NetHttpTransport class
    // from google-http-client, which depends on JDK standard java.net.HttpUrlConnection, which does
    // not support PATCH http method.
    //
    // It is a won't fix for JDK8: https://bugs.openjdk.java.net/browse/JDK-8207840.
    // A corresponding google-http-client issue:
    // https://github.com/googleapis/google-http-java-client/issues/167
    //
    // In JDK11 there is java.net.http.HttpRequest with PATCH method support but, gax-httpjson must
    // remain compatible with Java 8.
    //
    // Using "X-HTTP-Method-Override" header is probably the cleanest way to fix it. Other options
    // would be: hideous reflection hacks (not a safe option in a generic library, which
    // gax-httpjson is), writing own implementation of HttpUrlConnection (fragile and a lot of
    // work), depending on v2.ApacheHttpTransport (it has many extra dependencies, does not support
    // mtls etc).
    String actualHttpMethod = getApiMethodDescriptor().getHttpMethod();
    String originalHttpMethod = actualHttpMethod;
    if (HttpMethods.PATCH.equals(actualHttpMethod)) {
      actualHttpMethod = HttpMethods.POST;
    }
    HttpRequest httpRequest = requestFactory.buildRequest(actualHttpMethod, url, jsonHttpContent);
    if (originalHttpMethod != null && !originalHttpMethod.equals(actualHttpMethod)) {
      HttpHeadersUtils.setHeader(
          httpRequest.getHeaders(), "X-HTTP-Method-Override", originalHttpMethod);
    }
    return httpRequest;
  }

  // This will be frequently executed, so avoiding using regexps if not necessary.
  private String normalizeEndpoint(String endpoint) {
    String normalized = endpoint;
    // Set protocol as https by default if not set explicitly
    if (!normalized.contains("://")) {
      normalized = "https://" + normalized;
    }

    if (normalized.charAt(normalized.length() - 1) != '/') {
      normalized += '/';
    }

    return normalized;
  }

  @Override
  public void run() {
    try {
      HttpRequest httpRequest = createHttpRequest();
      HttpResponse httpResponse = httpRequest.execute();
      if (!httpResponse.isSuccessStatusCode()) {
        ApiExceptionFactory.createException(
            null,
            HttpJsonStatusCode.of(httpResponse.getStatusCode(), httpResponse.getStatusMessage()),
            false);
      }

      if (getApiMethodDescriptor().getResponseParser() != null) {
        ResponseT response =
            getApiMethodDescriptor()
                .getResponseParser()
                .parse(httpResponse.getContent(), getHttpJsonCallOptions().getTypeRegistry());

        getResponseFuture().set(response);
      } else {
        getResponseFuture().set(null);
      }
    } catch (Exception e) {
      getResponseFuture().setException(e);
    }
  }

  static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new AutoValue_HttpRequestRunnable.Builder<RequestT, ResponseT>()
        .setHeaderEnhancers(new LinkedList<HttpJsonHeaderEnhancer>());
  }

  @AutoValue.Builder
  abstract static class Builder<RequestT, ResponseT> {
    abstract Builder<RequestT, ResponseT> setHttpJsonCallOptions(HttpJsonCallOptions callOptions);

    abstract Builder<RequestT, ResponseT> setRequest(RequestT request);

    abstract Builder<RequestT, ResponseT> setApiMethodDescriptor(
        ApiMethodDescriptor<RequestT, ResponseT> methodDescriptor);

    abstract Builder<RequestT, ResponseT> setHttpTransport(HttpTransport httpTransport);

    abstract Builder<RequestT, ResponseT> setEndpoint(String endpoint);

    abstract Builder<RequestT, ResponseT> setJsonFactory(JsonFactory jsonFactory);

    abstract Builder<RequestT, ResponseT> setHeaderEnhancers(
        List<HttpJsonHeaderEnhancer> headerEnhancers);

    abstract Builder<RequestT, ResponseT> setResponseFuture(
        SettableApiFuture<ResponseT> responseFuture);

    abstract HttpRequestRunnable<RequestT, ResponseT> build();
  }
}
