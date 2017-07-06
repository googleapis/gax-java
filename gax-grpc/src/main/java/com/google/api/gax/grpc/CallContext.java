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
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.EntryPointUnaryCallable;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.base.Preconditions;
import io.grpc.CallOptions;
import io.grpc.Channel;

/**
 * GrpcCallContext encapsulates context data used to make a grpc call.
 *
 * <p>GrpcCallContext is immutable in the sense that none of its methods modifies the
 * GrpcCallContext itself or the underlying data. Methods of the form {@code withX}, such as {@link
 * #withChannel}, return copies of the object, but with one field changed. The immutability and
 * thread safety of the arguments solely depends on the arguments themselves.
 */
@BetaApi
public final class GrpcCallContext implements ApiCallContext {
  private final Channel channel;
  private final CallOptions callOptions;

  private GrpcCallContext(Channel channel, CallOptions callOptions) {
    this.channel = channel;
    this.callOptions = Preconditions.checkNotNull(callOptions);
  }

  public static GrpcCallContext createDefault() {
    return new GrpcCallContext(null, CallOptions.DEFAULT);
  }

  public static GrpcCallContext of(Channel channel, CallOptions callOptions) {
    return new GrpcCallContext(channel, callOptions);
  }

  public Channel getChannel() {
    return channel;
  }

  public CallOptions getCallOptions() {
    return callOptions;
  }

  public GrpcCallContext withChannel(Channel channel) {
    return new GrpcCallContext(channel, this.callOptions);
  }

  public GrpcCallContext withCallOptions(CallOptions callOptions) {
    return new GrpcCallContext(this.channel, callOptions);
  }

  @Override
  public <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> newUnaryCallable(
      UnaryCallable<RequestT, ResponseT> unaryCallable) {
    return new EntryPointUnaryCallable<>(unaryCallable, this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    GrpcCallContext that = (GrpcCallContext) o;

    if (channel != null ? !channel.equals(that.channel) : that.channel != null) {
      return false;
    }
    return callOptions.equals(that.callOptions);
  }

  @Override
  public int hashCode() {
    int result = channel != null ? channel.hashCode() : 0;
    result = 31 * result + callOptions.hashCode();
    return result;
  }
}
