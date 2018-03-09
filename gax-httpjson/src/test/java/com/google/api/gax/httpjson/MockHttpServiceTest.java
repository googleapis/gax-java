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
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
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
  private static MockHttpService testService = new MockHttpService();

  private static PetMessage gerbilMessage =
      new PetMessage(
          ImmutableMap.<String, List<String>>of("type", Lists.newArrayList("rodent")), null);
  private static PetMessage ospreyMessage =
      new PetMessage(
          ImmutableMap.<String, List<String>>of("type", Lists.newArrayList("raptor")), null);
  private static HumanMessage humanMessage =
      new HumanMessage(
          ImmutableMap.<String, List<String>>of("type", Lists.newArrayList("toddler")), null);

  private static final ApiException RESPONSE_EXCEPTION =
      new ApiException(null, HttpJsonStatusCode.of(Code.INVALID_ARGUMENT), false);
  private static final ApiException PARSE_EXCEPTION =
      new ApiException(
          "Unknown object type.", null, HttpJsonStatusCode.of(Code.INVALID_ARGUMENT), false);
  private static final GenericUrl TARGET_URL = new GenericUrl("http://google.com");
  private static final HttpRequestFactory HTTP_REQUEST_FACTORY = testService.createRequestFactory();

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

  private static final HttpResponseFormatter<PetMessage> PET_MESSAGE_FORMATTER =
      new HttpResponseFormatter<PetMessage>() {
        @Override
        public PetMessage parse(InputStream httpContent) {
          return null;
        }

        @Override
        public void writeResponse(Appendable output, Object response) {
          if (!(response instanceof PetMessage)) {
            throw PARSE_EXCEPTION;
          }
          try {
            output.append(((PetMessage) response).getFieldStringValue("type"));
          } catch (Exception e) {
            fail();
          }
        }
      };

  @Before
  public void cleanUp() {
    testService.reset();
  }

  @Test
  public void testMockHttpService() throws IOException {
    testService.setResponseFormatter(PET_MESSAGE_FORMATTER);

    // Queue up return objects.
    testService.addResponse(gerbilMessage);
    testService.addResponse(ospreyMessage);
    testService.addResponse(humanMessage);
    testService.addNullResponse();
    testService.addException(RESPONSE_EXCEPTION);

    // First HTTP call returns gerbil.
    HttpResponse httpResponse = HTTP_REQUEST_FACTORY.buildGetRequest(TARGET_URL).execute();
    assertEquals("rodent", getHttpResponseString(httpResponse));

    // Second HTTP call returns osprey.
    httpResponse = HTTP_REQUEST_FACTORY.buildGetRequest(TARGET_URL).execute();
    assertEquals("raptor", getHttpResponseString(httpResponse));

    // Third HTTP call returns human, which is not parsable by PET_MESSAGE_FORMATTER and should fail.
    try {
      HTTP_REQUEST_FACTORY.buildGetRequest(TARGET_URL).execute();
      fail();
    } catch (ApiException e) {
      // Expected parsing exception.
    }

    // Fourth HTTP call returns empty body.
    httpResponse = HTTP_REQUEST_FACTORY.buildGetRequest(TARGET_URL).execute();
    assertNull(httpResponse.getContent());

    // Fifth HTTP call throws exception.
    try {
      HTTP_REQUEST_FACTORY.buildGetRequest(TARGET_URL).execute();
      fail();
    } catch (HttpResponseException e) {
      assertEquals(400, e.getStatusCode());
      assertTrue(e.getContent().contains("ApiException"));
    }
  }

  private String getHttpResponseString(HttpResponse httpResponse) throws IOException {
    return CharStreams.toString(new InputStreamReader(httpResponse.getContent()));
  }
}
