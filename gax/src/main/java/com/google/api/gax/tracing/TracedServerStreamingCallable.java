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

import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StreamController;
import com.google.common.base.Preconditions;
import javax.annotation.Nonnull;

/**
 * A wrapper callable that will wrap a callable chain in a trace.
 *
 * <p>This class is meant to be an internal implementation google-cloud-java clients only.
 */
@BetaApi("The surface for tracing is not stable and might change in the future")
@InternalApi("For internal use by google-cloud-java clients only")
public class TracedServerStreamingCallable<RequestT, ResponseT>
    extends ServerStreamingCallable<RequestT, ResponseT> {

  @Nonnull private final ApiTracerFactory tracerFactory;
  @Nonnull private final SpanName spanName;
  @Nonnull private final ServerStreamingCallable<RequestT, ResponseT> innerCallable;

  public TracedServerStreamingCallable(
      @Nonnull ServerStreamingCallable<RequestT, ResponseT> innerCallable,
      @Nonnull ApiTracerFactory tracerFactory,
      @Nonnull SpanName spanName) {
    this.tracerFactory = Preconditions.checkNotNull(tracerFactory, "tracerFactory can't be null");
    this.spanName = Preconditions.checkNotNull(spanName, "spanName can't be null");
    this.innerCallable = Preconditions.checkNotNull(innerCallable, "innerCallable can't be null");
  }

  @Override
  public void call(
      RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context) {

    ApiTracer tracer = tracerFactory.newTracer(spanName);
    TracedResponseObserver<ResponseT> tracedObserver =
        new TracedResponseObserver<>(tracer, responseObserver);

    context = context.withTracer(tracer);

    try {
      innerCallable.call(request, tracedObserver, context);
    } catch (RuntimeException e) {
      tracedObserver.onError(e);
      throw e;
    }
  }

  /** A {@link ResponseObserver} to mark a started operation trace as finished. */
  private static class TracedResponseObserver<ResponseT> implements ResponseObserver<ResponseT> {
    private final ApiTracer tracer;
    private final ResponseObserver<ResponseT> innerObserver;

    private TracedResponseObserver(
        @Nonnull ApiTracer tracer, @Nonnull ResponseObserver<ResponseT> innerObserver) {
      this.tracer = Preconditions.checkNotNull(tracer, "tracer can't be null");
      this.innerObserver = Preconditions.checkNotNull(innerObserver, "innerObserver can't be null");
    }

    @Override
    public void onStart(StreamController controller) {
      innerObserver.onStart(controller);
    }

    @Override
    public void onResponse(ResponseT response) {
      tracer.responseReceived();
      innerObserver.onResponse(response);
    }

    @Override
    public void onError(Throwable t) {
      tracer.operationFailed(t);
      innerObserver.onError(t);
    }

    @Override
    public void onComplete() {
      tracer.operationSucceeded();
      innerObserver.onComplete();
    }
  }
}
