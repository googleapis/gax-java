/*
 * Copyright 2017 Google LLC
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
package com.google.api.gax.httpjson;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * HttpJsonCallContext encapsulates context data used to make an http-json call.
 *
 * <p>HttpJsonCallContext is immutable in the sense that none of its methods modifies the
 * HttpJsonCallContext itself or the underlying data. Methods of the form {@code withX} return
 * copies of the object, but with one field changed. The immutability and thread safety of the
 * arguments solely depends on the arguments themselves.
 */
@BetaApi("Reference ApiCallContext instead - this class is likely to experience breaking changes")
public final class HttpJsonCallContext implements ApiCallContext {
  private final HttpJsonChannel channel;
  private final HttpJsonCallOptions callOptions;
  @Nullable private final Duration timeout;
  @Nullable private final Duration streamWaitTimeout;
  @Nullable private final Duration streamIdleTimeout;
  private final ImmutableMap<String, List<String>> extraHeaders;
  private final ApiCallContextOptions options;
  private final ApiTracer tracer;
  @Nullable private final RetrySettings retrySettings;
  @Nullable private final ImmutableSet<StatusCode.Code> retryableCodes;

  /** Returns an empty instance. */
  public static HttpJsonCallContext createDefault() {
    return new HttpJsonCallContext(
        null,
        HttpJsonCallOptions.newBuilder().build(),
        null,
        null,
        null,
        ImmutableMap.of(),
        ApiCallContextOptions.getDefaultOptions(),
        null,
        null,
        null);
  }

  public static HttpJsonCallContext of(HttpJsonChannel channel, HttpJsonCallOptions options) {
    return new HttpJsonCallContext(
        channel,
        options,
        null,
        null,
        null,
        ImmutableMap.of(),
        ApiCallContextOptions.getDefaultOptions(),
        null,
        null,
        null);
  }

  private HttpJsonCallContext(
      HttpJsonChannel channel,
      HttpJsonCallOptions callOptions,
      Duration timeout,
      Duration streamWaitTimeout,
      Duration streamIdleTimeout,
      ImmutableMap<String, List<String>> extraHeaders,
      ApiCallContextOptions options,
      ApiTracer tracer,
      RetrySettings defaultRetrySettings,
      Set<StatusCode.Code> defaultRetryableCodes) {
    this.channel = channel;
    this.callOptions = callOptions;
    this.timeout = timeout;
    this.streamWaitTimeout = streamWaitTimeout;
    this.streamIdleTimeout = streamIdleTimeout;
    this.extraHeaders = extraHeaders;
    this.options = options;
    this.tracer = tracer;
    this.retrySettings = defaultRetrySettings;
    this.retryableCodes =
        defaultRetryableCodes == null ? null : ImmutableSet.copyOf(defaultRetryableCodes);
  }

  /**
   * Returns inputContext cast to {@link HttpJsonCallContext}, or an empty {@link
   * HttpJsonCallContext} if inputContext is null.
   *
   * @param inputContext the {@link ApiCallContext} to cast if it is not null
   */
  @Override
  public HttpJsonCallContext nullToSelf(ApiCallContext inputContext) {
    HttpJsonCallContext httpJsonCallContext;
    if (inputContext == null) {
      httpJsonCallContext = this;
    } else {
      if (!(inputContext instanceof HttpJsonCallContext)) {
        throw new IllegalArgumentException(
            "context must be an instance of HttpJsonCallContext, but found "
                + inputContext.getClass().getName());
      }
      httpJsonCallContext = (HttpJsonCallContext) inputContext;
    }
    return httpJsonCallContext;
  }

  @Override
  public HttpJsonCallContext merge(ApiCallContext inputCallContext) {
    if (inputCallContext == null) {
      return this;
    }
    if (!(inputCallContext instanceof HttpJsonCallContext)) {
      throw new IllegalArgumentException(
          "context must be an instance of HttpJsonCallContext, but found "
              + inputCallContext.getClass().getName());
    }
    HttpJsonCallContext httpJsonCallContext = (HttpJsonCallContext) inputCallContext;

    HttpJsonChannel newChannel = httpJsonCallContext.channel;
    if (newChannel == null) {
      newChannel = this.channel;
    }

    // Do deep merge of callOptions
    HttpJsonCallOptions newCallOptions = callOptions.merge(httpJsonCallContext.callOptions);

    Duration newTimeout = httpJsonCallContext.timeout;
    if (newTimeout == null) {
      newTimeout = this.timeout;
    }

    Duration newStreamWaitTimeout = httpJsonCallContext.streamWaitTimeout;
    if (newStreamWaitTimeout == null) {
      newStreamWaitTimeout = streamWaitTimeout;
    }

    Duration newStreamIdleTimeout = httpJsonCallContext.streamIdleTimeout;
    if (newStreamIdleTimeout == null) {
      newStreamIdleTimeout = streamIdleTimeout;
    }

    ImmutableMap<String, List<String>> newExtraHeaders =
        Headers.mergeHeaders(extraHeaders, httpJsonCallContext.extraHeaders);

    ApiCallContextOptions newOptions = options.merge(httpJsonCallContext.options);

    ApiTracer newTracer = httpJsonCallContext.tracer;
    if (newTracer == null) {
      newTracer = this.tracer;
    }

    RetrySettings newRetrySettings = httpJsonCallContext.retrySettings;
    if (newRetrySettings == null) {
      newRetrySettings = this.retrySettings;
    }

    Set<StatusCode.Code> newRetryableCodes = httpJsonCallContext.retryableCodes;
    if (newRetryableCodes == null) {
      newRetryableCodes = this.retryableCodes;
    }

    return new HttpJsonCallContext(
        newChannel,
        newCallOptions,
        newTimeout,
        newStreamWaitTimeout,
        newStreamIdleTimeout,
        newExtraHeaders,
        newOptions,
        newTracer,
        newRetrySettings,
        newRetryableCodes);
  }

  @Override
  public HttpJsonCallContext withCredentials(Credentials newCredentials) {
    HttpJsonCallOptions.Builder builder =
        callOptions != null ? callOptions.toBuilder() : HttpJsonCallOptions.newBuilder();
    return withCallOptions(builder.setCredentials(newCredentials).build());
  }

  @Override
  public HttpJsonCallContext withTransportChannel(TransportChannel inputChannel) {
    Preconditions.checkNotNull(inputChannel);
    if (!(inputChannel instanceof HttpJsonTransportChannel)) {
      throw new IllegalArgumentException(
          "Expected HttpJsonTransportChannel, got " + inputChannel.getClass().getName());
    }
    HttpJsonTransportChannel transportChannel = (HttpJsonTransportChannel) inputChannel;
    return withChannel(transportChannel.getChannel());
  }

  @Override
  public HttpJsonCallContext withTimeout(Duration timeout) {
    // Default RetrySettings use 0 for RPC timeout. Treat that as disabled timeouts.
    if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
      timeout = null;
    }

    // Prevent expanding deadlines
    if (timeout != null && this.timeout != null && this.timeout.compareTo(timeout) <= 0) {
      return this;
    }

    return new HttpJsonCallContext(
        this.channel,
        this.callOptions,
        timeout,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.extraHeaders,
        this.options,
        this.tracer,
        this.retrySettings,
        this.retryableCodes);
  }

  @Nullable
  @Override
  public Duration getTimeout() {
    return timeout;
  }

  @Override
  public HttpJsonCallContext withStreamWaitTimeout(@Nullable Duration streamWaitTimeout) {
    if (streamWaitTimeout != null) {
      Preconditions.checkArgument(
          streamWaitTimeout.compareTo(Duration.ZERO) >= 0, "Invalid timeout: < 0 s");
    }

    return new HttpJsonCallContext(
        this.channel,
        this.callOptions,
        this.timeout,
        streamWaitTimeout,
        this.streamIdleTimeout,
        this.extraHeaders,
        this.options,
        this.tracer,
        this.retrySettings,
        this.retryableCodes);
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

  @Override
  public HttpJsonCallContext withStreamIdleTimeout(@Nullable Duration streamIdleTimeout) {
    if (streamIdleTimeout != null) {
      Preconditions.checkArgument(
          streamIdleTimeout.compareTo(Duration.ZERO) >= 0, "Invalid timeout: < 0 s");
    }

    return new HttpJsonCallContext(
        this.channel,
        this.callOptions,
        this.timeout,
        this.streamWaitTimeout,
        streamIdleTimeout,
        this.extraHeaders,
        this.options,
        this.tracer,
        this.retrySettings,
        this.retryableCodes);
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

  @BetaApi("The surface for extra headers is not stable yet and may change in the future.")
  @Override
  public ApiCallContext withExtraHeaders(Map<String, List<String>> extraHeaders) {
    Preconditions.checkNotNull(extraHeaders);
    ImmutableMap<String, List<String>> newExtraHeaders =
        Headers.mergeHeaders(this.extraHeaders, extraHeaders);
    return new HttpJsonCallContext(
        this.channel,
        this.callOptions,
        this.timeout,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        newExtraHeaders,
        this.options,
        this.tracer,
        this.retrySettings,
        this.retryableCodes);
  }

  @BetaApi("The surface for extra headers is not stable yet and may change in the future.")
  @Override
  public Map<String, List<String>> getExtraHeaders() {
    return extraHeaders;
  }

  /** {@inheritDoc} */
  @Override
  public <T> ApiCallContext withOption(Key<T> key, T value) {
    ApiCallContextOptions newOptions = options.withOption(key, value);
    return new HttpJsonCallContext(
        this.channel,
        this.callOptions,
        this.timeout,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.extraHeaders,
        newOptions,
        this.tracer,
        this.retrySettings,
        this.retryableCodes);
  }

  /** {@inheritDoc} */
  @Override
  public <T> T getOption(Key<T> key) {
    return options.getOption(key);
  }

  public HttpJsonChannel getChannel() {
    return channel;
  }

  public HttpJsonCallOptions getCallOptions() {
    return callOptions;
  }

  @Deprecated
  @Nullable
  public Instant getDeadline() {
    return getCallOptions() != null ? getCallOptions().getDeadline() : null;
  }

  @Deprecated
  @Nullable
  public Credentials getCredentials() {
    return getCallOptions() != null ? getCallOptions().getCredentials() : null;
  }

  @Override
  public RetrySettings getRetrySettings() {
    return retrySettings;
  }

  @Override
  public HttpJsonCallContext withRetrySettings(RetrySettings retrySettings) {
    return new HttpJsonCallContext(
        this.channel,
        this.callOptions,
        this.timeout,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.extraHeaders,
        this.options,
        this.tracer,
        retrySettings,
        this.retryableCodes);
  }

  @Override
  public Set<StatusCode.Code> getRetryableCodes() {
    return retryableCodes;
  }

  @Override
  public HttpJsonCallContext withRetryableCodes(Set<StatusCode.Code> retryableCodes) {
    return new HttpJsonCallContext(
        this.channel,
        this.callOptions,
        this.timeout,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.extraHeaders,
        this.options,
        this.tracer,
        this.retrySettings,
        retryableCodes);
  }

  public HttpJsonCallContext withChannel(HttpJsonChannel newChannel) {
    return new HttpJsonCallContext(
        newChannel,
        this.callOptions,
        this.timeout,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.extraHeaders,
        this.options,
        this.tracer,
        this.retrySettings,
        this.retryableCodes);
  }

  public HttpJsonCallContext withCallOptions(HttpJsonCallOptions newCallOptions) {
    return new HttpJsonCallContext(
        this.channel,
        newCallOptions,
        this.timeout,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.extraHeaders,
        this.options,
        this.tracer,
        this.retrySettings,
        this.retryableCodes);
  }

  @Deprecated
  public HttpJsonCallContext withDeadline(Instant newDeadline) {
    HttpJsonCallOptions.Builder builder =
        callOptions != null ? callOptions.toBuilder() : HttpJsonCallOptions.newBuilder();
    return withCallOptions(builder.setDeadline(newDeadline).build());
  }

  @Nonnull
  @Override
  public ApiTracer getTracer() {
    if (tracer == null) {
      return BaseApiTracer.getInstance();
    }
    return tracer;
  }

  /** {@inheritDoc} */
  @Override
  public HttpJsonCallContext withTracer(@Nonnull ApiTracer newTracer) {
    Preconditions.checkNotNull(newTracer);

    return new HttpJsonCallContext(
        this.channel,
        this.callOptions,
        this.timeout,
        this.streamWaitTimeout,
        this.streamIdleTimeout,
        this.extraHeaders,
        this.options,
        newTracer,
        this.retrySettings,
        this.retryableCodes);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    HttpJsonCallContext that = (HttpJsonCallContext) o;
    return Objects.equals(this.channel, that.channel)
        && Objects.equals(this.callOptions, that.callOptions)
        && Objects.equals(this.timeout, that.timeout)
        && Objects.equals(this.extraHeaders, that.extraHeaders)
        && Objects.equals(this.options, that.options)
        && Objects.equals(this.tracer, that.tracer)
        && Objects.equals(this.retrySettings, that.retrySettings)
        && Objects.equals(this.retryableCodes, that.retryableCodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        channel,
        callOptions,
        timeout,
        extraHeaders,
        options,
        tracer,
        retrySettings,
        retryableCodes);
  }
}
