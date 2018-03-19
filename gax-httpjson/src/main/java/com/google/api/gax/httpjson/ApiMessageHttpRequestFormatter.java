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
public class ApiMessageHttpRequestFormatter<T extends ApiMessage>
    implements HttpRequestFormatter<T> {

  private final ApiMethodDescriptor<T, ?> methodDescriptor;
  private final Gson requestMarshaller;

  /* Constructs an ApiMessageHttpRequestFormatter from an API method descriptor. */
  public ApiMessageHttpRequestFormatter(final ApiMethodDescriptor<T, ?> methodDescriptor) {
    this.methodDescriptor = methodDescriptor;

    final Gson baseGson = new GsonBuilder().create();

    TypeAdapter requestTypeAdapter =
        new TypeAdapter<T>() {
          @Override
          public void write(JsonWriter out, T value) {
            baseGson.toJson(value, methodDescriptor.getRequestType(), out);
          }

          @Override
          public T read(JsonReader in) {
            return null;
          }
        };

    this.requestMarshaller =
        new GsonBuilder()
            .registerTypeAdapter(methodDescriptor.getRequestType(), requestTypeAdapter)
            .create();
  }

  @Override
  public Map<String, List<String>> getQueryParams(T apiMessage) {
    Set<String> paramNames = methodDescriptor.getQueryParams();
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
  public void writeRequestBody(ApiMessage apiMessage, Appendable writer) {
    ApiMessage body = apiMessage.getApiMessageRequestBody();
    if (body != null) {
      requestMarshaller.toJson(body, writer);
    }
  }

  @Override
  public String getPath(T apiMessage) {
    Map<String, String> pathParams = getPathParams(apiMessage);
    PathTemplate pathPattern = PathTemplate.create(methodDescriptor.endpointPathTemplate());
    return pathPattern.instantiate(pathParams);
  }

  @Override
  public String getHttpMethod() {
    return methodDescriptor.getHttpMethod();
  }

  private Map<String, String> getPathParams(T apiMessage) {
    String resourceNameField = methodDescriptor.getResourceNameField();
    String resourceNamePath = apiMessage.getFieldStringValue(resourceNameField);
    if (resourceNamePath == null) {
      throw new IllegalArgumentException(
          String.format("Resource name field %s is null in message object.", resourceNameField));
    }
    return methodDescriptor.getResourceNameFactory().parse(resourceNamePath).getFieldValuesMap();
  }
}
