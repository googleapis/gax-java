/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

import autovalue.shaded.com.google.common.common.base.Preconditions;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

public class StreamingApiCallable<RequestT, ResponseT> {
  private final StreamingCallable<RequestT, ResponseT> callable;
  private ManagedChannel channel;

  public StreamingApiCallable(StreamingCallable<RequestT, ResponseT> callable) {
    this.callable = callable;
  }

  public void bind(ManagedChannel channel) {
    this.channel = channel;
  }

  /**
   * Create a callable object that represents a streaming API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param streamingCallSettings {@link com.google.api.gax.grpc.StreamingCallSettings} to configure
   *     the method-level settings with.
   * @param channel {@link ManagedChannel} to use to connect to the service.
   * @return {@link com.google.api.gax.grpc.StreamingApiCallable} callable object.
   */
  public static <RequestT, ResponseT> StreamingApiCallable<RequestT, ResponseT> create(
      StreamingCallSettings<RequestT, ResponseT> streamingCallSettings, ManagedChannel channel) {
    return streamingCallSettings.createStreamingApiCallable(channel);
  }

  /**
   * Conduct a bidirectional streaming call
   *
   * @param responseObserver {@link io.grpc.stub.StreamObserver} to observe the streaming responses
   * @return {@link StreamObserver} which is used for making streaming requests.
   */
  public StreamObserver<RequestT> bidiStreamingCall(StreamObserver<ResponseT> responseObserver) {
    Preconditions.checkNotNull(channel);
    return callable.bidiStreamingCall(
        CallContext.<RequestT, ResponseT>of(channel, responseObserver, null));
  }

  /**
   * Conduct a server streaming call
   *
   * @param responseObserver {@link io.grpc.stub.StreamObserver} to observe the streaming responses
   * @param request request
   */
  public void serverStreamingCall(StreamObserver<ResponseT> responseObserver, RequestT request) {
    Preconditions.checkNotNull(channel);
    callable.serverStreamingCall(
        CallContext.<RequestT, ResponseT>of(channel, responseObserver, request));
  }

  /**
   * Conduct a client streaming call
   *
   * @param responseObserver {@link io.grpc.stub.StreamObserver} to receive the non-streaming
   *     response.
   * @return {@link StreamObserver} which is used for making streaming requests.
   */
  public StreamObserver<RequestT> clientStreamingCall(StreamObserver<ResponseT> responseObserver) {
    Preconditions.checkNotNull(channel);
    return callable.clientStreamingCall(
        CallContext.<RequestT, ResponseT>of(channel, responseObserver, null));
  }
}
