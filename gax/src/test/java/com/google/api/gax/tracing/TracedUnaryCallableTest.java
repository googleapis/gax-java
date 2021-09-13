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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.tracing.ApiTracerFactory.OperationType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class TracedUnaryCallableTest {
  private static final SpanName SPAN_NAME = SpanName.of("FakeClient", "FakeRpc");

  @Rule
  public final MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock private ApiTracerFactory tracerFactory;
  private ApiTracer parentTracer;
  @Mock private ApiTracer tracer;
  @Mock private UnaryCallable<String, String> innerCallable;
  private SettableApiFuture<String> innerResult;

  private TracedUnaryCallable<String, String> tracedUnaryCallable;
  private FakeCallContext callContext;

  @Before
  public void setUp() {
    parentTracer = BaseApiTracer.getInstance();

    // Wire the mock tracer factory
    when(tracerFactory.newTracer(
            any(ApiTracer.class), any(SpanName.class), eq(OperationType.Unary)))
        .thenReturn(tracer);

    // Wire the mock inner callable
    innerResult = SettableApiFuture.create();
    when(innerCallable.futureCall(anyString(), any(ApiCallContext.class))).thenReturn(innerResult);

    // Build the system under test
    tracedUnaryCallable = new TracedUnaryCallable<>(innerCallable, tracerFactory, SPAN_NAME);
    callContext = FakeCallContext.createDefault();
  }

  @Test
  public void testTracerCreated() {
    tracedUnaryCallable.futureCall("test", callContext);
    verify(tracerFactory, times(1)).newTracer(parentTracer, SPAN_NAME, OperationType.Unary);
  }

  @Test
  public void testOperationFinish() {
    innerResult.set("successful result");
    tracedUnaryCallable.futureCall("test", callContext);

    verify(tracer, times(1)).operationSucceeded();
  }

  @Test
  public void testOperationCancelled() {
    innerResult.cancel(true);
    tracedUnaryCallable.futureCall("test", callContext);
    verify(tracer, times(1)).operationCancelled();
  }

  @Test
  public void testOperationFailed() {
    RuntimeException fakeError = new RuntimeException("fake error");
    innerResult.setException(fakeError);
    tracedUnaryCallable.futureCall("test", callContext);

    verify(tracer, times(1)).operationFailed(fakeError);
  }

  @Test
  public void testSyncError() {
    RuntimeException fakeError = new RuntimeException("fake error");

    // Reset the irrelevant expectations from setup. (only needed to silence the warnings).
    @SuppressWarnings("unchecked")
    UnaryCallable<String, String>[] innerCallableWrapper = new UnaryCallable[] {innerCallable};
    reset(innerCallableWrapper);

    when(innerCallable.futureCall(eq("failing test"), any(ApiCallContext.class)))
        .thenThrow(fakeError);

    try {
      tracedUnaryCallable.futureCall("failing test", callContext);
    } catch (RuntimeException e) {
      // ignored
    }

    verify(tracer, times(1)).operationFailed(fakeError);
  }
}
