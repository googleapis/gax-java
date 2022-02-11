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
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.internal.ApiCallContextOptions;
import com.google.api.gax.rpc.internal.Headers;
import com.google.api.gax.tracing.ApiTracer;
import com.google.api.gax.tracing.BaseApiTracer;
import com.google.auth.Credentials;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.auth.MoreCallCredentials;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
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
public final class GrpcCallContext implements ApiCallContext {
  static final CallOptions.Key<ApiTracer> TRACER_KEY = CallOptions.Key.create("gax.tracer");

  private final Channel channel;
  private final CallOptions callOptions;
  @Nullable private final Duration timeout;
  @Nullable private final Duration streamWaitTimeout;
  @Nullable private final Duration streamIdleTimeout;
  @Nullable private final Integer channelAffinity;
  @Nullable private final RetrySettings retrySettings;
  @Nullable private final ImmutableSet<StatusCode.Code> retryableCodes;
  private final ImmutableMap<String, List<String>> extraHeaders;
  private final ApiCallContextOptions options;

  /** Returns an empty instance with a null channel and default {@link CallOptions}. */
  public static GrpcCallContext createDefault() {
    return new GrpcCallContext(
        null,
        CallOptions.DEFAULT,
        null,
        null,
        null,
        null,
        ImmutableMap.<String, List<String>>of(),
        ApiCallContextOptions.getDefaultOptions(),
        null,
        null);
  }

  /** Returns an instance with the given channel and {@link CallOptions}. */
  public static GrpcCallContext of(Channel channel, CallOptions callOptions) {
    return new GrpcCallContext(
        channel,
        callOptions,
        null,
        null,
        null,
        null,
        ImmutableMap.<String, List<String>>of(),
        ApiCallContextOptions.getDefaultOptions(),
        null,
        null);
  }

  private GrpcCallContext(
      Channel channel,
      CallOptions callOptions,
      @Nullable Duration timeout,
      @Nullable Duration streamWaitTimeout,
      @Nullable Duration streamIdleTimeout,
      @Nullable Integer channelAffinity,
      ImmutableMap<String, List<String>> extraHeaders,
      ApiCallContextOptions options,
      @Nullable RetrySettings retrySettings,
      @Nullable Set<StatusCode.Code> retryableCodes) {
    this.channel = channel;
    this.callOptions = Preconditions.checkNotNull(callOptions);
    this.timeout = timeout;
    this.streamWaitTimeout = streamWaitTimeout;
    this.streamIdleTimeout = streamIdleTimeout;
    this.channelAffinity = channelAffinity;
    this.extraHeaders = Preconditions.checkNotNull(extraHeaders);
    this.options = Preconditions.checkNotNull(options);
    this.retrySettings = retrySettings;
    this.retryableCodes = retryableCodes == null ? null : ImmutableSet.copyOf(retryableCodes);
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
  public GrpcCallContext withTimeout(@Nullable Duration timeout) {
    // Default RetrySettings use 0 for RPC timeout. Treat that as disabled timeouts.
    if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
      timeout = null;
    }

    // Prevent expanding timeouts
    if (timeout != null && this.timeout != null && this.timeout.compareTo(timeout) <= 0) {
      return this;
    }

    return new GrpcCallContext(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
  }

  @Nullable
  @Override
  public Duration getTimeout() {
    return timeout;
  }

  @Override
  public GrpcCallContext withStreamWaitTimeout(@Nullable Duration streamWaitTimeout) {
    if (streamWaitTimeout != null) {
      Preconditions.checkArgument(
          streamWaitTimeout.compareTo(Duration.ZERO) >= 0, "Invalid timeout: < 0 s");
    }

    return new GrpcCallContext(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
  }

  @Override
  public GrpcCallContext withStreamIdleTimeout(@Nullable Duration streamIdleTimeout) {
    if (streamIdleTimeout != null) {
      Preconditions.checkArgument(
          streamIdleTimeout.compareTo(Duration.ZERO) >= 0, "Invalid timeout: < 0 s");
    }

    return new GrpcCallContext(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
  }

  @BetaApi("The surface for channel affinity is not stable yet and may change in the future.")
  public GrpcCallContext withChannelAffinity(@Nullable Integer affinity) {
    return new GrpcCallContext(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        affinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
  }

  @BetaApi("The surface for extra headers is not stable yet and may change in the future.")
  @Override
  public GrpcCallContext withExtraHeaders(Map<String, List<String>> extraHeaders) {
    Preconditions.checkNotNull(extraHeaders);
    ImmutableMap<String, List<String>> newExtraHeaders =
        Headers.mergeHeaders(this.extraHeaders, extraHeaders);
    return new GrpcCallContext(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        newExtraHeaders,
        options,
        retrySettings,
        retryableCodes);
  }

  @Override
  public RetrySettings getRetrySettings() {
    return retrySettings;
  }

  @Override
  public GrpcCallContext withRetrySettings(RetrySettings retrySettings) {
    return new GrpcCallContext(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
  }

  @Override
  public Set<StatusCode.Code> getRetryableCodes() {
    return retryableCodes;
  }

  @Override
  public GrpcCallContext withRetryableCodes(Set<StatusCode.Code> retryableCodes) {
    return new GrpcCallContext(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
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
      newChannel = channel;
    }

    Deadline newDeadline = grpcCallContext.callOptions.getDeadline();
    if (newDeadline == null) {
      newDeadline = callOptions.getDeadline();
    }

    CallCredentials newCallCredentials = grpcCallContext.callOptions.getCredentials();
    if (newCallCredentials == null) {
      newCallCredentials = callOptions.getCredentials();
    }

    ApiTracer newTracer = grpcCallContext.callOptions.getOption(TRACER_KEY);
    if (newTracer == null) {
      newTracer = callOptions.getOption(TRACER_KEY);
    }

    Duration newTimeout = grpcCallContext.timeout;
    if (newTimeout == null) {
      newTimeout = timeout;
    }

    Duration newStreamWaitTimeout = grpcCallContext.streamWaitTimeout;
    if (newStreamWaitTimeout == null) {
      newStreamWaitTimeout = streamWaitTimeout;
    }

    Duration newStreamIdleTimeout = grpcCallContext.streamIdleTimeout;
    if (newStreamIdleTimeout == null) {
      newStreamIdleTimeout = streamIdleTimeout;
    }

    Integer newChannelAffinity = grpcCallContext.channelAffinity;
    if (newChannelAffinity == null) {
      newChannelAffinity = channelAffinity;
    }

    RetrySettings newRetrySettings = grpcCallContext.retrySettings;
    if (newRetrySettings == null) {
      newRetrySettings = retrySettings;
    }

    Set<StatusCode.Code> newRetryableCodes = grpcCallContext.retryableCodes;
    if (newRetryableCodes == null) {
      newRetryableCodes = retryableCodes;
    }

    ImmutableMap<String, List<String>> newExtraHeaders =
        Headers.mergeHeaders(extraHeaders, grpcCallContext.extraHeaders);

    ApiCallContextOptions newOptions = options.merge(grpcCallContext.options);

    CallOptions newCallOptions =
        grpcCallContext
            .callOptions
            .withCallCredentials(newCallCredentials)
            .withDeadline(newDeadline);

    if (newTracer != null) {
      newCallOptions = newCallOptions.withOption(TRACER_KEY, newTracer);
    }

    return new GrpcCallContext(
        newChannel,
        newCallOptions,
        newTimeout,
        newStreamWaitTimeout,
        newStreamIdleTimeout,
        newChannelAffinity,
        newExtraHeaders,
        newOptions,
        newRetrySettings,
        newRetryableCodes);
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
  @Override
  @Nullable
  public Duration getStreamWaitTimeout() {
    return streamWaitTimeout;
  }

  /**
   * The stream idle timeout set for this context.
   *
   * @see ApiCallContext#withStreamIdleTimeout(Duration)
   */
  @Override
  @Nullable
  public Duration getStreamIdleTimeout() {
    return streamIdleTimeout;
  }

  /** The channel affinity for this context. */
  @Nullable
  public Integer getChannelAffinity() {
    return channelAffinity;
  }

  /** The extra header for this context. */
  @BetaApi("The surface for extra headers is not stable yet and may change in the future.")
  @Override
  public Map<String, List<String>> getExtraHeaders() {
    return extraHeaders;
  }

  /** Returns a new instance with the channel set to the given channel. */
  public GrpcCallContext withChannel(Channel newChannel) {
    return new GrpcCallContext(
        newChannel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
  }

  /** Returns a new instance with the call options set to the given call options. */
  public GrpcCallContext withCallOptions(CallOptions newCallOptions) {
    return new GrpcCallContext(
        channel,
        newCallOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
  }

  public GrpcCallContext withRequestParamsDynamicHeaderOption(String requestParams) {
    CallOptions newCallOptions =
        CallOptionsUtil.putRequestParamsDynamicHeaderOption(callOptions, requestParams);

    return withCallOptions(newCallOptions);
  }

  /** {@inheritDoc} */
  @Override
  @Nonnull
  public ApiTracer getTracer() {
    ApiTracer tracer = callOptions.getOption(TRACER_KEY);
    if (tracer == null) {
      tracer = BaseApiTracer.getInstance();
    }
    return tracer;
  }

  /** {@inheritDoc} */
  @Override
  public GrpcCallContext withTracer(@Nonnull ApiTracer tracer) {
    Preconditions.checkNotNull(tracer);
    return withCallOptions(callOptions.withOption(TRACER_KEY, tracer));
  }

  /** {@inheritDoc} */
  @Override
  public <T> GrpcCallContext withOption(Key<T> key, T value) {
    ApiCallContextOptions newOptions = options.withOption(key, value);
    return new GrpcCallContext(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        newOptions,
        retrySettings,
        retryableCodes);
  }

  /** {@inheritDoc} */
  @Override
  public <T> T getOption(Key<T> key) {
    return options.getOption(key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        channel,
        callOptions,
        timeout,
        streamWaitTimeout,
        streamIdleTimeout,
        channelAffinity,
        extraHeaders,
        options,
        retrySettings,
        retryableCodes);
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
        && Objects.equals(timeout, that.timeout)
        && Objects.equals(streamWaitTimeout, that.streamWaitTimeout)
        && Objects.equals(streamIdleTimeout, that.streamIdleTimeout)
        && Objects.equals(channelAffinity, that.channelAffinity)
        && Objects.equals(extraHeaders, that.extraHeaders)
        && Objects.equals(options, that.options)
        && Objects.equals(retrySettings, that.retrySettings)
        && Objects.equals(retryableCodes, that.retryableCodes);
  }

  Metadata getMetadata() {
    Metadata metadata = new Metadata();
    for (Map.Entry<String, List<String>> header : extraHeaders.entrySet()) {
      String headerKey = header.getKey();
      for (String headerValue : header.getValue()) {
        metadata.put(Metadata.Key.of(headerKey, Metadata.ASCII_STRING_MARSHALLER), headerValue);
      }
    }
    return metadata;
  }
}
