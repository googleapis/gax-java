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

import com.google.api.core.SettableApiFuture;
import com.google.api.pathtemplate.PathTemplate;
import com.google.common.collect.ImmutableMap;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

public class HttpJsonDirectCallableTest {
  private final ApiMethodDescriptor<String, String> API_DESCRIPTOR =
      ApiMethodDescriptor.<String, String>newBuilder()
          .setFullMethodName("fakeMethod")
          .setHttpMethod("GET")
          .setRequestFormatter(new FakeRequestFormatter())
          .setResponseParser(new FakeResponseParser())
          .build();

  @Test
  public void testTimeout() {
    HttpJsonChannel mockChannel = Mockito.mock(HttpJsonChannel.class);

    String expectedRequest = "fake";

    HttpJsonDirectCallable<String, String> callable = new HttpJsonDirectCallable<>(API_DESCRIPTOR);

    // Mock the channel that captures the call options
    ArgumentCaptor<HttpJsonCallOptions> capturedCallOptions =
        ArgumentCaptor.forClass(HttpJsonCallOptions.class);

    Mockito.when(
            mockChannel.issueFutureUnaryCall(
                capturedCallOptions.capture(),
                Mockito.anyString(),
                Mockito.any(ApiMethodDescriptor.class)))
        .thenReturn(SettableApiFuture.create());

    // Compose the call context
    Duration timeout = Duration.ofSeconds(10);
    Instant minExpectedDeadline = Instant.now().plus(timeout);

    HttpJsonCallContext callContext =
        HttpJsonCallContext.createDefault().withChannel(mockChannel).withTimeout(timeout);

    callable.futureCall(expectedRequest, callContext);

    Instant maxExpectedDeadline = Instant.now().plus(timeout);

    // Verify that the timeout was converted into a deadline
    assertThat(capturedCallOptions.getValue().getDeadline()).isAtLeast(minExpectedDeadline);
    assertThat(capturedCallOptions.getValue().getDeadline()).isAtMost(maxExpectedDeadline);
  }

  @Test
  public void testTimeoutAfterDeadline() {
    HttpJsonChannel mockChannel = Mockito.mock(HttpJsonChannel.class);

    String expectedRequest = "fake";

    HttpJsonDirectCallable<String, String> callable = new HttpJsonDirectCallable<>(API_DESCRIPTOR);

    // Mock the channel that captures the call options
    ArgumentCaptor<HttpJsonCallOptions> capturedCallOptions =
        ArgumentCaptor.forClass(HttpJsonCallOptions.class);

    Mockito.when(
            mockChannel.issueFutureUnaryCall(
                capturedCallOptions.capture(),
                Mockito.anyString(),
                Mockito.any(ApiMethodDescriptor.class)))
        .thenReturn(SettableApiFuture.create());

    // Compose the call context
    Instant priorDeadline = Instant.now().plusSeconds(5);
    Duration timeout = Duration.ofSeconds(10);

    HttpJsonCallContext callContext =
        HttpJsonCallContext.createDefault()
            .withChannel(mockChannel)
            .withDeadline(priorDeadline)
            .withTimeout(timeout);

    callable.futureCall(expectedRequest, callContext);

    // Verify that the timeout was ignored
    assertThat(capturedCallOptions.getValue().getDeadline()).isEqualTo(priorDeadline);
  }

  @Test
  public void testTimeoutBeforeDeadline() {
    HttpJsonChannel mockChannel = Mockito.mock(HttpJsonChannel.class);

    String expectedRequest = "fake";

    HttpJsonDirectCallable<String, String> callable = new HttpJsonDirectCallable<>(API_DESCRIPTOR);

    // Mock the channel that captures the call options
    ArgumentCaptor<HttpJsonCallOptions> capturedCallOptions =
        ArgumentCaptor.forClass(HttpJsonCallOptions.class);

    Mockito.when(
            mockChannel.issueFutureUnaryCall(
                capturedCallOptions.capture(),
                Mockito.anyString(),
                Mockito.any(ApiMethodDescriptor.class)))
        .thenReturn(SettableApiFuture.create());

    // Compose the call context
    Duration timeout = Duration.ofSeconds(10);
    Instant subsequentDeadline = Instant.now().plusSeconds(15);

    Instant minExpectedDeadline = Instant.now().plus(timeout);

    HttpJsonCallContext callContext =
        HttpJsonCallContext.createDefault()
            .withChannel(mockChannel)
            .withDeadline(subsequentDeadline)
            .withTimeout(timeout);

    callable.futureCall(expectedRequest, callContext);

    Instant maxExpectedDeadline = Instant.now().plus(timeout);

    // Verify that the timeout was converted into a deadline
    assertThat(capturedCallOptions.getValue().getDeadline()).isAtLeast(minExpectedDeadline);
    assertThat(capturedCallOptions.getValue().getDeadline()).isAtMost(maxExpectedDeadline);
  }

  private static final class FakeRequestFormatter implements HttpRequestFormatter<String> {
    @Override
    public Map<String, List<String>> getQueryParamNames(String apiMessage) {
      return ImmutableMap.of();
    }

    @Override
    public String getRequestBody(String apiMessage) {
      return "fake";
    }

    @Override
    public String getPath(String apiMessage) {
      return "/fake/path";
    }

    @Override
    public PathTemplate getPathTemplate() {
      return PathTemplate.create("/fake/path");
    }
  }

  private static final class FakeResponseParser implements HttpResponseParser<String> {
    @Override
    public String parse(InputStream httpContent) {
      return "fake";
    }

    @Override
    public String serialize(String response) {
      return response;
    }
  }
}
