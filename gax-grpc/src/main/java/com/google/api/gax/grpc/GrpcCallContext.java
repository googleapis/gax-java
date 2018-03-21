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

import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.core.InternalExtensionOnly;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.api.gax.rpc.TransportChannel;
import com.google.auth.Credentials;
import com.google.common.base.Preconditions;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.auth.MoreCallCredentials;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/**
 * GrpcCallContext encapsulates context data used to make a grpc call.
 *
 * <p>GrpcCallContext is immutable in the sense that none of its methods modifies the
 * GrpcCallContext itself or the underlying data. Methods of the form {@code withX}, such as {@link
 * #withTransportChannel}, return copies of the object, but with one field changed. The immutability
 * and thread safety of the arguments solely depends on the arguments themselves.
 */
@BetaApi("Reference ApiCallContext instead - this class is likely to experience breaking changes")
@InternalExtensionOnly
public final class GrpcCallContext implements ApiCallContext {
  private final Channel channel;
  private final CallOptions callOptions;
  @Nullable private final Duration streamWaitTimeout;
  @Nullable private final Duration streamIdleTimeout;
  @Nullable private final Integer channelAffinity;

  /** Returns an empty instance with a null channel and default {@link CallOptions}. */
  public static GrpcCallContext createDefault() {
    return new GrpcCallContext(null, CallOptions.DEFAULT, null, null, null);
  }

  /** Returns an instance with the given channel and {@link CallOptions}. */
  public static GrpcCallContext of(Channel channel, CallOptions callOptions) {
    return new GrpcCallContext(channel, callOptions, null, null, null);
  }

  private GrpcCallContext(
      Channel channel,
      CallOptions callOptions,
      @Nullable Duration streamWaitTimeout,
      @Nullable Duration streamIdleTimeout,
      @Nullable Integer channelAffinity) {
    this.channel = channel;
    this.callOptions = Preconditions.checkNotNull(callOptions);
    this.streamWaitTimeout = streamWaitTimeout;
    this.streamIdleTimeout = streamIdleTimeout;
    this.channelAffinity = channelAffinity;
  }

  /**
   * Returns inputContext cast to {@link GrpcCallContext}, or an empty {@link GrpcCallContext} if
   * inputContext is null.
   *
   * @param inputContext the {@link ApiCallContext} to cast if it is not null
   */
  @Override
  public GrpcCallContext nullToSelf(ApiCallContext inputContext) {
    GrpcCallContext grpcCallContext;
    if (inputContext == null) {
      grpcCallContext = this;
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

  @Override
  public GrpcCallContext withCredentials(Credentials newCredentials) {
    Preconditions.checkNotNull(newCredentials);
    CallCredentials callCredentials = MoreCallCredentials.from(newCredentials);
    return withCallOptions(callOptions.withCallCredentials(callCredentials));
  }

  @Override
  public GrpcCallContext withTransportChannel(TransportChannel inputChannel) {
    Preconditions.checkNotNull(inputChannel);
    if (!(inputChannel instanceof GrpcTransportChannel)) {
      throw new IllegalArgumentException(
          "Expected GrpcTransportChannel, got " + inputChannel.getClass().getName());
    }
    GrpcTransportChannel transportChannel = (GrpcTransportChannel) inputChannel;
    return withChannel(transportChannel.getChannel());
  }

  @Override
  public GrpcCallContext withTimeout(Duration rpcTimeout) {
    if (rpcTimeout == null) {
      return withCallOptions(callOptions.withDeadline(null));
    } else if (rpcTimeout.isZero() || rpcTimeout.isNegative()) {
      throw new DeadlineExceededException(
          "Invalid timeout: <= 0 s", null, GrpcStatusCode.of(Status.Code.DEADLINE_EXCEEDED), false);
    } else {
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
  }

  @Override
  public GrpcCallContext withStreamWaitTimeout(@Nullable Duration streamWaitTimeout) {
    if (streamWaitTimeout != null) {
      Preconditions.checkArgument(
          streamWaitTimeout.compareTo(Duration.ZERO) >= 0, "Invalid timeout: < 0 s");
    }

    return new GrpcCallContext(
        channel, callOptions, streamWaitTimeout, streamIdleTimeout, channelAffinity);
  }

  @Override
  public GrpcCallContext withStreamIdleTimeout(@Nullable Duration streamIdleTimeout) {
    if (streamIdleTimeout != null) {
      Preconditions.checkArgument(
          streamIdleTimeout.compareTo(Duration.ZERO) >= 0, "Invalid timeout: < 0 s");
    }

    return new GrpcCallContext(
        channel, callOptions, streamWaitTimeout, streamIdleTimeout, channelAffinity);
  }

  public GrpcCallContext withChannelAffinity(@Nullable Integer affinity) {
    return new GrpcCallContext(
        channel, callOptions, streamWaitTimeout, streamIdleTimeout, affinity);
  }

  @Override
  public ApiCallContext merge(ApiCallContext inputCallContext) {
    if (inputCallContext == null) {
      return this;
    }
    if (!(inputCallContext instanceof GrpcCallContext)) {
      throw new IllegalArgumentException(
          "context must be an instance of GrpcCallContext, but found "
              + inputCallContext.getClass().getName());
    }
    GrpcCallContext grpcCallContext = (GrpcCallContext) inputCallContext;

    Channel newChannel = grpcCallContext.channel;
    if (newChannel == null) {
      newChannel = this.channel;
    }

    Deadline newDeadline = grpcCallContext.callOptions.getDeadline();
    if (newDeadline == null) {
      newDeadline = this.callOptions.getDeadline();
    }

    CallCredentials newCallCredentials = grpcCallContext.callOptions.getCredentials();
    if (newCallCredentials == null) {
      newCallCredentials = this.callOptions.getCredentials();
    }

    Duration newStreamWaitTimeout = grpcCallContext.streamWaitTimeout;
    if (newStreamWaitTimeout == null) {
      newStreamWaitTimeout = this.streamWaitTimeout;
    }

    Duration newStreamIdleTimeout = grpcCallContext.streamIdleTimeout;
    if (newStreamIdleTimeout == null) {
      newStreamIdleTimeout = this.streamIdleTimeout;
    }

    Integer newChannelAffinity = grpcCallContext.channelAffinity;
    if (newChannelAffinity == null) {
      newChannelAffinity = this.channelAffinity;
    }

    CallOptions newCallOptions =
        grpcCallContext
            .callOptions
            .withCallCredentials(newCallCredentials)
            .withDeadline(newDeadline);

    return new GrpcCallContext(
        newChannel, newCallOptions, newStreamWaitTimeout, newStreamIdleTimeout, newChannelAffinity);
  }

  /** The {@link Channel} set on this context. */
  public Channel getChannel() {
    return channel;
  }

  /** The {@link CallOptions} set on this context. */
  public CallOptions getCallOptions() {
    return callOptions;
  }

  /**
   * The stream wait timeout set for this context.
   *
   * @see ApiCallContext#withStreamWaitTimeout(Duration)
   */
  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  @Nullable
  public Duration getStreamWaitTimeout() {
    return streamWaitTimeout;
  }

  /**
   * The stream idle timeout set for this context.
   *
   * @see ApiCallContext#withStreamIdleTimeout(Duration)
   */
  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  @Nullable
  public Duration getStreamIdleTimeout() {
    return streamIdleTimeout;
  }

  /**
   * The channel affinity for this context.
   *
   * @see ApiCallContext#withStreamIdleTimeout(Duration)
   */
  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  @Nullable
  public Integer getChannelAffinity() {
    return channelAffinity;
  }

  /** Returns a new instance with the channel set to the given channel. */
  public GrpcCallContext withChannel(Channel newChannel) {
    return new GrpcCallContext(
        newChannel,
        this.callOptions,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.channelAffinity);
  }

  /** Returns a new instance with the call options set to the given call options. */
  public GrpcCallContext withCallOptions(CallOptions newCallOptions) {
    return new GrpcCallContext(
        this.channel,
        newCallOptions,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.channelAffinity);
  }

  public GrpcCallContext withRequestParamsDynamicHeaderOption(String requestParams) {
    CallOptions newCallOptions =
        CallOptionsUtil.putRequestParamsDynamicHeaderOption(callOptions, requestParams);

    return withCallOptions(newCallOptions);
  }

  @InternalApi("for testing")
  Deadline getDeadline() {
    return callOptions.getDeadline();
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
    return Objects.equals(channel, that.channel)
        && Objects.equals(callOptions, that.callOptions)
        && Objects.equals(streamWaitTimeout, that.streamWaitTimeout)
        && Objects.equals(streamIdleTimeout, that.streamIdleTimeout)
        && Objects.equals(channelAffinity, that.channelAffinity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        channel, callOptions, streamWaitTimeout, streamIdleTimeout, channelAffinity);
  }
}
