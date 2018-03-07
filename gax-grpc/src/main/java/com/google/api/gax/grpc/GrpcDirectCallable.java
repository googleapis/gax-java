/*
 * Copyright 2016 Google LLC
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

import com.google.api.core.ApiFuture;
import com.google.api.core.ListenableFutureToApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.ClientCalls;

/**
 * {@code GrpcDirectCallable} creates gRPC calls.
 *
 * <p>Package-private for internal use.
 */
class GrpcDirectCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
  private final MethodDescriptor<RequestT, ResponseT> descriptor;

  GrpcDirectCallable(MethodDescriptor<RequestT, ResponseT> descriptor) {
    this.descriptor = Preconditions.checkNotNull(descriptor);
  }

  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext inputContext) {
    Preconditions.checkNotNull(request);
    Preconditions.checkNotNull(inputContext);
    GrpcCallContext context = GrpcCallContext.createDefault().nullToSelf(inputContext);

    WrappedClientCall<RequestT, ResponseT> clientCall =
        newCall(
            context.getChannel(),
            context.getCallOptions(),
            context.getMetadataHandler(),
            context.getTrailingMetadataHandler());

    return new ListenableFutureToApiFuture<>(ClientCalls.futureUnaryCall(clientCall, request));
  }

  private WrappedClientCall<RequestT, ResponseT> newCall(
      Channel channel,
      CallOptions callOptions,
      Function<Object, Boolean> metadataHandler,
      Function<Object, Boolean> trailingMetadataHandler) {
    Preconditions.checkNotNull(channel);
    Preconditions.checkNotNull(callOptions);
    return new WrappedClientCall<>(
        channel.newCall(descriptor, callOptions), metadataHandler, trailingMetadataHandler);
  }

  static class WrappedClientCall<RequestT, ResponseT>
      extends ForwardingClientCall<RequestT, ResponseT> {
    private final ClientCall<RequestT, ResponseT> delegate;
    private final Function<Object, Boolean> metadataHandler;
    private final Function<Object, Boolean> trailingMetadataHandler;

    public WrappedClientCall(
        ClientCall<RequestT, ResponseT> delegate,
        Function<Object, Boolean> metadataHandler,
        Function<Object, Boolean> trailingMetadataHandler) {
      this.delegate = delegate;
      this.metadataHandler = metadataHandler;
      this.trailingMetadataHandler = trailingMetadataHandler;
    }

    @Override
    public void start(Listener<ResponseT> responseListener, Metadata headers) {
      Listener<ResponseT> wrappedListener =
          new WrappedListener<>(responseListener, metadataHandler, trailingMetadataHandler);
      super.start(wrappedListener, headers);
    }

    @Override
    protected ClientCall<RequestT, ResponseT> delegate() {
      return delegate;
    }
  }

  static class WrappedListener<RespT> extends ForwardingClientCallListener<RespT> {

    private final Listener<RespT> delegate;
    private final Function<Object, Boolean> metadataHandler;
    private final Function<Object, Boolean> trailingMetadataHandler;

    public WrappedListener(
        Listener<RespT> delegate,
        Function<Object, Boolean> metadataHandler,
        Function<Object, Boolean> trailingMetadataHandler) {
      this.delegate = delegate;
      this.metadataHandler = metadataHandler;
      this.trailingMetadataHandler = trailingMetadataHandler;
    }

    @Override
    public void onHeaders(Metadata headers) {
      super.onHeaders(headers);
      if (metadataHandler != null) {
        metadataHandler.apply(headers);
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      super.onClose(status, trailers);
      if (trailingMetadataHandler != null) {
        trailingMetadataHandler.apply(trailers);
      }
    }

    @Override
    protected Listener<RespT> delegate() {
      return delegate;
    }
  }
}
