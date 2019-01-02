/*
 * Copyright 2019 Google LLC
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
package com.google.api.gax.tracing;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockResponseObserver;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCall;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCallable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class TracedServerStreamingCallableTest {
  private static final SpanName SPAN_NAME = SpanName.of("FakeClient", "FakeRpc");

  @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ApiTracerFactory tracerFactory;
  @Mock private ApiTracer tracer;

  private MockServerStreamingCallable<String, String> innerCallable;
  private TracedServerStreamingCallable<String, String> tracedCallable;
  private MockResponseObserver<String> responseObserver;
  private FakeCallContext callContext;

  @Before
  public void setUp() {
    // Wire the mock tracer factory
    Mockito.when(tracerFactory.newTracer(Mockito.any(SpanName.class))).thenReturn(tracer);
    innerCallable = new MockServerStreamingCallable<>();

    responseObserver = new MockResponseObserver<>(true);
    callContext = FakeCallContext.createDefault();

    // Build the system under test
    tracedCallable = new TracedServerStreamingCallable<>(innerCallable, tracerFactory, SPAN_NAME);
  }

  @Test
  public void testTracerCreated() {
    tracedCallable.call("test", responseObserver, callContext);
    Mockito.verify(tracerFactory, Mockito.times(1)).newTracer(SPAN_NAME);
  }

  @Test
  public void testResponseNotify() {
    tracedCallable.call("test", responseObserver, callContext);

    MockServerStreamingCall<String, String> innerCall = innerCallable.popLastCall();
    innerCall.getController().getObserver().onResponse("response1");
    innerCall.getController().getObserver().onResponse("response2");

    assertThat(responseObserver.popNextResponse()).isEqualTo("response1");
    assertThat(responseObserver.popNextResponse()).isEqualTo("response2");
    Mockito.verify(tracer, Mockito.times(2)).responseReceived();
  }

  @Test
  public void testOperationFinish() {
    tracedCallable.call("test", responseObserver, callContext);

    MockServerStreamingCall<String, String> innerCall = innerCallable.popLastCall();
    innerCall.getController().getObserver().onComplete();

    assertThat(responseObserver.isDone()).isTrue();
    Mockito.verify(tracer, Mockito.times(1)).operationSucceeded();
  }

  @Test
  public void testOperationFail() {
    RuntimeException expectedError = new RuntimeException("expected error");

    tracedCallable.call("test", responseObserver, callContext);

    MockServerStreamingCall<String, String> innerCall = innerCallable.popLastCall();
    innerCall.getController().getObserver().onError(expectedError);

    assertThat(responseObserver.getFinalError()).isEqualTo(expectedError);
    Mockito.verify(tracer, Mockito.times(1)).operationFailed(expectedError);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSyncError() {
    RuntimeException expectedError = new RuntimeException("expected error");

    // Create a broken inner callable
    ServerStreamingCallable<String, String> innerCallable =
        Mockito.mock(ServerStreamingCallable.class);
    Mockito.doThrow(expectedError)
        .when(innerCallable)
        .call(
            Mockito.eq("test"),
            (ResponseObserver<String>) Mockito.any(ResponseObserver.class),
            Mockito.any(ApiCallContext.class));

    // Recreate the tracedCallable using the new inner callable
    tracedCallable = new TracedServerStreamingCallable<>(innerCallable, tracerFactory, SPAN_NAME);

    try {
      tracedCallable.call("test", responseObserver, callContext);
    } catch (RuntimeException e) {
      // noop
    }

    Mockito.verify(tracer, Mockito.times(1)).operationFailed(expectedError);
  }
}
