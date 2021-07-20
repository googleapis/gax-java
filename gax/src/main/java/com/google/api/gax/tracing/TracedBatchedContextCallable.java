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

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.InternalApi;
import com.google.api.gax.batching.BatchedCallContext;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.tracing.ApiTracerFactory.OperationType;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * This callable wraps a batching callable chain in an {@link ApiTracer} and annotates {@link
 * BatchedCallContext} batching context data.
 *
 * <p>For internal use only.
 */
@InternalApi("For internal use by google-cloud-java clients only")
public class TracedBatchedContextCallable<RequestT, ResponseT>
    extends UnaryCallable<RequestT, ResponseT> {

  private final ApiTracerFactory tracerFactory;
  private ApiCallContext baseCallContext;
  private final SpanName spanName;
  private final UnaryCallable<RequestT, ResponseT> innerCallable;

  public TracedBatchedContextCallable(
      UnaryCallable<RequestT, ResponseT> innerCallable,
      ApiCallContext callContext,
      ApiTracerFactory tracerFactory,
      SpanName spanName) {
    this.baseCallContext = Preconditions.checkNotNull(callContext);
    this.tracerFactory = Preconditions.checkNotNull(tracerFactory);
    this.spanName = Preconditions.checkNotNull(spanName);
    this.innerCallable = Preconditions.checkNotNull(innerCallable);
  }

  /**
   * Creates an {@link ApiTracer} and annotates batching context data. Performs a call
   * asynchronously.
   */
  public ApiFuture<ResponseT> futureCall(RequestT request, BatchedCallContext batchedCallContext) {
    return futureCall(
        request,
        baseCallContext,
        batchedCallContext.getTotalThrottledTimeMs(),
        batchedCallContext.getElementCount(),
        batchedCallContext.getByteCount());
  }

  /** Calls the wrapped {@link UnaryCallable} within the context of a new trace. */
  @Override
  public ApiFuture futureCall(RequestT request, ApiCallContext context) {
    ApiCallContext mergedContext = baseCallContext.merge(context);

    return futureCall(request, mergedContext, null, null, null);
  }

  private ApiFuture futureCall(
      RequestT request,
      ApiCallContext callContext,
      Long throttledTimeMs,
      Long elementCount,
      Long byteCount) {
    ApiTracer tracer =
        tracerFactory.newTracer(callContext.getTracer(), spanName, OperationType.Batching);
    TraceFinisher<ResponseT> finisher = new TraceFinisher<>(tracer);

    try {
      if (throttledTimeMs != null) {
        tracer.batchRequestThrottled(throttledTimeMs);
      }
      if (elementCount != null && byteCount != null) {
        tracer.batchRequestSent(elementCount, byteCount);
      }
      callContext = callContext.withTracer(tracer);
      ApiFuture<ResponseT> future = innerCallable.futureCall(request, callContext);
      ApiFutures.addCallback(future, finisher, MoreExecutors.directExecutor());
      return future;
    } catch (RuntimeException e) {
      finisher.onFailure(e);
      throw e;
    }
  }

  public UnaryCallable<RequestT, ResponseT> withDefaultCallContext(
      final ApiCallContext defaultCallContext) {
    return new TracedBatchedContextCallable<>(
        innerCallable, baseCallContext.merge(defaultCallContext), tracerFactory, spanName);
  }
}
