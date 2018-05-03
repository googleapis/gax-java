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

import com.google.api.client.http.HttpRequest;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.gax.httpjson.testing.FakeApiMessage;
import com.google.api.pathtemplate.PathTemplate;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.threeten.bp.Instant;

public class HttpRequestRunnableTest {
  private static HttpJsonCallOptions fakeCallOptions;
  private static CatMessage catMessage;
  private static final String ENDPOINT = "https://www.googleapis.com/animals/v1/projects/";
  private static HttpRequestRunnable httpRequestRunnable;
  private static HttpRequestFormatter<CatMessage> catFormatter;
  private static HttpResponseParser<Void> catParser;
  private static ApiMethodDescriptor<CatMessage, Void> methodDescriptor;

  @BeforeClass
  public static void setUp() {
    fakeCallOptions =
        new HttpJsonCallOptions() {
          @Override
          public Instant getDeadline() {
            return null;
          }

          @Override
          public Credentials getCredentials() {
            return null;
          }
        };

    catMessage =
        CatMessage.create(
            ImmutableMap.of(
                "name", Arrays.asList("feline"),
                "size", Arrays.asList("small"),
                "food", Arrays.asList("bird", "mouse")),
            null);

    catFormatter =
        new HttpRequestFormatter<CatMessage>() {
          private PathTemplate namePattern = PathTemplate.create("name/{name}");

          @Override
          public Map<String, List<String>> getQueryParamNames(CatMessage apiMessage) {
            Map<String, List<String>> queryParams = new TreeMap<>();
            queryParams.put("food", apiMessage.getFieldValues().get("food"));
            queryParams.put("size", apiMessage.getFieldValues().get("size"));
            queryParams.put("gibberish", apiMessage.getFieldValues().get("gibberish"));
            return queryParams;
          }

          @Override
          public String getRequestBody(CatMessage apiMessage) {
            return null;
          }

          @Override
          public String getPath(CatMessage apiMessage) {
            String name = ((List<String>) apiMessage.getFieldValue("name")).get(0);
            return namePattern.instantiate("name", name);
          }

          @Override
          public PathTemplate getPathTemplate() {
            return namePattern;
          }
        };

    catParser =
        new HttpResponseParser<Void>() {
          @Override
          public Void parse(InputStream httpContent) {
            return null;
          }

          @Override
          public String serialize(Void response) {
            return null;
          }
        };

    methodDescriptor =
        ApiMethodDescriptor.<CatMessage, Void>newBuilder()
            .setFullMethodName("house.cat.get")
            .setHttpMethod(null)
            .setRequestFormatter(catFormatter)
            .setResponseParser(catParser)
            .build();

    httpRequestRunnable =
        HttpRequestRunnable.<CatMessage, Void>newBuilder()
            .setHttpJsonCallOptions(fakeCallOptions)
            .setEndpoint(ENDPOINT)
            .setRequest(catMessage)
            .setApiMethodDescriptor(methodDescriptor)
            .setHttpTransport(new MockHttpTransport())
            .setJsonFactory(new JacksonFactory())
            .build();
  }

  @Test
  public void testRequestUrl() throws IOException {
    HttpRequest httpRequest = httpRequestRunnable.createHttpRequest();
    String expectedUrl = ENDPOINT + "name/feline" + "?food=bird&food=mouse&size=small";
    Truth.assertThat(httpRequest.getUrl().toString()).isEqualTo(expectedUrl);
  }

  // TODO(andrealin): test request body

  private static class CatMessage extends FakeApiMessage {
    Map<String, List<String>> fieldValues;

    private CatMessage(
        Map<String, Object> fieldValues, ApiMessage messageBody, List<String> fieldMask) {
      super(fieldValues, messageBody, fieldMask);
      ImmutableMap.Builder<String, List<String>> mapBuilder = ImmutableMap.builder();
      for (Map.Entry<String, Object> field : fieldValues.entrySet()) {
        mapBuilder.put(field.getKey(), (List<String>) field.getValue());
      }
      this.fieldValues = mapBuilder.build();
    }

    public static CatMessage create(Map<String, List<String>> fieldValues, List<String> fieldMask) {
      Map<String, Object> objectVals = new HashMap<>();
      for (Map.Entry<String, List<String>> field : fieldValues.entrySet()) {
        objectVals.put(field.getKey(), field.getValue());
      }
      return new CatMessage(objectVals, null, fieldMask);
    }

    Map<String, List<String>> getFieldValues() {
      return fieldValues;
    }
  }
}
