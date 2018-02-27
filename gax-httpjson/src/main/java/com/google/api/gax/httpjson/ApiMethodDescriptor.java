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

import com.google.api.client.http.HttpMethods;
import com.google.api.core.BetaApi;
import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

@BetaApi
@AutoValue
/* Method descriptor for messages to be transmitted over HTTP. */
public abstract class ApiMethodDescriptor<RequestT, ResponseT> {
  public abstract String getFullMethodName();

  public abstract Gson getBaseGson();

  public abstract Gson getRequestMarshaller();

  public abstract Gson getResponseMarshaller();

  public abstract Type getRequestType();

  public abstract Type getResponseType();

  public abstract Set<String> getPathParams();

  public abstract Set<String> getQueryParams();

  public abstract String getHttpMethod();

  public abstract HttpRequestFormatter<RequestT> getHttpRequestBuilder();

  /* In the form "[prefix]%s[suffix]", where
   *    [prefix] is any string; if length greater than 0, it should end with '/'.
   *    [suffix] is any string; if length greater than 0, it should begin with '/'.
   * This String format is applied to a serialized ResourceName to create the relative endpoint path.
   */
  public abstract String endpointPathTemplate();

  private static <RequestT, ResponseT> ApiMethodDescriptor<RequestT, ResponseT> create(
      String fullMethodName,
      RequestT requestInstance,
      ResponseT responseInstance,
      String endpointPathTemplate,
      Set<String> pathParams,
      Set<String> queryParams,
      HttpRequestFormatter<RequestT> httpRequestFormatter,
      String httpMethod) {
    final Type requestType = requestInstance.getClass();
    final Type responseType = responseInstance.getClass();
    final Gson baseGson = new GsonBuilder().create();

    TypeAdapter requestTypeAdapter =
        new TypeAdapter<RequestT>() {
          @Override
          public void write(JsonWriter out, RequestT value) throws IOException {
            baseGson.toJson(value, requestType, out);
          }

          @Override
          public RequestT read(JsonReader in) throws IOException {
            return null;
          }
        };

    TypeAdapter responseTypeAdapter =
        new TypeAdapter<ResponseT>() {
          @Override
          public void write(JsonWriter out, ResponseT value) throws IOException {
            throw new UnsupportedOperationException("Unnecessary operation.");
          }

          @Override
          public ResponseT read(JsonReader in) throws IOException {
            return baseGson.fromJson(in, responseType);
          }
        };

    Gson requestMarshaller =
        new GsonBuilder().registerTypeAdapter(requestType, requestTypeAdapter).create();
    Gson responseMarshaller =
        new GsonBuilder().registerTypeAdapter(responseType, responseTypeAdapter).create();

    return new AutoValue_ApiMethodDescriptor<RequestT, ResponseT>(
        fullMethodName,
        baseGson,
        requestMarshaller,
        responseMarshaller,
        requestType,
        responseType,
        pathParams,
        queryParams,
        httpMethod,
        httpRequestFormatter,
        endpointPathTemplate);
  }

  ResponseT parseResponse(Reader input) {
    return getResponseMarshaller().fromJson(input, getResponseType());
  }

  void writeRequest(Appendable output, RequestT request) {
    this.getRequestMarshaller().toJson(request, output);
  }

  void writeRequestBody(RequestT apiMessage, Appendable output) {
    getHttpRequestBuilder().writeRequestBody(apiMessage, getRequestMarshaller(), output);
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<RequestT, ResponseT>()
        .setPathParams(new HashSet<String>())
        .setQueryParams(new HashSet<String>())
        .setHttpMethod(HttpMethods.GET);
  }

  public static class Builder<RequestT, ResponseT> {
    String fullMethodName;
    RequestT requestInstance;
    ResponseT responseInstance;
    String endpointPathTemplate;
    Set<String> pathParams;
    Set<String> queryParams;
    HttpRequestFormatter<RequestT> httpRequestFormatter;
    String httpMethod;

    public Builder<RequestT, ResponseT> setMethodName(String fullMethodName) {
      this.fullMethodName = fullMethodName;
      return this;
    }

    public Builder<RequestT, ResponseT> setRequestInstance(RequestT requestInstance) {
      this.requestInstance = requestInstance;
      return this;
    }

    public Builder<RequestT, ResponseT> setResponseInstance(ResponseT responseInstance) {
      this.responseInstance = responseInstance;
      return this;
    }

    public Builder<RequestT, ResponseT> setEndpointPathTemplate(String endpointPathTemplate) {
      this.endpointPathTemplate = endpointPathTemplate;
      return this;
    }

    public Builder<RequestT, ResponseT> setPathParams(Set<String> pathParams) {
      this.pathParams = pathParams;
      return this;
    }

    public Builder<RequestT, ResponseT> setQueryParams(Set<String> queryParams) {
      this.queryParams = queryParams;
      return this;
    }

    public Builder<RequestT, ResponseT> setHttpRequestFormatter(
        HttpRequestFormatter<RequestT> httpRequestFormatter) {
      this.httpRequestFormatter = httpRequestFormatter;
      return this;
    }

    public Builder<RequestT, ResponseT> setHttpMethod(String httpMethod) {
      this.httpMethod = httpMethod;
      return this;
    }

    public ApiMethodDescriptor<RequestT, ResponseT> build() {
      return ApiMethodDescriptor.create(
          fullMethodName,
          requestInstance,
          responseInstance,
          endpointPathTemplate,
          pathParams,
          queryParams,
          httpRequestFormatter,
          httpMethod);
    }
  }
}
