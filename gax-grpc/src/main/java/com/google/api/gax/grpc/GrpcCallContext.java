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
import com.google.common.base.Preconditions;
import io.grpc.CallOptions;
import io.grpc.Channel;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

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

  /**
   * Returns inputContext cast to {@link GrpcCallContext}, or an empty {@link GrpcCallContext} if
   * inputContext is null.
   *
   * @param inputContext the {@link ApiCallContext} to cast if it is not null
   */
  public static GrpcCallContext getAsGrpcCallContextWithDefault(ApiCallContext inputContext) {
    GrpcCallContext grpcCallContext;
    if (inputContext == null) {
      grpcCallContext = GrpcCallContext.createDefault();
    } else {
      if (!(inputContext instanceof GrpcCallContext)) {
        throw new IllegalArgumentException(
            "context must be an instance of GrpcCallContext, but found "
                + inputContext.getClass().getName());
      }
      grpcCallContext = (GrpcCallContext) inputContext;
    }
    return grpcCallContext;
  }

  private GrpcCallContext(Channel channel, CallOptions callOptions) {
    this.channel = channel;
    this.callOptions = Preconditions.checkNotNull(callOptions);
  }

  @Override
  public GrpcCallContext withTimeout(Duration rpcTimeout) {
    CallOptions oldOptions = callOptions;
    CallOptions newOptions =
        oldOptions.withDeadlineAfter(rpcTimeout.toMillis(), TimeUnit.MILLISECONDS);
    GrpcCallContext nextContext = withCallOptions(newOptions);

    if (oldOptions.getDeadline() == null) {
      return nextContext;
    }
    if (oldOptions.getDeadline().isBefore(newOptions.getDeadline())) {
      return this;
    }
    return nextContext;
  }

  /** Returns an empty instance with a null channel and default {@link CallOptions}. */
  public static GrpcCallContext createDefault() {
    return new GrpcCallContext(null, CallOptions.DEFAULT);
  }

  /** Returns an instance with the given channel and {@link CallOptions}. */
  public static GrpcCallContext of(Channel channel, CallOptions callOptions) {
    return new GrpcCallContext(channel, callOptions);
  }

  /** The {@link Channel} set on this context. */
  public Channel getChannel() {
    return channel;
  }

  /** The {@link CallOptions} set on this context. */
  public CallOptions getCallOptions() {
    return callOptions;
  }

  /** Returns a new instance with the channel set to the given channel. */
  public GrpcCallContext withChannel(Channel channel) {
    return new GrpcCallContext(channel, this.callOptions);
  }

  /** Returns a new instance with the call options set to the given call options. */
  public GrpcCallContext withCallOptions(CallOptions callOptions) {
    return new GrpcCallContext(this.channel, callOptions);
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
    return Objects.equals(channel, that.channel) && Objects.equals(callOptions, that.callOptions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channel, callOptions);
  }
}
