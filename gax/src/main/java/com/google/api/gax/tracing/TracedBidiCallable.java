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
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ClientStreamReadyObserver;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.api.gax.tracing.Tracer.Scope;

public class TracedBidiCallable<RequestT, ResponseT>
    extends BidiStreamingCallable<RequestT, ResponseT> {

  private final TracerFactory tracerFactory;
  private final SpanName spanName;
  private final BidiStreamingCallable<RequestT, ResponseT> innerCallable;

  public TracedBidiCallable(
      BidiStreamingCallable<RequestT, ResponseT> innerCallable,
      TracerFactory tracerFactory,
      SpanName spanName) {
    this.tracerFactory = tracerFactory;
    this.spanName = spanName;
    this.innerCallable = innerCallable;
  }

  @Override
  public ClientStream<RequestT> internalCall(
      ResponseObserver<ResponseT> responseObserver,
      ClientStreamReadyObserver<RequestT> onReady,
      ApiCallContext context) {

    Tracer tracer = tracerFactory.newTracer(Tracer.Type.Bidi, spanName);
    context = context.withTracer(tracer);

    ResponseObserver<ResponseT> tracedObserver =
        new TracingResponseObserver<>(tracer, responseObserver);
    ClientStreamReadyObserver<RequestT> tracedReadyObserver =
        new TracingClientStreamReadyObserver<>(tracer, onReady);

    try (Scope scope = tracer.inScope()) {
      ClientStream<RequestT> clientStream =
          innerCallable.internalCall(tracedObserver, tracedReadyObserver, context);
      return new TracingClientStream<>(tracer, clientStream);
    }
  }

  private static class TracingResponseObserver<ResponseT> implements ResponseObserver<ResponseT> {
    private final Tracer tracer;
    private final ResponseObserver<ResponseT> innerObserver;

    public TracingResponseObserver(Tracer tracer, ResponseObserver<ResponseT> innerObserver) {
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

  private static class TracingClientStreamReadyObserver<RequestT>
      implements ClientStreamReadyObserver<RequestT> {
    private final Tracer tracer;
    private final ClientStreamReadyObserver<RequestT> innerObserver;

    public TracingClientStreamReadyObserver(
        Tracer tracer, ClientStreamReadyObserver<RequestT> innerObserver) {
      this.tracer = tracer;
      this.innerObserver = innerObserver;
    }

    @Override
    public void onReady(ClientStream<RequestT> stream) {
      innerObserver.onReady(new TracingClientStream<>(tracer, stream));
    }
  }

  private static class TracingClientStream<RequestT> implements ClientStream<RequestT> {
    private final Tracer tracer;
    private final ClientStream<RequestT> innerStream;

    public TracingClientStream(Tracer tracer, ClientStream<RequestT> innerStream) {
      this.tracer = tracer;
      this.innerStream = innerStream;
    }

    @Override
    public void send(RequestT request) {
      tracer.sentRequest();
      innerStream.send(request);
    }

    @Override
    public void closeSendWithError(Throwable t) {
      innerStream.closeSendWithError(t);
    }

    @Override
    public void closeSend() {
      innerStream.closeSend();
    }

    @Override
    public boolean isSendReady() {
      return innerStream.isSendReady();
    }
  }
}
