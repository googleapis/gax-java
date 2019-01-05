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
package com.google.api.gax.tracing;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.common.collect.Lists;
import java.util.List;
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
public class TracedClientStreamingCallableTest {
  private static final SpanName SPAN_NAME = SpanName.of("fake-client", "fake-method");
  public @Rule MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ApiTracerFactory tracerFactory;
  @Mock private ApiTracer tracer;

  private FakeClientCallable innerCallable;
  private TracedClientStreamingCallable<String, String> tracedCallable;
  private FakeStreamObserver outerResponseObsever;
  private FakeCallContext callContext;

  @Before
  public void setUp() throws Exception {
    Mockito.when(tracerFactory.newTracer(SPAN_NAME)).thenReturn(tracer);
    innerCallable = new FakeClientCallable();
    tracedCallable = new TracedClientStreamingCallable<>(innerCallable, tracerFactory, SPAN_NAME);
    outerResponseObsever = new FakeStreamObserver();
    callContext = FakeCallContext.createDefault();
  }

  @Test
  public void testTracerCreated() {
    tracedCallable.clientStreamingCall(outerResponseObsever, callContext);

    Mockito.verify(tracerFactory, Mockito.times(1)).newTracer(SPAN_NAME);
  }

  @Test
  public void testOperationFinished() {
    tracedCallable.clientStreamingCall(outerResponseObsever, callContext);
    innerCallable.responseObserver.onNext("ignored");
    innerCallable.responseObserver.onCompleted();

    Mockito.verify(tracer, Mockito.times(1)).operationSucceeded();
  }

  @Test
  public void testOperationFailed() {
    RuntimeException expectedError = new RuntimeException("fake error");
    tracedCallable.clientStreamingCall(outerResponseObsever, callContext);
    innerCallable.responseObserver.onError(expectedError);

    Mockito.verify(tracer, Mockito.times(1)).operationFailed(expectedError);
  }

  @Test
  public void testSyncError() {
    RuntimeException expectedError = new RuntimeException("fake error");
    innerCallable.syncError = expectedError;

    try {
      tracedCallable.clientStreamingCall(outerResponseObsever, callContext);
    } catch (RuntimeException e) {
      // noop
    }

    Mockito.verify(tracer, Mockito.times(1)).operationFailed(expectedError);
  }

  @Test
  public void testRequestNotify() {
    ApiStreamObserver<String> requestStream =
        tracedCallable.clientStreamingCall(outerResponseObsever, callContext);

    requestStream.onNext("request1");
    requestStream.onNext("request2");
    innerCallable.responseObserver.onNext("response");
    innerCallable.responseObserver.onCompleted();

    Mockito.verify(tracer, Mockito.times(2)).requestSent();
    assertThat(outerResponseObsever.completed).isTrue();
    assertThat(innerCallable.requestObserver.messages).containsExactly("request1", "request2");
  }

  private static class FakeClientCallable extends ClientStreamingCallable<String, String> {
    RuntimeException syncError;
    ApiStreamObserver<String> responseObserver;
    ApiCallContext callContext;
    FakeStreamObserver requestObserver;

    @Override
    public ApiStreamObserver<String> clientStreamingCall(
        ApiStreamObserver<String> responseObserver, ApiCallContext context) {

      if (syncError != null) {
        throw syncError;
      }

      this.responseObserver = responseObserver;
      this.callContext = callContext;
      this.requestObserver = new FakeStreamObserver();

      return this.requestObserver;
    }
  }

  private static class FakeStreamObserver implements ApiStreamObserver<String> {
    List<String> messages = Lists.newArrayList();
    Throwable error;
    boolean completed;

    @Override
    public void onNext(String value) {
      messages.add(value);
    }

    @Override
    public void onError(Throwable t) {
      error = t;
      completed = true;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }
}
