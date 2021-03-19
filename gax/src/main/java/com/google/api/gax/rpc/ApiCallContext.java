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
package com.google.api.gax.rpc;

import com.google.api.core.BetaApi;
import com.google.api.core.InternalExtensionOnly;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.RetryingContext;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.tracing.ApiTracer;
import com.google.auth.Credentials;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/**
 * Context for an API call.
 *
 * <p>An API call can be composed of many RPCs (in case of retries). This class contains settings
 * for both: API calls and RPCs.
 *
 * <p>Implementations need to be immutable because default instances are stored in callable objects.
 *
 * <p>This is transport specific and each transport has an implementation with its own options.
 */
@InternalExtensionOnly
public interface ApiCallContext extends RetryingContext {

  /** Returns a new ApiCallContext with the given credentials set. */
  ApiCallContext withCredentials(Credentials credentials);

  /** Returns a new ApiCallContext with the given channel set. */
  ApiCallContext withTransportChannel(TransportChannel channel);

  /**
   * Returns a new ApiCallContext with the given timeout set.
   *
   * <p>This sets the maximum amount of time a single unary RPC attempt can take. If retries are
   * enabled, then this can take much longer, as each RPC attempt will have the same constant
   * timeout. Unlike a deadline, timeouts are relative durations that are measure from the beginning
   * of each RPC attempt. Please note that this limits the duration of a server streaming RPC as
   * well.
   *
   * <p>If a method has default {@link com.google.api.gax.retrying.RetrySettings}, the max attempts
   * and/or total timeout is still respected when scheduling each RPC attempt.
   */
  ApiCallContext withTimeout(@Nullable Duration timeout);

  /** Returns the configured per-RPC timeout. */
  @Nullable
  Duration getTimeout();

  /**
   * Returns a new ApiCallContext with the given stream timeout set.
   *
   * <p>This timeout only applies to a {@link ServerStreamingCallable}s. It limits the maximum
   * amount of time that can pass between demand being signaled via {@link
   * StreamController#request(int)} and actual message delivery to {@link
   * ResponseObserver#onResponse(Object)}. Or, in the case of automatic flow control, since the last
   * message was delivered to {@link ResponseObserver#onResponse(Object)}. This is useful to detect
   * server or connection stalls. When the timeout has been reached, the stream will be closed with
   * a retryable {@link WatchdogTimeoutException} and a status of {@link StatusCode.Code#ABORTED}.
   *
   * <p>A value of {@link Duration#ZERO}, disables the streaming wait timeout and a null value will
   * use the default in the callable.
   *
   * <p>Please note that this timeout is best effort and the maximum resolution is configured in
   * {@link StubSettings#getStreamWatchdogCheckInterval()}.
   */
  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  ApiCallContext withStreamWaitTimeout(@Nullable Duration streamWaitTimeout);

  /**
   * Return the stream wait timeout set for this context.
   *
   * @see #withStreamWaitTimeout(Duration)
   */
  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  @Nullable
  Duration getStreamWaitTimeout();

  /**
   * Returns a new ApiCallContext with the given stream idle timeout set.
   *
   * <p>This timeout only applies to a {@link ServerStreamingCallable}s. It limits the maximum
   * amount of timeout that can pass between a message being received by {@link
   * ResponseObserver#onResponse(Object)} and demand being signaled via {@link
   * StreamController#request(int)}. Please note that this timeout is best effort and the maximum
   * resolution configured in {@link StubSettings#getStreamWatchdogCheckInterval()}. This is useful
   * to clean up streams that were partially read but never closed. When the timeout has been
   * reached, the stream will be closed with a nonretryable {@link WatchdogTimeoutException} and a
   * status of {@link StatusCode.Code#ABORTED}.
   *
   * <p>A value of {@link Duration#ZERO}, disables the streaming idle timeout and a null value will
   * use the default in the callable.
   *
   * <p>Please note that this timeout is best effort and the maximum resolution is configured in
   * {@link StubSettings#getStreamWatchdogCheckInterval()}.
   */
  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  ApiCallContext withStreamIdleTimeout(@Nullable Duration streamIdleTimeout);

  /**
   * The stream idle timeout set for this context.
   *
   * @see #withStreamIdleTimeout(Duration)
   */
  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  @Nullable
  Duration getStreamIdleTimeout();

  /**
   * The {@link ApiTracer} that was previously set for this context.
   *
   * <p>The {@link ApiTracer} will be used to trace the current operation and to annotate various
   * events like retries.
   */
  @BetaApi("The surface for tracing is not stable yet and may change in the future")
  @Nonnull
  ApiTracer getTracer();

  /**
   * Returns a new {@link ApiCallContext} with the given {@link ApiTracer}.
   *
   * <p>The {@link ApiTracer} will be used to trace the current operation and to annotate various
   * events like retries.
   *
   * @param tracer the {@link ApiTracer} to set.
   */
  @BetaApi("The surface for tracing is not stable yet and may change in the future")
  ApiCallContext withTracer(@Nonnull ApiTracer tracer);

  /**
   * Returns a new ApiCallContext with the given {@link RetrySettings} set.
   *
   * <p>This sets the {@link RetrySettings} to use for the RPC. These settings will work in
   * combination with either the default retryable codes for the RPC, or the retryable codes
   * supplied through {@link #withRetryableCodes(Set)}. Calling {@link
   * #withRetrySettings(RetrySettings)} on an RPC that does not include {@link
   * Code#DEADLINE_EXCEEDED} as one of its retryable codes (or without calling {@link
   * #withRetryableCodes(Set)} with a set that includes at least {@link Code#DEADLINE_EXCEEDED})
   * will effectively only set a single timeout that is equal to {@link
   * RetrySettings#getInitialRpcTimeout()}. If this timeout is exceeded, the RPC will not be retried
   * and will fail with {@link Code#DEADLINE_EXCEEDED}.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * ApiCallContext context = GrpcCallContext.createDefault()
   *   .withRetrySettings(RetrySettings.newBuilder()
   *     .setInitialRetryDelay(Duration.ofMillis(10L))
   *     .setInitialRpcTimeout(Duration.ofMillis(100L))
   *     .setMaxAttempts(10)
   *     .setMaxRetryDelay(Duration.ofSeconds(10L))
   *     .setMaxRpcTimeout(Duration.ofSeconds(30L))
   *     .setRetryDelayMultiplier(1.4)
   *     .setRpcTimeoutMultiplier(1.5)
   *     .setTotalTimeout(Duration.ofMinutes(10L))
   *     .build())
   *   .withRetryableCodes(Sets.newSet(
   *     StatusCode.Code.UNAVAILABLE,
   *     StatusCode.Code.DEADLINE_EXCEEDED));
   * }</pre>
   */
  @BetaApi
  ApiCallContext withRetrySettings(RetrySettings retrySettings);

  /**
   * Returns a new ApiCallContext with the given retryable codes set.
   *
   * <p>This sets the retryable codes to use for the RPC. These settings will work in combination
   * with either the default {@link RetrySettings} for the RPC, or the {@link RetrySettings}
   * supplied through {@link #withRetrySettings(RetrySettings)}.
   *
   * <p>Setting a non-empty set of retryable codes for an RPC that is not already retryable by
   * default, will not have any effect and the RPC will NOT be retried. This option can only be used
   * to change which codes are considered retryable for an RPC that already has at least one
   * retryable code in its default settings.
   */
  @BetaApi
  ApiCallContext withRetryableCodes(Set<StatusCode.Code> retryableCodes);

  /** If inputContext is not null, returns it; if it is null, returns the present instance. */
  ApiCallContext nullToSelf(ApiCallContext inputContext);

  /**
   * For any values in {@code inputCallContext} that are not null, override the corresponding values
   * in the present instance.
   */
  ApiCallContext merge(ApiCallContext inputCallContext);

  /** Return a new ApiCallContext with the extraHeaders merged into the present instance. */
  @BetaApi("The surface for extra headers is not stable yet and may change in the future.")
  ApiCallContext withExtraHeaders(Map<String, List<String>> extraHeaders);

  /** Return the extra headers set for this context. */
  @BetaApi("The surface for extra headers is not stable yet and may change in the future.")
  Map<String, List<String>> getExtraHeaders();
}
