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

import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ClientStreamReadyObserver;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.common.base.Preconditions;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

/**
 * A wrapper callable that will wrap a callable chain in a trace.
 *
 * <p>This class is meant to be an internal implementation google-cloud-java clients only.
 */
@BetaApi("The surface for tracing is not stable and might change in the future")
@InternalApi("For internal use by google-cloud-java clients only")
public class TracedBidiCallable<RequestT, ResponseT>
    extends BidiStreamingCallable<RequestT, ResponseT> {

  @Nonnull private final ApiTracerFactory tracerFactory;
  @Nonnull private final SpanName spanName;
  @Nonnull private final BidiStreamingCallable<RequestT, ResponseT> innerCallable;

  public TracedBidiCallable(
      @Nonnull BidiStreamingCallable<RequestT, ResponseT> innerCallable,
      @Nonnull ApiTracerFactory tracerFactory,
      @Nonnull SpanName spanName) {
    this.tracerFactory = Preconditions.checkNotNull(tracerFactory, "tracerFactory can't be null");
    this.spanName = Preconditions.checkNotNull(spanName, "spanName can't be null");
    this.innerCallable = Preconditions.checkNotNull(innerCallable, "innerCallable can't be null");
  }

  @Override
  public ClientStream<RequestT> internalCall(
      ResponseObserver<ResponseT> responseObserver,
      ClientStreamReadyObserver<RequestT> onReady,
      ApiCallContext context) {

    ApiTracer tracer = tracerFactory.newTracer(spanName);
    context = context.withTracer(tracer);

    AtomicReference<Throwable> cancellationCauseHolder = new AtomicReference<>(null);

    ResponseObserver<ResponseT> tracedObserver =
        new TracedResponseObserver<>(tracer, responseObserver, cancellationCauseHolder);
    ClientStreamReadyObserver<RequestT> tracedReadyObserver =
        new TracedClientStreamReadyObserver<>(tracer, onReady, cancellationCauseHolder);

    try {
      ClientStream<RequestT> clientStream =
          innerCallable.internalCall(tracedObserver, tracedReadyObserver, context);
      return new TracingClientStream<>(tracer, clientStream, cancellationCauseHolder);
    } catch (RuntimeException e) {
      tracer.operationFailed(e);
      throw e;
    }
  }

  /**
   * {@link ResponseObserver} wrapper to annotate the current trace with received messages and to
   * close the current trace upon completion of the RPC.
   */
  private static class TracedResponseObserver<ResponseT> implements ResponseObserver<ResponseT> {
    private final ApiTracer tracer;
    private final ResponseObserver<ResponseT> innerObserver;
    private final AtomicReference<Throwable> cancellationCauseHolder;

    private TracedResponseObserver(
        ApiTracer tracer,
        ResponseObserver<ResponseT> innerObserver,
        AtomicReference<Throwable> cancellationCauseHolder) {
      this.tracer = tracer;
      this.innerObserver = innerObserver;
      this.cancellationCauseHolder = cancellationCauseHolder;
    }

    @Override
    public void onStart(final StreamController controller) {
      innerObserver.onStart(
          new StreamController() {
            @Override
            public void cancel() {
              cancellationCauseHolder.compareAndSet(
                  null, new CancellationException("Cancelled without cause"));
              controller.cancel();
            }

            @Override
            public void disableAutoInboundFlowControl() {
              controller.disableAutoInboundFlowControl();
            }

            @Override
            public void request(int count) {
              controller.request(count);
            }
          });
    }

    @Override
    public void onResponse(ResponseT response) {
      tracer.responseReceived();
      innerObserver.onResponse(response);
    }

    @Override
    public void onError(Throwable t) {
      Throwable cancellationCause = cancellationCauseHolder.get();
      if (cancellationCause != null) {
        tracer.operationCancelled();
      } else {
        tracer.operationFailed(t);
      }
      innerObserver.onError(t);
    }

    @Override
    public void onComplete() {
      tracer.operationSucceeded();
      innerObserver.onComplete();
    }
  }

  private static class TracedClientStreamReadyObserver<RequestT>
      implements ClientStreamReadyObserver<RequestT> {
    private final ApiTracer tracer;
    private final ClientStreamReadyObserver<RequestT> innerObserver;
    private final AtomicReference<Throwable> cancellationCauseHolder;

    TracedClientStreamReadyObserver(
        ApiTracer tracer,
        ClientStreamReadyObserver<RequestT> innerObserver,
        AtomicReference<Throwable> cancellationCauseHolder) {
      this.tracer = tracer;
      this.innerObserver = innerObserver;
      this.cancellationCauseHolder = cancellationCauseHolder;
    }

    @Override
    public void onReady(ClientStream<RequestT> stream) {
      innerObserver.onReady(new TracingClientStream<>(tracer, stream, cancellationCauseHolder));
    }
  }

  /** {@link ClientStream} wrapper that annotates traces with sent messages. */
  private static class TracingClientStream<RequestT> implements ClientStream<RequestT> {
    private final ApiTracer tracer;
    private final ClientStream<RequestT> innerStream;
    private final AtomicReference<Throwable> cancellationCauseHolder;

    private TracingClientStream(
        ApiTracer tracer,
        ClientStream<RequestT> innerStream,
        AtomicReference<Throwable> cancellationCauseHolder) {
      this.tracer = tracer;
      this.innerStream = innerStream;
      this.cancellationCauseHolder = cancellationCauseHolder;
    }

    @Override
    public void send(RequestT request) {
      tracer.requestSent();
      innerStream.send(request);
    }

    @Override
    public void closeSendWithError(Throwable t) {
      if (t == null) {
        t = new CancellationException("Cancelled without a cause");
      }
      cancellationCauseHolder.compareAndSet(null, t);
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
