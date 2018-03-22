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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.gax.httpjson.testing.FakeApiMessage;
import com.google.api.gax.httpjson.testing.MockHttpService;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class MockHttpServiceTest {

  private static PetMessage gerbilMessage =
      new PetMessage(
          ImmutableMap.<String, List<String>>of("type", Lists.newArrayList("rodent")), null);
  private static PetMessage ospreyMessage =
      new PetMessage(
          ImmutableMap.<String, List<String>>of("type", Lists.newArrayList("raptor")), null);
  private static HumanMessage humanMessage =
      new HumanMessage(
          ImmutableMap.<String, List<String>>of("type", Lists.newArrayList("toddler")), null);

  private static final String RESPONSE_EXCEPTION_STRING = "[Expected exception]";
  private static final ApiException PARSE_EXCEPTION =
      new ApiException(
          "Unknown object type.", null, HttpJsonStatusCode.of(Code.INVALID_ARGUMENT), false);

  private static class PetMessage extends FakeApiMessage {
    public PetMessage(Map<String, List<String>> fieldValues, ApiMessage requestBodyMessage) {
      super(fieldValues, requestBodyMessage);
    }
  }

  private static class HumanMessage extends FakeApiMessage {
    public HumanMessage(Map<String, List<String>> fieldValues, ApiMessage requestBodyMessage) {
      super(fieldValues, requestBodyMessage);
    }
  }

  private static final HttpResponseParser<PetMessage> PET_MESSAGE_FORMATTER =
      new HttpResponseParser<PetMessage>() {
        @Override
        public PetMessage parse(InputStream httpContent) {
          return null;
        }

        @Override
        public String writeResponse(Object response) {
          if (!(response instanceof PetMessage)) {
            throw PARSE_EXCEPTION;
          }
          return ((PetMessage) response).getFieldStringValue("type");
        }
      };

  private static final String BASE_ENDPOINT = "http://google.com/";

  private static final ImmutableMap<String, Map<String, HttpResponseParser<?>>>
      SERVER_METHOD_DESCRIPTORS =
          new ImmutableMap.Builder<String, Map<String, HttpResponseParser<?>>>()
              .put(
                  "pet/{name}",
                  ImmutableMap.<String, HttpResponseParser<?>>of("GET", PET_MESSAGE_FORMATTER))
              .build();
  private static MockHttpService testService =
      new MockHttpService(SERVER_METHOD_DESCRIPTORS, BASE_ENDPOINT);

  private static final HttpRequestFactory HTTP_REQUEST_FACTORY = testService.createRequestFactory();

  @Before
  public void cleanUp() {
    testService.reset();
  }

  @Test
  public void testMessageResponse() throws IOException {
    // Queue up return objects.
    testService.addResponse(gerbilMessage);
    testService.addResponse(ospreyMessage);

    // First HTTP call returns gerbil.
    HttpResponse httpResponse =
        HTTP_REQUEST_FACTORY
            .buildGetRequest(new GenericUrl("http://google.com/pet/rodent"))
            .execute();
    assertEquals("rodent", getHttpResponseString(httpResponse));

    // Second HTTP call returns raptor.
    httpResponse =
        HTTP_REQUEST_FACTORY
            .buildGetRequest(
                new GenericUrl("http://google.com/pet/raptor?species=birb&name=G%C3%BCnter"))
            .execute();
    assertEquals("raptor", getHttpResponseString(httpResponse));
  }

  @Test
  public void testNullResponse() throws IOException {
    testService.addNullResponse();
    HttpResponse httpResponse =
        HTTP_REQUEST_FACTORY
            .buildGetRequest(new GenericUrl("http://google.com/pet/raptor?species=birb"))
            .execute();
    assertNull(httpResponse.getContent());
  }

  @Test
  public void testBadFormatter() throws IOException {
    testService.addResponse(humanMessage);

    // Human message type is not parsable by PET_MESSAGE_FORMATTER and should fail.
    try {
      HTTP_REQUEST_FACTORY
          .buildGetRequest(new GenericUrl("http://google.com/pet/raptor?species=birb"))
          .execute();
      fail();
    } catch (ApiException e) {
      // Expected parsing exception.
    }
  }

  @Test
  public void testUnknownMethodPath() throws IOException {
    testService.addResponse(gerbilMessage);

    try {
      // This url does not match any path template in SERVER_METHOD_DESCRIPTORS.
      GenericUrl url = new GenericUrl("http://google.com/car/");
      HttpResponse httpResponse = HTTP_REQUEST_FACTORY.buildGetRequest(url).execute();
      fail();
    } catch (HttpResponseException e) {
      // Expected parsing exception.
      assertFalse(e.isSuccessStatusCode());
    }
  }

  @Test
  public void testReturnException() throws IOException {
    testService.addException(new Exception(RESPONSE_EXCEPTION_STRING));

    // Fifth HTTP call throws exception.
    try {
      HTTP_REQUEST_FACTORY
          .buildGetRequest(new GenericUrl("http://google.com/pet/rodent"))
          .execute();
      fail();
    } catch (HttpResponseException e) {
      assertFalse(e.isSuccessStatusCode());
      assertTrue(e.getContent().contains(RESPONSE_EXCEPTION_STRING));
    }
  }

  private String getHttpResponseString(HttpResponse httpResponse) throws IOException {
    return CharStreams.toString(new InputStreamReader(httpResponse.getContent()));
  }
}
