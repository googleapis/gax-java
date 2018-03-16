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
package com.google.api.gax.httpjson.testing;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.gax.httpjson.ApiMessage;
import com.google.api.gax.httpjson.ApiMethodDescriptor;
import com.google.api.pathtemplate.PathTemplate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/* Mocks an HTTPTransport. Expected responses and exceptions can be added to a queue
 * from which this mock HttpTransport polls when it relays a response. */
public final class MockHttpService extends MockHttpTransport {

  private final List<String> requestPaths = new LinkedList<>();
  private final Queue<Object> responses = new LinkedList<>();
  private List<ApiMethodDescriptor<? extends ApiMessage, ? extends ApiMessage>> serializers;
  private String endpoint;

  /* Create a MockHttpService.
   *
   * @param serializers - the list of method descriptors for the methods that the mocked server supports.
   * @param fixedEndpoint - the fixed portion of the endpoint URL that prefixes the methods' path template substring. */
  public MockHttpService(
      List<ApiMethodDescriptor<? extends ApiMessage, ? extends ApiMessage>> serializers,
      String fixedEndpoint) {
    this.serializers = ImmutableList.copyOf(serializers);
    this.endpoint = fixedEndpoint;
  }

  private static final class NullResponse {}

  private MockLowLevelHttpResponse getHttpResponse(String targetUrl) {
    MockLowLevelHttpResponse httpResponse = new MockLowLevelHttpResponse();
    Preconditions.checkArgument(!responses.isEmpty());
    Writer writer = new StringWriter();

    Object response = responses.poll();
    if (response instanceof ApiMessage) {
      Preconditions.checkArgument(serializers != null, "MockHttpService has null serializers.");

      // relativePath will be repeatedly truncated until it contains only
      // the path template substring of the endpoint URL.
      String relativePath = targetUrl.replaceFirst(endpoint, "");
      int queryParamIndex = relativePath.indexOf("?");
      queryParamIndex = queryParamIndex < 0 ? relativePath.length() : queryParamIndex;
      relativePath = relativePath.substring(0, queryParamIndex);

      for (ApiMethodDescriptor<? extends ApiMessage, ? extends ApiMessage> methodDescriptor :
          serializers) {
        // Server figures out which RPC method is called based on the endpoint path pattern.
        if (PathTemplate.create(methodDescriptor.endpointPathTemplate()).matches(relativePath)) {
          methodDescriptor.writeResponse(writer, response.getClass(), response);
          httpResponse.setContent(writer.toString().getBytes());
          httpResponse.setStatusCode(200);
          return httpResponse;
        }
      }
      throw new IllegalArgumentException("Method descriptor not found for response object.");
    } else if (response instanceof Exception) {
      Exception e = (Exception) response;
      httpResponse.setStatusCode(400);
      httpResponse.setContent(e.toString().getBytes());
      httpResponse.setContentEncoding("text/plain");
    } else if (response instanceof NullResponse) {
      return new MockLowLevelHttpResponse().setStatusCode(200);
    } else {
      Exception e =
          new IllegalArgumentException(
              String.format(
                  "response \"%s\" type \"%s\" is unrecognized",
                  response.toString(), response.getClass().getName()));
      httpResponse.setStatusCode(400);
      httpResponse.setContent(e.toString().getBytes());
      httpResponse.setContentEncoding("text/plain");
    }

    return httpResponse;
  }

  @Override
  public LowLevelHttpRequest buildRequest(String method, final String url) throws IOException {
    requestPaths.add(url);
    return new MockLowLevelHttpRequest() {
      @Override
      public LowLevelHttpResponse execute() throws IOException {
        return getHttpResponse(url);
      }
    };
  }

  /* Add an ApiMessage to the response queue. */
  public void addResponse(ApiMessage response) {
    responses.add(response);
  }

  /* Add an expected null response (empty HTTP response body). */
  public void addNullResponse() {
    responses.add(new NullResponse());
  }

  /* Add an Exception to the response queue. */
  public void addException(Exception exception) {
    responses.add(exception);
  }

  /* Get the FIFO list of URL paths to which requests were sent. */
  public List<String> getRequestPaths() {
    return requestPaths;
  }

  /* Reset the expected response queue, the method descriptor, and the logged request paths list. */
  public void reset() {
    responses.clear();
    requestPaths.clear();
  }
}
