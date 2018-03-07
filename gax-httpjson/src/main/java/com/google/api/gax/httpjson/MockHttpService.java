/*
 * Copyright 2018, Google LLC All rights reserved.
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

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public final class MockHttpService extends MockHttpTransport {

  private final List<String> requestPaths = new LinkedList<>();
  private final Queue<Object> responses = new LinkedList<>();
  private ApiMethodDescriptor<ApiMessage, ApiMessage> serializer;

  private MockLowLevelHttpResponse getHttpResponse() {
    MockLowLevelHttpResponse httpResponse = new MockLowLevelHttpResponse();
    Preconditions.checkArgument(!responses.isEmpty());
    Preconditions.checkArgument(serializer != null, "MockHttpService serializer is null.");
    Writer writer = new StringWriter();

    Object response = responses.poll();
    if (response instanceof ApiMessage) {
      serializer.writeResponse(writer, response.getClass(), response);
      httpResponse.setContent(writer.toString().getBytes());
      httpResponse.setStatusCode(200);
    } else if (response instanceof Exception) {
      Exception e = (Exception) response;
      httpResponse.setStatusCode(400);
      httpResponse.setContent(e.toString().getBytes());
      httpResponse.setContentEncoding("text/plain");
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
  public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
    requestPaths.add(url);
    return new MockLowLevelHttpRequest() {
      @Override
      public LowLevelHttpResponse execute() throws IOException {
        return getHttpResponse();
      }
    };
  }

  public void addResponse(ApiMessage response) {
    responses.add(response);
  }

  public void addException(Exception exception) {
    responses.add(exception);
  }

  public List<String> getRequestPaths() {
    return requestPaths;
  }

  public void reset() {
    responses.clear();
    serializer = null;
    requestPaths.clear();
  }

  public void setSerializer(ApiMethodDescriptor serializer) {
    this.serializer = serializer;
  }
}
