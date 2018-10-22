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
package com.google.api.gax.tracing;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.OperationCallable;
import com.google.api.gax.tracing.Tracer.Scope;
import com.google.common.util.concurrent.MoreExecutors;

public class TracedOperationCallable<RequestT, ResponseT, MetadataT>
    extends OperationCallable<RequestT, ResponseT, MetadataT> {
  private OperationCallable<RequestT, ResponseT, MetadataT> innerCallable;
  private final TracerFactory tracerFactory;
  private SpanName spanName;

  public TracedOperationCallable(
      OperationCallable<RequestT, ResponseT, MetadataT> innerCallable,
      TracerFactory tracerFactory,
      SpanName spanName) {
    this.innerCallable = innerCallable;
    this.tracerFactory = tracerFactory;
    this.spanName = spanName;
  }

  @Override
  public OperationFuture<ResponseT, MetadataT> futureCall(
      RequestT request, ApiCallContext context) {
    Tracer tracer = tracerFactory.newTracer(Tracer.Type.LongRunning, spanName);
    TraceFinisher finisher = new TraceFinisher(tracer);

    context = context.withTracer(tracer);

    try (Scope ignored = tracer.inScope()) {
      OperationFuture<ResponseT, MetadataT> future = innerCallable.futureCall(request, context);

      ApiFutures.addCallback(future, finisher, MoreExecutors.directExecutor());

      return future;
    } catch (RuntimeException e) {
      finisher.onFailure(e);
      throw e;
    }
  }

  @Override
  public OperationFuture<ResponseT, MetadataT> resumeFutureCall(
      String operationName, ApiCallContext context) {
    Tracer tracer =
        tracerFactory.newTracer(
            Tracer.Type.LongRunning, spanName.withMethodName(spanName.getMethodName() + ".Resume"));
    TraceFinisher finisher = new TraceFinisher(tracer);

    context = context.withTracer(tracer);

    try (Scope ignored = tracer.inScope()) {
      OperationFuture<ResponseT, MetadataT> future =
          innerCallable.resumeFutureCall(operationName, context);

      ApiFutures.addCallback(future, finisher, MoreExecutors.directExecutor());

      return future;
    } catch (RuntimeException e) {
      finisher.onFailure(e);
      throw e;
    }
  }

  @Override
  public ApiFuture<Void> cancel(String operationName, ApiCallContext context) {
    Tracer tracer =
        tracerFactory.newTracer(
            Tracer.Type.Unary, spanName.withMethodName(spanName.getMethodName() + ".Resume"));
    TraceFinisher finisher = new TraceFinisher(tracer);

    context = context.withTracer(tracer);

    try (Scope ignored = tracer.inScope()) {
      ApiFuture<Void> future = innerCallable.cancel(operationName, context);

      ApiFutures.addCallback(future, finisher, MoreExecutors.directExecutor());

      return future;
    } catch (RuntimeException e) {
      finisher.onFailure(e);
      throw e;
    }
  }
}
