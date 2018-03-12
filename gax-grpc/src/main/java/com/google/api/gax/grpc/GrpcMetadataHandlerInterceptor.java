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

import com.google.api.core.InternalApi;
import com.google.common.base.Function;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

/**
 * An interceptor to handle custom headers.
 *
 * <p>Package-private for internal usage.
 */
@InternalApi
public class GrpcMetadataHandlerInterceptor implements ClientInterceptor {

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions, Channel next) {
    ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
    return new SimpleForwardingClientCall<ReqT, RespT>(call) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {

        Function<Metadata, Void> metadataHandler =
            CallOptionsUtil.getMetadataHandlerOption(callOptions);
        Function<Metadata, Void> trailingMetadataHandler =
            CallOptionsUtil.getTrailingMetadataHandlerOption(callOptions);

        if (metadataHandler != null || trailingMetadataHandler != null) {
          responseListener =
              new WrappedListener<>(responseListener, metadataHandler, trailingMetadataHandler);
        }

        super.start(responseListener, headers);
      }
    };
  }

  static class WrappedListener<RespT> extends ForwardingClientCallListener<RespT> {

    private final Listener<RespT> delegate;
    private final Function<Metadata, Void> metadataHandler;
    private final Function<Metadata, Void> trailingMetadataHandler;

    public WrappedListener(
        Listener<RespT> delegate,
        Function<Metadata, Void> metadataHandler,
        Function<Metadata, Void> trailingMetadataHandler) {
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
