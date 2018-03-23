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

import com.google.api.core.BetaApi;
import com.google.api.pathtemplate.PathTemplate;
import com.google.api.resourcenames.ResourceNameFactory;
import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Utility class to parse ApiMessages into various HTTP request parts. */
@BetaApi
@AutoValue
public abstract class ApiMessageHttpRequestFormatter<RequestT extends ApiMessage>
    implements HttpRequestFormatter<RequestT> {

  /** The name of the field in the RequestT that contains the resource name path. */
  public abstract String getResourceNameField();

  /** A ResourceNameFactory that can parse the resource name String into a ResourceName object. */
  public abstract ResourceNameFactory getResourceNameFactory();

  public abstract Set<String> getQueryParamNames();

  /** Path template for endpoint URL path. */
  @Override
  public abstract PathTemplate getPathTemplate();

  protected abstract Gson getRequestMarshaller();

  private static <RequestT extends ApiMessage> ApiMessageHttpRequestFormatter<RequestT> create(
      final RequestT requestInstance,
      Set<String> queryParams,
      String resourceNameField,
      ResourceNameFactory resourceNameFactory,
      PathTemplate pathTemplate) {

    final Gson baseGson = new GsonBuilder().create();

    TypeAdapter requestTypeAdapter =
        new TypeAdapter<RequestT>() {
          @Override
          public void write(JsonWriter out, RequestT value) {
            baseGson.toJson(value, requestInstance.getClass(), out);
          }

          @Override
          public RequestT read(JsonReader in) {
            return null;
          }
        };

    Gson requestMarshaller =
        new GsonBuilder()
            .registerTypeAdapter(requestInstance.getClass(), requestTypeAdapter)
            .create();

    return new AutoValue_ApiMessageHttpRequestFormatter<>(
        resourceNameField, resourceNameFactory, queryParams, pathTemplate, requestMarshaller);
  }

  public static <RequestT extends ApiMessage>
      ApiMessageHttpRequestFormatter.Builder<RequestT> newBuilder() {
    return new ApiMessageHttpRequestFormatter.Builder<>();
  }

  @Override
  public Map<String, List<String>> getQueryParamNames(RequestT apiMessage) {
    Set<String> paramNames = getQueryParamNames();
    Map<String, List<String>> queryParams = new HashMap<>();
    Map<String, List<String>> nullableParams = apiMessage.populateFieldsInMap(paramNames);
    Iterator<Map.Entry<String, List<String>>> iterator = nullableParams.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, List<String>> pair = iterator.next();
      if (pair.getValue() != null && pair.getValue().size() > 0 && pair.getValue().get(0) != null) {
        queryParams.put(pair.getKey(), pair.getValue());
      }
    }
    return queryParams;
  }

  @Override
  public String getRequestBody(ApiMessage apiMessage) {
    ApiMessage body = apiMessage.getApiMessageRequestBody();
    if (body != null) {
      return getRequestMarshaller().toJson(body);
    }
    return null;
  }

  @Override
  public String getPath(RequestT apiMessage) {
    Map<String, String> pathParams = getPathParams(apiMessage);
    return getPathTemplate().instantiate(pathParams);
  }

  private Map<String, String> getPathParams(RequestT apiMessage) {
    String resourceNamePath = apiMessage.getFieldStringValue(getResourceNameField());
    if (resourceNamePath == null) {
      throw new IllegalArgumentException(
          String.format(
              "Resource name field %s is null in message object.", getResourceNameField()));
    }
    return getResourceNameFactory().parse(resourceNamePath).getFieldValuesMap();
  }

  public static class Builder<RequestT extends ApiMessage> {
    private RequestT requestInstance;
    private String resourceNameField;
    private ResourceNameFactory resourceNameFactory;
    private Set<String> queryParams;
    private PathTemplate pathTemplate;

    private Builder() {}

    public Builder<RequestT> setRequestInstance(RequestT requestInstance) {
      this.requestInstance = requestInstance;
      return this;
    }

    public Builder<RequestT> setResourceNameField(String resourceNameField) {
      this.resourceNameField = resourceNameField;
      return this;
    }

    public Builder<RequestT> setResourceNameFactory(ResourceNameFactory resourceNameFactory) {
      this.resourceNameFactory = resourceNameFactory;
      return this;
    }

    public Builder<RequestT> setPathTemplate(PathTemplate pathTemplate) {
      this.pathTemplate = pathTemplate;
      return this;
    }

    public Builder<RequestT> setQueryParams(Set<String> queryParams) {
      this.queryParams = queryParams;
      return this;
    }

    public ApiMessageHttpRequestFormatter<RequestT> build() {
      return ApiMessageHttpRequestFormatter.create(
          requestInstance, queryParams, resourceNameField, resourceNameFactory, pathTemplate);
    }
  }
}
