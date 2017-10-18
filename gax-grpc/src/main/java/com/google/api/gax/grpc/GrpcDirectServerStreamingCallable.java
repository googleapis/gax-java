/*
 * Copyright 2017, Google Inc. All rights reserved.
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

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.RequestParamsEncoder;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.common.base.Preconditions;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import java.util.Iterator;

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
  private final RequestParamsEncoder<RequestT> paramsEncoder;

  GrpcDirectServerStreamingCallable(
      MethodDescriptor<RequestT, ResponseT> descriptor,
      RequestParamsEncoder<RequestT> paramsEncoder) {
    this.descriptor = Preconditions.checkNotNull(descriptor);
    this.paramsEncoder = Preconditions.checkNotNull(paramsEncoder);
  }

  @Override
  public void serverStreamingCall(
      RequestT request, ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
    Preconditions.checkNotNull(request);
    Preconditions.checkNotNull(responseObserver);

    ClientCall<RequestT, ResponseT> call =
        GrpcClientCalls.newCall(descriptor, context, request, paramsEncoder);
    ClientCalls.asyncServerStreamingCall(
        call, request, new ApiStreamObserverDelegate<>(responseObserver));
  }

  @Override
  public Iterator<ResponseT> blockingServerStreamingCall(RequestT request, ApiCallContext context) {
    Preconditions.checkNotNull(request);
    ClientCall<RequestT, ResponseT> call =
        GrpcClientCalls.newCall(descriptor, context, request, paramsEncoder);
    return ClientCalls.blockingServerStreamingCall(call, request);
  }
}
