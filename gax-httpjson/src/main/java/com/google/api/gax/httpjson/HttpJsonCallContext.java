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
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.internal.Headers;
import com.google.api.gax.tracing.ApiTracer;
import com.google.api.gax.tracing.NoopApiTracer;
import com.google.auth.Credentials;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
@BetaApi
public final class HttpJsonCallContext implements ApiCallContext {
  private final HttpJsonChannel channel;
  private final Duration timeout;
  private final Instant deadline;
  private final Credentials credentials;
  private final ImmutableMap<String, List<String>> extraHeaders;
  private final ApiTracer tracer;

  /** Returns an empty instance. */
  public static HttpJsonCallContext createDefault() {
    return new HttpJsonCallContext(
        null, null, null, null, ImmutableMap.<String, List<String>>of(), null);
  }

  private HttpJsonCallContext(
      HttpJsonChannel channel,
      Duration timeout,
      Instant deadline,
      Credentials credentials,
      ImmutableMap<String, List<String>> extraHeaders,
      ApiTracer tracer) {
    this.channel = channel;
    this.timeout = timeout;
    this.deadline = deadline;
    this.credentials = credentials;
    this.extraHeaders = extraHeaders;
    this.tracer = tracer;
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

    Duration newTimeout = httpJsonCallContext.timeout;
    if (newTimeout == null) {
      newTimeout = this.timeout;
    }

    Instant newDeadline = httpJsonCallContext.deadline;
    if (newDeadline == null) {
      newDeadline = this.deadline;
    }

    Credentials newCredentials = httpJsonCallContext.credentials;
    if (newCredentials == null) {
      newCredentials = this.credentials;
    }

    ImmutableMap<String, List<String>> newExtraHeaders =
        Headers.mergeHeaders(extraHeaders, httpJsonCallContext.extraHeaders);

    ApiTracer newTracer = httpJsonCallContext.tracer;
    if (newTracer == null) {
      newTracer = this.tracer;
    }

    return new HttpJsonCallContext(
        newChannel, newTimeout, newDeadline, newCredentials, newExtraHeaders, newTracer);
  }

  @Override
  public HttpJsonCallContext withCredentials(Credentials newCredentials) {
    return new HttpJsonCallContext(
        this.channel, this.timeout, this.deadline, newCredentials, this.extraHeaders, this.tracer);
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
        this.channel, timeout, this.deadline, this.credentials, this.extraHeaders, this.tracer);
  }

  @Nullable
  @Override
  public Duration getTimeout() {
    return timeout;
  }

  @Override
  public ApiCallContext withStreamWaitTimeout(@Nonnull Duration streamWaitTimeout) {
    throw new UnsupportedOperationException("Http/json transport does not support streaming");
  }

  @Nullable
  @Override
  public Duration getStreamWaitTimeout() {
    throw new UnsupportedOperationException("Http/json transport does not support streaming");
  }

  @Override
  public ApiCallContext withStreamIdleTimeout(@Nonnull Duration streamIdleTimeout) {
    throw new UnsupportedOperationException("Http/json transport does not support streaming");
  }

  @Nullable
  @Override
  public Duration getStreamIdleTimeout() {
    throw new UnsupportedOperationException("Http/json transport does not support streaming");
  }

  @BetaApi("The surface for extra headers is not stable yet and may change in the future.")
  @Override
  public ApiCallContext withExtraHeaders(Map<String, List<String>> extraHeaders) {
    Preconditions.checkNotNull(extraHeaders);
    ImmutableMap<String, List<String>> newExtraHeaders =
        Headers.mergeHeaders(this.extraHeaders, extraHeaders);
    return new HttpJsonCallContext(
        channel, timeout, deadline, credentials, newExtraHeaders, this.tracer);
  }

  @BetaApi("The surface for extra headers is not stable yet and may change in the future.")
  @Override
  public Map<String, List<String>> getExtraHeaders() {
    return this.extraHeaders;
  }

  public HttpJsonChannel getChannel() {
    return channel;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public HttpJsonCallContext withChannel(HttpJsonChannel newChannel) {
    return new HttpJsonCallContext(
        newChannel, timeout, deadline, credentials, extraHeaders, this.tracer);
  }

  public HttpJsonCallContext withDeadline(Instant newDeadline) {
    return new HttpJsonCallContext(
        channel, timeout, newDeadline, credentials, extraHeaders, this.tracer);
  }

  @Nonnull
  @Override
  public ApiTracer getTracer() {
    if (tracer == null) {
      return NoopApiTracer.getInstance();
    }
    return tracer;
  }

  /** {@inheritDoc} */
  @Override
  public HttpJsonCallContext withTracer(@Nonnull ApiTracer newTracer) {
    Preconditions.checkNotNull(newTracer);

    return new HttpJsonCallContext(
        channel, timeout, deadline, credentials, extraHeaders, newTracer);
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
    return Objects.equals(channel, that.channel)
        && Objects.equals(timeout, that.timeout)
        && Objects.equals(deadline, that.deadline)
        && Objects.equals(credentials, that.credentials)
        && Objects.equals(extraHeaders, that.extraHeaders)
        && Objects.equals(tracer, that.tracer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channel, timeout, deadline, credentials, extraHeaders, tracer);
  }
}
