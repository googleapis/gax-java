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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.HttpResponseException;
import com.google.api.gax.httpjson.testing.MockHttpService;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Cardinality;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class HttpJsonDirectCallableTest {

  private static final ApiMethodDescriptor<Field, Field> FAKE_METHOD_DESCRIPTOR =
      ApiMethodDescriptor.<Field, Field>newBuilder()
          .setFullMethodName("google.cloud.v1.Fake/FakeMethod")
          .setHttpMethod("POST")
          .setRequestFormatter(
              ProtoMessageRequestFormatter.<Field>newBuilder()
                  .setPath(
                      "/fake/v1/name/{name}",
                      request -> {
                        Map<String, String> fields = new HashMap<>();
                        ProtoRestSerializer<Field> serializer = ProtoRestSerializer.create();
                        serializer.putPathParam(fields, "name", request.getName());
                        return fields;
                      })
                  .setQueryParamsExtractor(
                      request -> {
                        Map<String, List<String>> fields = new HashMap<>();
                        ProtoRestSerializer<Field> serializer = ProtoRestSerializer.create();
                        serializer.putQueryParam(fields, "number", request.getNumber());
                        return fields;
                      })
                  .setRequestBodyExtractor(
                      request ->
                          ProtoRestSerializer.create()
                              .toBody("*", request.toBuilder().clearName().build()))
                  .build())
          .setResponseParser(
              ProtoMessageResponseParser.<Field>newBuilder()
                  .setDefaultInstance(Field.getDefaultInstance())
                  .build())
          .build();

  private static final MockHttpService MOCK_SERVICE =
      new MockHttpService(Collections.singletonList(FAKE_METHOD_DESCRIPTOR), "google.com:443");

  private final ManagedHttpJsonChannel channel =
      new ManagedHttpJsonInterceptorChannel(
          ManagedHttpJsonChannel.newBuilder()
              .setEndpoint("google.com:443")
              .setExecutor(executorService)
              .setHttpTransport(MOCK_SERVICE)
              .build(),
          new HttpJsonHeaderInterceptor(Collections.singletonMap("header-key", "headerValue")));

  private static ExecutorService executorService;

  @BeforeClass
  public static void initialize() {
    executorService =
        Executors.newFixedThreadPool(
            2,
            r -> {
              Thread t = Executors.defaultThreadFactory().newThread(r);
              t.setDaemon(true);
              return t;
            });
  }

  @AfterClass
  public static void destroy() {
    executorService.shutdownNow();
  }

  @After
  public void tearDown() {
    MOCK_SERVICE.reset();
  }

  @Test
  public void testSuccessfulUnaryResponse() throws ExecutionException, InterruptedException {
    HttpJsonDirectCallable<Field, Field> callable =
        new HttpJsonDirectCallable<>(FAKE_METHOD_DESCRIPTOR);

    HttpJsonCallContext callContext =
        HttpJsonCallContext.createDefault()
            .withChannel(channel)
            .withTimeout(Duration.ofSeconds(30));

    Field request;
    Field expectedResponse;
    request = expectedResponse = createTestMessage();

    MOCK_SERVICE.addResponse(expectedResponse);

    Field actualResponse = callable.futureCall(request, callContext).get();

    assertThat(actualResponse).isEqualTo(expectedResponse);
    assertThat(MOCK_SERVICE.getRequestPaths().size()).isEqualTo(1);
    String headerValue = MOCK_SERVICE.getRequestHeaders().get("header-key").iterator().next();
    assertThat(headerValue).isEqualTo("headerValue");
  }

  @Test
  public void testErrorUnaryResponse() throws InterruptedException {
    HttpJsonDirectCallable<Field, Field> callable =
        new HttpJsonDirectCallable<>(FAKE_METHOD_DESCRIPTOR);

    HttpJsonCallContext callContext = HttpJsonCallContext.createDefault().withChannel(channel);

    ApiException exception =
        ApiExceptionFactory.createException(
            new Exception(), FakeStatusCode.of(Code.NOT_FOUND), false);
    MOCK_SERVICE.addException(exception);

    try {
      callable.futureCall(createTestMessage(), callContext).get();
      Assert.fail("No exception raised");
    } catch (ExecutionException e) {
      HttpResponseException respExp = (HttpResponseException) e.getCause();
      assertThat(respExp.getStatusCode()).isEqualTo(400);
      assertThat(respExp.getContent()).isEqualTo(exception.toString());
    }
  }

  @Test
  public void testErrorNullContentSuccessfulResponse() throws InterruptedException {
    HttpJsonDirectCallable<Field, Field> callable =
        new HttpJsonDirectCallable<>(FAKE_METHOD_DESCRIPTOR);

    HttpJsonCallContext callContext = HttpJsonCallContext.createDefault().withChannel(channel);

    MOCK_SERVICE.addNullResponse();

    try {
      callable.futureCall(createTestMessage(), callContext).get();
      Assert.fail("No exception raised");
    } catch (ExecutionException e) {
      HttpJsonStatusRuntimeException respExp = (HttpJsonStatusRuntimeException) e.getCause();
      assertThat(respExp.getStatusCode()).isEqualTo(200);
      assertThat(respExp.getCause().getMessage())
          .isEqualTo("Both response message and response exception were null");
    }
  }

  @Test
  public void testErrorNullContentFailedResponse() throws InterruptedException {
    HttpJsonDirectCallable<Field, Field> callable =
        new HttpJsonDirectCallable<>(FAKE_METHOD_DESCRIPTOR);

    HttpJsonCallContext callContext = HttpJsonCallContext.createDefault().withChannel(channel);
    MOCK_SERVICE.addNullResponse(400);

    try {
      callable.futureCall(createTestMessage(), callContext).get();
      Assert.fail("No exception raised");
    } catch (ExecutionException e) {
      HttpResponseException respExp = (HttpResponseException) e.getCause();
      assertThat(respExp.getStatusCode()).isEqualTo(400);
      assertThat(respExp.getContent()).isNull();
    }
  }

  private Field createTestMessage() {
    return Field.newBuilder() // "echo" service
        .setName("imTheBestField")
        .setNumber(2)
        .setCardinality(Cardinality.CARDINALITY_OPTIONAL)
        .setDefaultValue("blah")
        .build();
  }
}
