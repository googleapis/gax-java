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
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.rpc.EmptyRequestParamsExtractor;
import com.google.api.gax.rpc.RequestParamsExtractor;
import com.google.api.gax.rpc.RequestUrlParamsEncoder;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StreamingCallSettings;
import com.google.api.gax.rpc.UnaryCallSettingsTyped;
import com.google.api.gax.rpc.UnaryCallable;

/** Class with utility methods to create grpc-based direct callables. */
@BetaApi
public class GrpcCallableFactory {

  private GrpcCallableFactory() {}

  /**
   * Create a callable object with grpc-specific functionality. Designed for use by generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createUnaryCallable(
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
      UnaryCallSettingsTyped<RequestT, ResponseT> callSettings,
      ClientContext clientContext) {
    return createUnaryCallable(
        grpcCallSettings, callSettings, clientContext, EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a callable object with grpc-specific functionality. Designed for use by generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   */
  @BetaApi
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createUnaryCallable(
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
      UnaryCallSettingsTyped<RequestT, ResponseT> callSettings,
      ClientContext clientContext,
      RequestParamsExtractor<RequestT> paramsExtractor) {
    UnaryCallable<RequestT, ResponseT> callable =
        new GrpcDirectCallable<>(
            grpcCallSettings.getMethodDescriptor(),
            new RequestUrlParamsEncoder<>(paramsExtractor, false));
    callable = new GrpcExceptionCallable<>(callable, callSettings.getRetryableCodes());
    return callable;
  }

  /**
   * Create a bidirectional streaming callable object with grpc-specific functionality. Designed for
   * use by generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   */
  @BetaApi
  public static <RequestT, ResponseT>
      BidiStreamingCallable<RequestT, ResponseT> createBidiStreamingCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
          ClientContext clientContext) {
    return new GrpcDirectBidiStreamingCallable<>(grpcCallSettings.getMethodDescriptor());
  }

  /**
   * Create a server-streaming callable with grpc-specific functionality. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   */
  @BetaApi
  public static <RequestT, ResponseT>
      ServerStreamingCallable<RequestT, ResponseT> createServerStreamingCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
          ClientContext clientContext) {
    return createServerStreamingCallable(
        grpcCallSettings,
        streamingCallSettings,
        clientContext,
        EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a server-streaming callable with grpc-specific functionality. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   */
  @BetaApi
  public static <RequestT, ResponseT>
      ServerStreamingCallable<RequestT, ResponseT> createServerStreamingCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
          ClientContext clientContext,
          RequestParamsExtractor<RequestT> paramsExtractor) {
    return new GrpcDirectServerStreamingCallable<>(
        grpcCallSettings.getMethodDescriptor(),
        new RequestUrlParamsEncoder<>(paramsExtractor, false));
  }

  /**
   * Create a client-streaming callable object with grpc-specific functionality. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   */
  @BetaApi
  public static <RequestT, ResponseT>
      ClientStreamingCallable<RequestT, ResponseT> createClientStreamingCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
          ClientContext clientContext) {
    return new GrpcDirectClientStreamingCallable<>(grpcCallSettings.getMethodDescriptor());
  }
}
