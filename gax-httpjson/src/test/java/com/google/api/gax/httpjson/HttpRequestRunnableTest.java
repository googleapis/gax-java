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
import com.google.api.gax.httpjson.testing.FakeApiMessage;
import com.google.api.pathtemplate.PathTemplate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class HttpRequestRunnableTest {
  private static CatMessage catMessage;
  private static final String ENDPOINT = "https://www.googleapis.com/animals/v1/projects/";
  private static HttpRequestFormatter<CatMessage> catFormatter;
  private static HttpResponseParser<EmptyMessage> catParser;
  private static final PathTemplate nameTemplate = PathTemplate.create("name/{name}");
  private static final Set<String> queryParams =
      Sets.newTreeSet(Lists.newArrayList("food", "size", "gibberish"));

  @SuppressWarnings("unchecked")
  @BeforeClass
  public static void setUp() {
    catMessage =
        new CatMessage(
            ImmutableMap.of(
                "name", "feline",
                "size", Arrays.asList("small"),
                "food", Arrays.asList("bird", "mouse")),
            null,
            null);

    catFormatter =
        new HttpRequestFormatter<CatMessage>() {
          private PathTemplate namePattern = PathTemplate.create("name/{name}");

          @Override
          public Map<String, List<String>> getQueryParamNames(CatMessage apiMessage) {
            Map<String, List<String>> values = new TreeMap<>();
            for (String queryParam : queryParams) {
              Object fieldValue = apiMessage.getFieldValue(queryParam);
              if (fieldValue == null) {
                continue;
              }
              if (fieldValue instanceof List) {
                values.put(queryParam, (List<String>) fieldValue);
              } else {
                values.put(queryParam, Lists.newArrayList(fieldValue.toString()));
              }
            }
            return values;
          }

          @Override
          public String getRequestBody(CatMessage apiMessage) {
            return null;
          }

          @Override
          public String getPath(CatMessage apiMessage) {
            String name = apiMessage.getFieldValue("name").toString();
            return nameTemplate.instantiate("name", name);
          }

          @Override
          public PathTemplate getPathTemplate() {
            return namePattern;
          }
        };

    catParser = Mockito.mock(HttpResponseParser.class);
  }

  @Test
  public void testRequestUrl() throws IOException {
    ApiMethodDescriptor<CatMessage, EmptyMessage> methodDescriptor =
        ApiMethodDescriptor.<CatMessage, EmptyMessage>newBuilder()
            .setFullMethodName("house.cat.get")
            .setHttpMethod(null)
            .setRequestFormatter(catFormatter)
            .setResponseParser(catParser)
            .build();

    HttpRequestRunnable<CatMessage, EmptyMessage> httpRequestRunnable =
        new HttpRequestRunnable<>(
            catMessage,
            methodDescriptor,
            ENDPOINT,
            HttpJsonCallOptions.newBuilder().build(),
            new MockHttpTransport(),
            HttpJsonMetadata.newBuilder().build(),
            (result) -> {});

    HttpRequest httpRequest = httpRequestRunnable.createHttpRequest();
    Truth.assertThat(httpRequest.getContent()).isInstanceOf(EmptyContent.class);
    String expectedUrl = ENDPOINT + "name/feline" + "?food=bird&food=mouse&size=small";
    Truth.assertThat(httpRequest.getUrl().toString()).isEqualTo(expectedUrl);
  }

  @Test
  public void testRequestUrlUnnormalized() throws IOException {
    ApiMethodDescriptor<CatMessage, EmptyMessage> methodDescriptor =
        ApiMethodDescriptor.<CatMessage, EmptyMessage>newBuilder()
            .setFullMethodName("house.cat.get")
            .setHttpMethod("PUT")
            .setRequestFormatter(catFormatter)
            .setResponseParser(catParser)
            .build();

    HttpRequestRunnable<CatMessage, EmptyMessage> httpRequestRunnable =
        new HttpRequestRunnable<>(
            catMessage,
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
    ApiMethodDescriptor<CatMessage, EmptyMessage> methodDescriptor =
        ApiMethodDescriptor.<CatMessage, EmptyMessage>newBuilder()
            .setFullMethodName("house.cat.get")
            .setHttpMethod("PATCH")
            .setRequestFormatter(catFormatter)
            .setResponseParser(catParser)
            .build();

    HttpRequestRunnable<CatMessage, EmptyMessage> httpRequestRunnable =
        new HttpRequestRunnable<>(
            catMessage,
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

  // TODO(andrealin): test request body

  private static class CatMessage extends FakeApiMessage {
    CatMessage(Map<String, Object> fieldValues, ApiMessage messageBody, List<String> fieldMask) {
      super(fieldValues, messageBody, fieldMask);
    }
  }
}
