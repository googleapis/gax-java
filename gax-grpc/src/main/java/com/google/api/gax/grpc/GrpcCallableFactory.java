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

import com.google.api.core.BetaApi;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.rpc.EmptyRequestParamsExtractor;
import com.google.api.gax.rpc.RequestParamsExtractor;
import com.google.api.gax.rpc.RequestUrlParamsEncoder;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.UnaryCallable;
import io.grpc.MethodDescriptor;

/** Class with utility methods to create grpc-based direct callables. */
@BetaApi
public class GrpcCallableFactory {

  private GrpcCallableFactory() {}

  /**
   * Create a callable object that directly issues the call to the underlying API with nothing
   * wrapping it. Designed for use by generated code.
   *
   * @param methodDescriptor the gRPC method descriptor
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createDirectCallable(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    return createDirectCallable(methodDescriptor, EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a callable object that directly issues the call to the underlying API with nothing
   * wrapping it. Designed for use by generated code.
   *
   * @param methodDescriptor the gRPC method descriptor
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   */
  @BetaApi
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createDirectCallable(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor,
      RequestParamsExtractor<RequestT> paramsExtractor) {
    return new GrpcDirectCallable<>(
        methodDescriptor, new RequestUrlParamsEncoder<>(paramsExtractor, false));
  }

  /**
   * Create a callable object that directly issues the bidirectional streaming call to the
   * underlying API with nothing wrapping it. Designed for use by generated code.
   *
   * @param methodDescriptor the gRPC method descriptor
   */
  public static <RequestT, ResponseT>
      GrpcDirectBidiStreamingCallable<RequestT, ResponseT> createDirectBidiStreamingCallable(
          MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    return new GrpcDirectBidiStreamingCallable<>(methodDescriptor);
  }

  /**
   * Create a callable object that directly issues the server streaming call to the underlying API
   * with nothing wrapping it. Designed for use by generated code.
   *
   * @param methodDescriptor the gRPC method descriptor
   */
  public static <RequestT, ResponseT>
      ServerStreamingCallable<RequestT, ResponseT> createDirectServerStreamingCallable(
          MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    return createDirectServerStreamingCallable(
        methodDescriptor, EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a callable object that directly issues the server streaming call to the underlying API
   * with nothing wrapping it. Designed for use by generated code.
   *
   * @param methodDescriptor the gRPC method descriptor
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   */
  @BetaApi
  public static <RequestT, ResponseT>
      ServerStreamingCallable<RequestT, ResponseT> createDirectServerStreamingCallable(
          MethodDescriptor<RequestT, ResponseT> methodDescriptor,
          RequestParamsExtractor<RequestT> paramsExtractor) {
    return new GrpcDirectServerStreamingCallable<>(
        methodDescriptor, new RequestUrlParamsEncoder<>(paramsExtractor, false));
  }

  /**
   * Create a callable object that directly issues the client streaming call to the underlying API
   * with nothing wrapping it. Designed for use by generated code.
   *
   * @param methodDescriptor the gRPC method descriptor
   */
  public static <RequestT, ResponseT>
      ClientStreamingCallable<RequestT, ResponseT> createDirectClientStreamingCallable(
          MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    return new GrpcDirectClientStreamingCallable<>(methodDescriptor);
  }
}
