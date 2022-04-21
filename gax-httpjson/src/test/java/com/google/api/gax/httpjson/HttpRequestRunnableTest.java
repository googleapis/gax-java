/*
 * Copyright 2018 Google LLC
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
import com.google.api.client.http.HttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.common.truth.Truth;
import com.google.longrunning.ListOperationsRequest;
import com.google.protobuf.Empty;
import com.google.protobuf.Field;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class HttpRequestRunnableTest {
  private static Field requestMessage;
  private static final String ENDPOINT = "https://www.googleapis.com/animals/v1/projects";
  private static HttpRequestFormatter<Field> requestFormatter;
  private static HttpResponseParser<Empty> responseParser;

  @SuppressWarnings("unchecked")
  @BeforeClass
  public static void setUp() {
    requestMessage =
        Field.newBuilder()
            .setName("feline")
            .setNumber(2)
            .setDefaultValue("bird")
            .setJsonName("mouse")
            .setTypeUrl("small")
            .build();

    requestFormatter =
        ProtoMessageRequestFormatter.<Field>newBuilder()
            .setPath(
                "/name/{name=*}",
                request -> {
                  Map<String, String> fields = new HashMap<>();
                  ProtoRestSerializer<ListOperationsRequest> serializer =
                      ProtoRestSerializer.create();
                  serializer.putPathParam(fields, "name", request.getName());
                  return fields;
                })
            .setQueryParamsExtractor(
                request -> {
                  Map<String, List<String>> fields = new LinkedHashMap<>();
                  ProtoRestSerializer<ListOperationsRequest> serializer =
                      ProtoRestSerializer.create();

                  serializer.putQueryParam(
                      fields,
                      "food",
                      Arrays.asList(request.getDefaultValue(), request.getJsonName()));
                  serializer.putQueryParam(fields, "size", request.getTypeUrl());
                  return fields;
                })
            .setRequestBodyExtractor(request -> null)
            .build();

    responseParser = Mockito.mock(HttpResponseParser.class);
  }

  @Test
  public void testRequestUrl() throws IOException {
    ApiMethodDescriptor<Field, Empty> methodDescriptor =
        ApiMethodDescriptor.<Field, Empty>newBuilder()
            .setFullMethodName("house.cat.get")
            .setHttpMethod(null)
            .setRequestFormatter(requestFormatter)
            .setResponseParser(responseParser)
            .build();

    HttpRequestRunnable<Field, Empty> httpRequestRunnable =
        new HttpRequestRunnable<>(
            requestMessage,
            methodDescriptor,
            ENDPOINT,
            HttpJsonCallOptions.newBuilder().build(),
            new MockHttpTransport(),
            HttpJsonMetadata.newBuilder().build(),
            (result) -> {});

    HttpRequest httpRequest = httpRequestRunnable.createHttpRequest();
    Truth.assertThat(httpRequest.getContent()).isInstanceOf(EmptyContent.class);
    String expectedUrl = ENDPOINT + "/name/feline" + "?food=bird&food=mouse&size=small";
    Truth.assertThat(httpRequest.getUrl().toString()).isEqualTo(expectedUrl);
  }

  @Test
  public void testRequestUrlUnnormalized() throws IOException {
    ApiMethodDescriptor<Field, Empty> methodDescriptor =
        ApiMethodDescriptor.<Field, Empty>newBuilder()
            .setFullMethodName("house.cat.get")
            .setHttpMethod("PUT")
            .setRequestFormatter(requestFormatter)
            .setResponseParser(responseParser)
            .build();

    HttpRequestRunnable<Field, Empty> httpRequestRunnable =
        new HttpRequestRunnable<>(
            requestMessage,
            methodDescriptor,
            "www.googleapis.com/animals/v1/projects",
            HttpJsonCallOptions.newBuilder().build(),
            new MockHttpTransport(),
            HttpJsonMetadata.newBuilder().build(),
            (result) -> {});

    HttpRequest httpRequest = httpRequestRunnable.createHttpRequest();
    Truth.assertThat(httpRequest.getContent()).isInstanceOf(EmptyContent.class);
    String expectedUrl =
        "https://www.googleapis.com/animals/v1/projects/name/feline?food=bird&food=mouse&size=small";
    Truth.assertThat(httpRequest.getUrl().toString()).isEqualTo(expectedUrl);
    Truth.assertThat(httpRequest.getRequestMethod()).isEqualTo("PUT");
    Truth.assertThat(httpRequest.getHeaders().get("X-HTTP-Method-Override")).isNull();
  }

  @Test
  public void testRequestUrlUnnormalizedPatch() throws IOException {
    ApiMethodDescriptor<Field, Empty> methodDescriptor =
        ApiMethodDescriptor.<Field, Empty>newBuilder()
            .setFullMethodName("house.cat.get")
            .setHttpMethod("PATCH")
            .setRequestFormatter(requestFormatter)
            .setResponseParser(responseParser)
            .build();

    HttpRequestRunnable<Field, Empty> httpRequestRunnable =
        new HttpRequestRunnable<>(
            requestMessage,
            methodDescriptor,
            "www.googleapis.com/animals/v1/projects",
            HttpJsonCallOptions.newBuilder().build(),
            new MockHttpTransport(),
            HttpJsonMetadata.newBuilder().build(),
            (result) -> {});

    HttpRequest httpRequest = httpRequestRunnable.createHttpRequest();
    Truth.assertThat(httpRequest.getContent()).isInstanceOf(EmptyContent.class);
    String expectedUrl =
        "https://www.googleapis.com/animals/v1/projects/name/feline?food=bird&food=mouse&size=small";
    Truth.assertThat(httpRequest.getUrl().toString()).isEqualTo(expectedUrl);
    Truth.assertThat(httpRequest.getRequestMethod()).isEqualTo("POST");
    Truth.assertThat(httpRequest.getHeaders().get("X-HTTP-Method-Override")).isEqualTo("PATCH");
  }
}
