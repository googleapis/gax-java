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

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StreamController;

public class TracedServerStreamingCallable<RequestT, ResponseT>
    extends ServerStreamingCallable<RequestT, ResponseT> {

  private final TracerFactory tracerFactory;
  private final SpanName spanName;
  private final ServerStreamingCallable<RequestT, ResponseT> innerCallable;

  public TracedServerStreamingCallable(
      ServerStreamingCallable<RequestT, ResponseT> innerCallable,
      TracerFactory tracerFactory,
      SpanName spanName) {
    this.tracerFactory = tracerFactory;
    this.spanName = spanName;
    this.innerCallable = innerCallable;
  }

  @Override
  public void call(
      RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context) {

    Tracer tracer = tracerFactory.newTracer(Tracer.Type.ServerStreaming, spanName);
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

  private static class TracedResponseObserver<ResponseT> implements ResponseObserver<ResponseT> {
    private final Tracer tracer;
    private final ResponseObserver<ResponseT> innerObserver;

    public TracedResponseObserver(Tracer tracer, ResponseObserver<ResponseT> innerObserver) {
      this.tracer = tracer;
      this.innerObserver = innerObserver;
    }

    @Override
    public void onStart(StreamController controller) {
      innerObserver.onStart(controller);
    }

    @Override
    public void onResponse(ResponseT response) {
      tracer.receivedResponse();
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
