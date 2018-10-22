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
import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.BatchingDescriptor;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.util.concurrent.MoreExecutors;

@InternalApi
public class TracedBatchCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
  private final TracerFactory tracerFactory;
  private final SpanName spanName;
  private final BatchingDescriptor<RequestT, ResponseT> batchingDescriptor;
  private final UnaryCallable<RequestT, ResponseT> innerCallable;

  public TracedBatchCallable(
      UnaryCallable<RequestT, ResponseT> innerCallable,
      TracerFactory tracerFactory,
      SpanName spanName,
      BatchingDescriptor<RequestT, ResponseT> batchingDescriptor) {
    this.tracerFactory = tracerFactory;
    this.spanName = spanName;
    this.batchingDescriptor = batchingDescriptor;
    this.innerCallable = innerCallable;
  }

  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext context) {
    Tracer tracer = tracerFactory.newTracer(Tracer.Type.Batched, spanName);
    TraceFinisher finisher = new TraceFinisher(tracer);

    try {
      long elementCount = batchingDescriptor.countElements(request);
      long requestSize = batchingDescriptor.countBytes(request);

      tracer.sentBatchRequest(elementCount, requestSize);

      context = context.withTracer(tracer);
      ApiFuture<ResponseT> future = innerCallable.futureCall(request, context);
      ApiFutures.addCallback(future, finisher, MoreExecutors.directExecutor());

      return future;
    } catch (RuntimeException e) {
      finisher.onFailure(e);
      throw e;
    }
  }
}
