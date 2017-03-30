/*
 * Copyright 2016, Google Inc. All rights reserved.
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
 *     * Neither the name of Google Inc. nor the names of its
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

import com.google.api.gax.core.ApiStreamObserver;
import com.google.common.base.Preconditions;
import io.grpc.ClientCall;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import java.util.Iterator;

/**
 * {@code DirectStreamingCallable} uses the given {@link ClientCallFactory} to create streaming gRPC
 * calls.
 *
 * <p>
 * It is used to bridge the abstractions provided by gRPC and GAX.
 *
 * <p>
 * Package-private for internal use.
 */
class DirectStreamingCallable<RequestT, ResponseT> {

  private final ClientCallFactory<RequestT, ResponseT> factory;

  DirectStreamingCallable(ClientCallFactory<RequestT, ResponseT> factory) {
    Preconditions.checkNotNull(factory);
    this.factory = factory;
  }

  void serverStreamingCall(
      RequestT request, ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
    Preconditions.checkNotNull(request);
    Preconditions.checkNotNull(responseObserver);
    ClientCall<RequestT, ResponseT> call =
        factory.newCall(context.getChannel(), context.getCallOptions());
    ClientCalls.asyncServerStreamingCall(
        call, request, new RpcStreamObserverDelegate(responseObserver));
  }

  Iterator<ResponseT> blockingServerStreamingCall(RequestT request, CallContext context) {
    Preconditions.checkNotNull(request);
    ClientCall<RequestT, ResponseT> call =
        factory.newCall(context.getChannel(), context.getCallOptions());
    return ClientCalls.blockingServerStreamingCall(call, request);
  }

  ApiStreamObserver<RequestT> bidiStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
    Preconditions.checkNotNull(responseObserver);
    ClientCall<RequestT, ResponseT> call =
        factory.newCall(context.getChannel(), context.getCallOptions());
    return new StreamObserverDelegate(
        ClientCalls.asyncBidiStreamingCall(call, new RpcStreamObserverDelegate(responseObserver)));
  }

  ApiStreamObserver<RequestT> clientStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
    Preconditions.checkNotNull(responseObserver);
    ClientCall<RequestT, ResponseT> call =
        factory.newCall(context.getChannel(), context.getCallOptions());
    return new StreamObserverDelegate(
        ClientCalls.asyncClientStreamingCall(
            call, new RpcStreamObserverDelegate(responseObserver)));
  }

  private static class RpcStreamObserverDelegate<V> implements StreamObserver<V> {

    private final ApiStreamObserver<V> delegate;

    public RpcStreamObserverDelegate(ApiStreamObserver<V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onNext(V v) {
      delegate.onNext(v);
    }

    @Override
    public void onError(Throwable throwable) {
      delegate.onError(throwable);
    }

    @Override
    public void onCompleted() {
      delegate.onCompleted();
    }
  }

  private static class StreamObserverDelegate<V> implements ApiStreamObserver<V> {

    private final StreamObserver<V> delegate;

    public StreamObserverDelegate(StreamObserver<V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onNext(V v) {
      delegate.onNext(v);
    }

    @Override
    public void onError(Throwable throwable) {
      delegate.onError(throwable);
    }

    @Override
    public void onCompleted() {
      delegate.onCompleted();
    }
  }
}
