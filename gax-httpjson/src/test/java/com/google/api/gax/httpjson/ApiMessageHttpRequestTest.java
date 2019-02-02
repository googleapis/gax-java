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
import com.google.api.core.SettableApiFuture;
import com.google.api.pathtemplate.PathTemplate;
import com.google.api.resourcenames.ResourceName;
import com.google.api.resourcenames.ResourceNameFactory;
import com.google.auth.Credentials;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.Test;
import org.threeten.bp.Instant;

public class ApiMessageHttpRequestTest {
  private static final String ENDPOINT = "https://www.googleapis.com/animals/v1/projects/";
  private static PathTemplate nameTemplate = PathTemplate.create("name/{name}");

  private static HttpJsonCallOptions fakeCallOptions =
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

  @Test
  public void testFieldMask() throws IOException {
    List<String> fieldMask = Lists.newArrayList("name", "limbs", "poisonous");
    FrogMessage frogMessage = new FrogMessage("tree_frog", 4, Lists.newArrayList("legs"), null);

    InsertFrogRequest insertFrogRequest =
        new InsertFrogRequest("name/tree_frog", "request57", frogMessage, fieldMask);

    OutputStream outputStream = insertFrog(insertFrogRequest);

    // JSON content string must contain all fields in fieldMask, even if the value is null.
    Truth.assertThat(outputStream.toString())
        .isEqualTo("{\"name\":\"tree_frog\",\"limbs\":[\"legs\"],\"poisonous\":null}");
  }

  @Test
  public void testPartialFieldMask() throws IOException {
    List<String> fieldMask = Lists.newArrayList("name", "poisonous");
    FrogMessage frogMessage = new FrogMessage("tree_frog", 4, Lists.newArrayList("legs"), null);

    InsertFrogRequest insertFrogRequest =
        new InsertFrogRequest("name/tree_frog", "request57", frogMessage, fieldMask);

    OutputStream outputStream = insertFrog(insertFrogRequest);

    // JSON content string must contain all and ONLY the fields in fieldMask, even if the value is null.
    Truth.assertThat(outputStream.toString())
        .isEqualTo("{\"name\":\"tree_frog\",\"poisonous\":null}");
  }

  @Test
  public void testNullFieldMask() throws IOException {
    List<String> nullFieldMask = null;
    FrogMessage frogMessage = new FrogMessage("tree_frog", 4, Lists.newArrayList("legs"), null);

    InsertFrogRequest insertFrogRequest =
        new InsertFrogRequest("name/tree_frog", "request57", frogMessage, nullFieldMask);

    OutputStream outputStream = insertFrog(insertFrogRequest);

    // If fieldMask is null, then don't serialize nulls.
    Truth.assertThat(outputStream.toString())
        .isEqualTo("{\"name\":\"tree_frog\",\"legs\":4,\"limbs\":[\"legs\"]}");
  }

  private OutputStream insertFrog(InsertFrogRequest insertFrogRequest) throws IOException {
    ApiMessageHttpRequestFormatter<InsertFrogRequest> frogFormatter =
        ApiMessageHttpRequestFormatter.<InsertFrogRequest>newBuilder()
            .setResourceNameField("name")
            .setPathTemplate(nameTemplate)
            .setResourceNameFactory(
                new ResourceNameFactory<ResourceName>() {
                  public ResourceName parse(final String formattedString) {
                    return new ResourceName() {
                      @Override
                      public Map<String, String> getFieldValuesMap() {
                        Map<String, String> fieldValues = new HashMap<>();
                        fieldValues.put("name", nameTemplate.parse(formattedString).get("name"));
                        return fieldValues;
                      }

                      @Override
                      public String getFieldValue(String s) {
                        return getFieldValuesMap().get(s);
                      }
                    };
                  }
                })
            .setQueryParams(Sets.newHashSet("requestId"))
            .build();

    ApiMethodDescriptor<InsertFrogRequest, Void> apiMethodDescriptor =
        ApiMethodDescriptor.<InsertFrogRequest, Void>newBuilder()
            .setFullMethodName("house.details.get")
            .setHttpMethod(null)
            .setRequestFormatter(frogFormatter)
            .build();

    HttpRequestRunnable httpRequestRunnable =
        HttpRequestRunnable.<InsertFrogRequest, Void>newBuilder()
            .setHttpJsonCallOptions(fakeCallOptions)
            .setEndpoint(ENDPOINT)
            .setRequest(insertFrogRequest)
            .setApiMethodDescriptor(apiMethodDescriptor)
            .setHttpTransport(new MockHttpTransport())
            .setJsonFactory(new JacksonFactory())
            .setResponseFuture(SettableApiFuture.<Void>create())
            .build();

    HttpRequest httpRequest = httpRequestRunnable.createHttpRequest();
    String expectedUrl = ENDPOINT + "name/tree_frog" + "?requestId=request57";
    Truth.assertThat(httpRequest.getUrl().toString()).isEqualTo(expectedUrl);

    OutputStream outputStream = new PrintableOutputStream();
    httpRequest.getContent().writeTo(outputStream);

    return outputStream;
  }

  // Example of a Request object that contains an inner request body message.
  private static class InsertFrogRequest implements ApiMessage {
    private final String name;
    private final String requestId;
    private final FrogMessage requestBody;

    private final transient List<String> fieldMask;

    InsertFrogRequest(String name, String requestId, FrogMessage frog, List<String> fieldMask) {
      this.name = name;
      this.requestId = requestId;
      this.requestBody = frog;
      this.fieldMask = fieldMask;
    }

    /* If this is a Request object, return the inner ApiMessage that represents the body
     * of the request; else return null. */
    @Nullable
    @Override
    public ApiMessage getApiMessageRequestBody() {
      return requestBody;
    }

    @Nullable
    @Override
    public Object getFieldValue(String fieldName) {
      if ("name".equals(fieldName)) {
        return name;
      }
      if ("requestId".equals(fieldName)) {
        return requestId;
      }
      if ("requestBody".equals(fieldName)) {
        return requestBody;
      }
      return null;
    }

    @Nullable
    @Override
    public List<String> getFieldMask() {
      return fieldMask;
    }
  }

  private static class FrogMessage implements ApiMessage {
    private final String name;
    private final Integer legs;
    private final List<String> limbs;
    private final Boolean poisonous;

    FrogMessage(String name, Integer legs, List<String> limbs, Boolean poisonous) {
      this.name = name;
      this.legs = legs;
      this.limbs = limbs;
      this.poisonous = poisonous;
    }

    @Nullable
    @Override
    public Object getFieldValue(String fieldName) {
      if ("name".equals(fieldName)) {
        return name;
      }
      if ("legs".equals(fieldName)) {
        return legs;
      }
      if ("limbs".equals(fieldName)) {
        return limbs;
      }
      if ("poisonous".equals(fieldName)) {
        return poisonous;
      }
      return null;
    }

    @Nullable
    @Override
    public List<String> getFieldMask() {
      return null;
    }

    /* If this is a Request object, return the inner ApiMessage that represents the body
     * of the request; else return null. */
    @Nullable
    @Override
    public ApiMessage getApiMessageRequestBody() {
      return null;
    }
  }

  public static class PrintableOutputStream extends OutputStream {
    private StringBuilder string = new StringBuilder();

    @Override
    public void write(int x) {
      this.string.append((char) x);
    }

    public String toString() {
      return this.string.toString();
    }
  }
}
