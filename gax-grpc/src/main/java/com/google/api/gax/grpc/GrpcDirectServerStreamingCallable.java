/*
 * Copyright 2017, Google LLC All rights reserved.
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
package com.google.api.gax.grpc;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StreamController;
import com.google.common.base.Preconditions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import javax.annotation.Nullable;

/**
 * {@code GrpcDirectServerStreamingCallable} creates server-streaming gRPC calls.
 *
 * <p>It is used to bridge the abstractions provided by gRPC and GAX.
 *
 * <p>Package-private for internal use.
 */
class GrpcDirectServerStreamingCallable<RequestT, ResponseT>
    extends ServerStreamingCallable<RequestT, ResponseT> {
  private final MethodDescriptor<RequestT, ResponseT> descriptor;

  GrpcDirectServerStreamingCallable(MethodDescriptor<RequestT, ResponseT> descriptor) {
    this.descriptor = Preconditions.checkNotNull(descriptor);
  }

  @Override
  public void call(
      RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context) {
    Preconditions.checkNotNull(request);
    Preconditions.checkNotNull(responseObserver);

    ClientCall<RequestT, ResponseT> call = GrpcClientCalls.newCall(descriptor, context);
    GrpcStreamController<RequestT, ResponseT> controller =
        new GrpcStreamController<>(call, responseObserver);
    controller.start(request);
  }

  /**
   * Wraps a GRPC ClientCall in a {@link StreamController}. It feeds events to a {@link
   * ResponseObserver} and allows for back pressure.
   */
  static class GrpcStreamController<RequestT, ResponseT> extends StreamController {
    private final ClientCall<RequestT, ResponseT> clientCall;
    private final ResponseObserver<ResponseT> observer;
    private boolean hasStarted;
    private boolean autoflowControl = true;
    private int numRequested;

    GrpcStreamController(
        ClientCall<RequestT, ResponseT> clientCall, ResponseObserver<ResponseT> observer) {
      this.clientCall = clientCall;
      this.observer = observer;
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
      clientCall.cancel(message, cause);
    }

    @Override
    public void disableAutoInboundFlowControl() {
      Preconditions.checkState(
          !hasStarted, "Can't disable automatic flow control after the stream has started.");
      autoflowControl = false;
    }

    @Override
    public void request(int count) {
      Preconditions.checkState(!autoflowControl, "Autoflow control is enabled.");

      // Buffer the requested count in case the consumer requested responses in the onStart()
      if (!hasStarted) {
        numRequested += count;
      } else {
        clientCall.request(count);
      }
    }

    void start(RequestT request) {
      observer.onStart(this);

      this.hasStarted = true;

      clientCall.start(
          new ResponseObserverAdapter<>(clientCall, autoflowControl, observer), new Metadata());

      clientCall.sendMessage(request);
      clientCall.halfClose();

      if (autoflowControl) {
        clientCall.request(1);
      } else if (numRequested > 0) {
        clientCall.request(numRequested);
      }
    }
  }

  /**
   * Adapts the events from a {@link ClientCall.Listener} to a {@link ResponseObserver} and handles
   * automatic flow control.
   *
   * @param <ResponseT> The type of the response.
   */
  static class ResponseObserverAdapter<ResponseT> extends ClientCall.Listener<ResponseT> {
    private final ClientCall<?, ResponseT> clientCall;
    private final boolean autoflowControl;
    private final ResponseObserver<ResponseT> delegate;

    ResponseObserverAdapter(
        ClientCall<?, ResponseT> clientCall,
        boolean autoflowControl,
        ResponseObserver<ResponseT> delegate) {
      this.clientCall = clientCall;
      this.autoflowControl = autoflowControl;
      this.delegate = delegate;
    }

    /**
     * Notifies the delegate of the new message and if automatic flow control is enabled, requests
     * the next message. Any errors raised by the delegate will be bubbled up to GRPC, which cancel
     * the ClientCall and close this listener.
     *
     * @param message The new message.
     */
    @Override
    public void onMessage(ResponseT message) {
      delegate.onResponse(message);

      if (autoflowControl) {
        clientCall.request(1);
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      if (status.isOk()) {
        delegate.onComplete();
      } else {
        delegate.onError(status.asRuntimeException(trailers));
      }
    }
  }
}
