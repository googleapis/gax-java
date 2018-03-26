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
import com.google.api.gax.httpjson.ApiMethodDescriptor;
import com.google.api.pathtemplate.PathTemplate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Mocks an HTTPTransport. Expected responses and exceptions can be added to a queue from which this
 * mock HttpTransport polls when it relays a response.
 */
public final class MockHttpService extends MockHttpTransport {

  private final Multimap<String, String> requestHeaders = LinkedListMultimap.create();
  private final List<String> requestPaths = new LinkedList<>();
  private final Queue<HttpResponseFactory> responseHandlers = new LinkedList<>();
  private List<ApiMethodDescriptor> serviceMethodDescriptors;
  private String endpoint;

  /**
   * Create a MockHttpService.
   *
   * @param serviceMethodDescriptors - list of method descriptors for the methods that this mock
   *     server supports
   * @param pathPrefix - the fixed portion of the endpoint URL that prefixes the methods' path
   *     template substring.
   */
  public MockHttpService(List<ApiMethodDescriptor> serviceMethodDescriptors, String pathPrefix) {
    this.serviceMethodDescriptors = ImmutableList.copyOf(serviceMethodDescriptors);
    this.endpoint = pathPrefix;
  }

  @Override
  public LowLevelHttpRequest buildRequest(final String method, final String url) {
    requestPaths.add(url);
    return new MockLowLevelHttpRequest() {
      @Override
      public void addHeader(String name, String value) {
        requestHeaders.put(name, value);
      }

      @Override
      public LowLevelHttpResponse execute() {
        return getHttpResponse(method, url);
      }
    };
  }

  /** Add an ApiMessage to the response queue. */
  public void addResponse(final Object response) {
    responseHandlers.add(
        new MockHttpService.HttpResponseFactory() {
          @Override
          public MockLowLevelHttpResponse getHttpResponse(String httpMethod, String fullTargetUrl) {
            MockLowLevelHttpResponse httpResponse = new MockLowLevelHttpResponse();
            Preconditions.checkArgument(
                serviceMethodDescriptors != null,
                "MockHttpService has null serviceMethodDescriptors.");

            String relativePath = getRelativePath(fullTargetUrl);

            for (ApiMethodDescriptor methodDescriptor : serviceMethodDescriptors) {
              if (!httpMethod.equals(methodDescriptor.getHttpMethod())) {
                continue;
              }

              PathTemplate pathTemplate = methodDescriptor.getRequestFormatter().getPathTemplate();
              // Server figures out which RPC method is called based on the endpoint path pattern.
              if (!pathTemplate.matches(relativePath)) {
                continue;
              }

              // Emulate the server's creation of an HttpResponse from the response message instance.
              String httpContent = methodDescriptor.getResponseParser().serialize(response);

              httpResponse.setContent(httpContent.getBytes());
              httpResponse.setStatusCode(200);
              return httpResponse;
            }

            // Return 404 when none of this server's endpoint templates match the given URL.
            httpResponse.setContent(
                String.format("Method not found for path '%s'", relativePath).getBytes());
            httpResponse.setStatusCode(404);
            return httpResponse;
          }
        });
  }

  /** Add an expected null response (empty HTTP response body). */
  public void addNullResponse() {
    responseHandlers.add(
        new MockHttpService.HttpResponseFactory() {
          @Override
          public MockLowLevelHttpResponse getHttpResponse(String httpMethod, String targetUrl) {
            return new MockLowLevelHttpResponse().setStatusCode(200);
          }
        });
  }

  /** Add an Exception to the response queue. */
  public void addException(final Exception exception) {
    responseHandlers.add(
        new MockHttpService.HttpResponseFactory() {
          @Override
          public MockLowLevelHttpResponse getHttpResponse(String httpMethod, String targetUrl) {
            MockLowLevelHttpResponse httpResponse = new MockLowLevelHttpResponse();
            httpResponse.setStatusCode(400);
            httpResponse.setContent(exception.toString().getBytes());
            httpResponse.setContentEncoding("text/plain");
            return httpResponse;
          }
        });
  }

  /** Get the FIFO list of URL paths to which requests were sent. */
  public List<String> getRequestPaths() {
    return requestPaths;
  }

  /** Get the FIFO list of request headers sent. */
  public Multimap<String, String> getRequestHeaders() {
    return ImmutableListMultimap.copyOf(requestHeaders);
  }

  /* Reset the expected response queue, the method descriptor, and the logged request paths list. */
  public void reset() {
    responseHandlers.clear();
    requestPaths.clear();
    requestHeaders.clear();
  }

  private String getRelativePath(String fullTargetUrl) {
    // relativePath will be repeatedly truncated until it contains only
    // the path template substring of the endpoint URL.
    String relativePath = fullTargetUrl.replaceFirst(endpoint, "");
    int queryParamIndex = relativePath.indexOf("?");
    queryParamIndex = queryParamIndex < 0 ? relativePath.length() : queryParamIndex;
    relativePath = relativePath.substring(0, queryParamIndex);

    return relativePath;
  }

  private MockLowLevelHttpResponse getHttpResponse(String httpMethod, String targetUrl) {
    Preconditions.checkArgument(!responseHandlers.isEmpty());
    return responseHandlers.poll().getHttpResponse(httpMethod, targetUrl);
  }

  private interface HttpResponseFactory {
    MockLowLevelHttpResponse getHttpResponse(String httpMethod, String targetUrl);
  }
}
