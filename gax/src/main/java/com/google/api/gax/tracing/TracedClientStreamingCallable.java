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

import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.tracing.Tracer.Type;

@InternalApi
public class TracedClientStreamingCallable<RequestT, ResponseT>
    extends ClientStreamingCallable<RequestT, ResponseT> {
  private final ClientStreamingCallable<RequestT, ResponseT> innerCallable;
  private final TracerFactory tracerFactory;
  private final SpanName spanName;

  public TracedClientStreamingCallable(
      ClientStreamingCallable<RequestT, ResponseT> innerCallable,
      TracerFactory tracerFactory,
      SpanName spanName) {
    this.innerCallable = innerCallable;
    this.tracerFactory = tracerFactory;
    this.spanName = spanName;
  }

  @Override
  public ApiStreamObserver<RequestT> clientStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {

    Tracer tracer = tracerFactory.newTracer(Type.ClientStreaming, spanName);
    context = context.withTracer(tracer);

    try {
      ApiStreamObserver<ResponseT> innerResponseObserver =
          new TracedResponseObserver<>(tracer, responseObserver);
      ApiStreamObserver<RequestT> innerRequestObserver =
          innerCallable.clientStreamingCall(innerResponseObserver, context);

      return new TracedRequestObserver<>(tracer, innerRequestObserver);
    } catch (RuntimeException e) {
      tracer.operationFailed(e);
      throw e;
    }
  }

  private static class TracedRequestObserver<RequestT> implements ApiStreamObserver<RequestT> {
    private final Tracer tracer;
    private final ApiStreamObserver<RequestT> innerObserver;

    TracedRequestObserver(Tracer tracer, ApiStreamObserver<RequestT> innerObserver) {
      this.tracer = tracer;
      this.innerObserver = innerObserver;
    }

    @Override
    public void onNext(RequestT value) {
      tracer.sentRequest();
      innerObserver.onNext(value);
    }

    @Override
    public void onError(Throwable t) {
      innerObserver.onError(t);
    }

    @Override
    public void onCompleted() {
      innerObserver.onCompleted();
    }
  }

  private static class TracedResponseObserver<RequestT> implements ApiStreamObserver<RequestT> {
    private final Tracer tracer;
    private final ApiStreamObserver<RequestT> innerObserver;

    TracedResponseObserver(Tracer tracer, ApiStreamObserver<RequestT> innerObserver) {
      this.tracer = tracer;
      this.innerObserver = innerObserver;
    }

    @Override
    public void onNext(RequestT value) {
      this.tracer.receivedResponse();
      innerObserver.onNext(value);
    }

    @Override
    public void onError(Throwable t) {
      tracer.operationFailed(t);
      innerObserver.onError(t);
    }

    @Override
    public void onCompleted() {
      tracer.operationSucceeded();
      innerObserver.onCompleted();
    }
  }
}
