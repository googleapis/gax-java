/*
 * Copyright 2021 Google LLC
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.batching.BatchedCallContext;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.tracing.ApiTracerFactory.OperationType;
import java.util.Random;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class TracedBatchedContextCallableTest {
  private static final SpanName SPAN_NAME = SpanName.of("FakeClient", "FakeRpc");

  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock private ApiTracerFactory tracerFactory;
  @Mock private ApiTracer tracer;
  @Mock private UnaryCallable<String, String> innerCallable;
  @Mock private BatchedCallContext batchedCallContext;
  private SettableApiFuture<String> innerResult;

  private FakeCallContext callContext;
  private TracedBatchedContextCallable<String, String> callable;

  @Before
  public void setUp() {
    when(tracerFactory.newTracer(
            any(ApiTracer.class), any(SpanName.class), eq(OperationType.Batching)))
        .thenReturn(tracer);
    innerResult = SettableApiFuture.create();
    when(innerCallable.futureCall(anyString(), any(ApiCallContext.class))).thenReturn(innerResult);

    callContext = FakeCallContext.createDefault();
    callable =
        new TracedBatchedContextCallable(innerCallable, callContext, tracerFactory, SPAN_NAME);
  }

  @Test
  public void testRootTracerCreated() {
    callable.futureCall("test", batchedCallContext);
    verify(tracerFactory, times(1))
        .newTracer(callContext.getTracer(), SPAN_NAME, OperationType.Batching);
  }

  @Test
  public void testThrottledTimeRecorded() {
    long throttledTime = new Random().nextLong();
    when(batchedCallContext.getTotalThrottledTimeMs()).thenReturn(throttledTime);
    callable.futureCall("test", batchedCallContext);
    verify(tracer).batchRequestThrottled(throttledTime);
  }

  @Test
  public void testOperationFinish() {
    innerResult.set("success");
    callable.futureCall("test", batchedCallContext);
    verify(tracer, times(1)).operationSucceeded();
  }

  @Test
  public void testOperationFailed() {
    RuntimeException fakeException = new RuntimeException("Exception");
    innerResult.setException(fakeException);
    callable.futureCall("test", batchedCallContext);
    verify(tracer, times(1)).operationFailed(fakeException);
  }
}
