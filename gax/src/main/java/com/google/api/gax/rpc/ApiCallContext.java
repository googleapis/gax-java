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
import com.google.auth.Credentials;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/**
 * Context for an API call.
 *
 * <p>Implementations need to be immutable because default instances are stored in callable objects.
 *
 * <p>This is transport specific and each transport has an implementation with its own options.
 */
@InternalExtensionOnly
public interface ApiCallContext {

  /** Returns a new ApiCallContext with the given credentials set. */
  ApiCallContext withCredentials(Credentials credentials);

  /** Returns a new ApiCallContext with the given channel set. */
  ApiCallContext withTransportChannel(TransportChannel channel);

  /**
   * Returns a new ApiCallContext with the given timeout set.
   *
   * <p>This timeout only applies to a single RPC call; if timeouts are configured, the overall time
   * taken will be much higher.
   */
  ApiCallContext withTimeout(Duration rpcTimeout);

  /**
   * Returns a new ApiCallContext with the given stream timeout set.
   *
   * <p>This timeout only applies to a {@link ServerStreamingCallable}s. It limits the maximum
   * amount of timeout that can pass between demand being signaled via {@link
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

  /** If inputContext is not null, returns it; if it is null, returns the present instance. */
  ApiCallContext nullToSelf(ApiCallContext inputContext);

  /**
   * For any values in {@code inputCallContext} that are not null, override the corresponding values
   * in the present instance.
   */
  ApiCallContext merge(ApiCallContext inputCallContext);
}
