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

import com.google.api.core.BetaApi;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;

/**
 * A settings class to configure a StreamingCallable for calls to a streaming API method.
 *
 * <p>Currently this class is used to create the StreamingCallable object based on the configured
 * MethodDescriptor from the gRPC method and the given channel.
 */
@BetaApi
public final class StreamingCallSettings<RequestT, ResponseT> {
  private final MethodDescriptor<RequestT, ResponseT> methodDescriptor;

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
    return new Builder<>(grpcMethodDescriptor);
  }

  private StreamingCallSettings(MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    this.methodDescriptor = methodDescriptor;
  }

  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
    return methodDescriptor;
  }

  /** Package-private */
  StreamingCallable<RequestT, ResponseT> createStreamingCallable(Channel channel) {
    ClientCallFactory<RequestT, ResponseT> clientCallFactory =
        new DescriptorClientCallFactory<>(methodDescriptor);
    StreamingCallable<RequestT, ResponseT> callable =
        new StreamingCallable<>(new DirectStreamingCallable<>(clientCallFactory), channel, this);
    return callable;
  }

  public static class Builder<RequestT, ResponseT> {
    private MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor;

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      this.grpcMethodDescriptor = grpcMethodDescriptor;
    }

    public Builder(StreamingCallSettings<RequestT, ResponseT> settings) {
      this.grpcMethodDescriptor = settings.getMethodDescriptor();
    }

    public StreamingCallSettings<RequestT, ResponseT> build() {
      return new StreamingCallSettings<>(grpcMethodDescriptor);
    }
  }
}
