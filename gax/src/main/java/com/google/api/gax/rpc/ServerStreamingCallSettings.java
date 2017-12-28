/*
 * Copyright 2017, Google LLC All rights reserved.
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
import com.google.api.gax.retrying.RestartingStreamingTrackerFactory;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.StreamTracker;
import com.google.api.gax.retrying.StreamTracker.Factory;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.threeten.bp.Duration;

/**
 * A settings class to configure a {@link ServerStreamingCallable}.
 *
 * <p>This class includes settings that are applicable to all server streaming calls, which
 * currently is watchdog settings and retries.
 *
 * <p>Watchdog configuration prevents server streams from getting stale in case the caller forgets
 * to close the stream or if the stream was reset but GRPC was not properly notified. There are 3
 * settings:
 *
 * <ul>
 *   <li>waitTimeout: how long to wait for a server response
 *   <li>idleTimeout: how long to wait for a client to request the next response
 *   <li>checkInterval: how often to check that active streams have not passed those thresholds.
 * </ul>
 *
 * <p>Retry configuration allows for the stream to be restarted and resumed. it is composed of 3
 * parts: the retryable codes, the retry settings and the stream tracker. The retryable codes
 * indicate which codes cause a retry to occur, the retry settings configure the retry logic when
 * the retry needs to happen and the stream tracker composes the request to resume the stream. To
 * turn off retries, set the retryable codes needs to be set to the empty set.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public final class ServerStreamingCallSettings<RequestT, ResponseT>
    extends StreamingCallSettings<RequestT, ResponseT> {
  private final Duration checkInterval;
  private final Duration waitTimeout;
  private final Duration idleTimeout;

  private final StreamTracker.Factory<RequestT, ResponseT> streamTrackerFactory;
  private final RetrySettings retrySettings;
  private final Set<StatusCode.Code> retryableCodes;

  private ServerStreamingCallSettings(Builder<RequestT, ResponseT> builder) {
    this.checkInterval = builder.checkInterval;
    this.waitTimeout = builder.waitTimeout;
    this.idleTimeout = builder.idleTimeout;

    this.streamTrackerFactory = builder.streamTrackerFactory;
    this.retrySettings = builder.retrySettings;
    this.retryableCodes = ImmutableSet.copyOf(builder.retryableCodes);
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
   * checkInterval does.
   */
  public Duration getCheckInterval() {
    return checkInterval;
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
   * waitTimeout does.
   */
  public Duration getWaitTimeout() {
    return waitTimeout;
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
   * idleTimeout does.
   */
  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} and {@link StreamTracker}
   * for a description of what streamTrackerFactory does.
   */
  public StreamTracker.Factory<RequestT, ResponseT> getStreamTrackerFactory() {
    return streamTrackerFactory;
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
   * retrySettings do.
   */
  public RetrySettings getRetrySettings() {
    return retrySettings;
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
   * retryableCodes do.
   */
  public Set<Code> getRetryableCodes() {
    return retryableCodes;
  }

  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<>();
  }

  public static class Builder<RequestT, ResponseT>
      extends StreamingCallSettings.Builder<RequestT, ResponseT> {
    private Duration checkInterval;
    private Duration waitTimeout;
    private Duration idleTimeout;
    private RetrySettings retrySettings;
    private StreamTracker.Factory<RequestT, ResponseT> streamTrackerFactory;
    private Set<StatusCode.Code> retryableCodes;

    private Builder() {
      this.checkInterval = Duration.ZERO;
      this.waitTimeout = Duration.ofDays(1);
      this.idleTimeout = Duration.ofDays(1);
      this.retrySettings =
          RetrySettings.newBuilder()
              // Disable retries by default
              .setMaxAttempts(1)
              // RPC timeouts are ignored
              .setInitialRpcTimeout(Duration.ofMillis(Long.MAX_VALUE))
              .setRpcTimeoutMultiplier(1)
              .setMaxRpcTimeout(Duration.ofMillis(Long.MAX_VALUE))
              .build();
      this.streamTrackerFactory = new RestartingStreamingTrackerFactory<>();
      this.retryableCodes = ImmutableSet.of();
    }

    private Builder(ServerStreamingCallSettings<RequestT, ResponseT> settings) {
      super(settings);
      this.checkInterval = settings.checkInterval;
      this.waitTimeout = settings.waitTimeout;
      this.idleTimeout = settings.idleTimeout;
      this.retrySettings = settings.retrySettings;
      this.streamTrackerFactory = settings.streamTrackerFactory;
      this.retryableCodes = settings.retryableCodes;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * checkInterval does.
     */
    public Builder<RequestT, ResponseT> setCheckInterval(Duration checkInterval) {
      this.checkInterval = checkInterval;
      return this;
    }

    public Duration getCheckInterval() {
      return checkInterval;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * waitTimeout does.
     */
    public Builder<RequestT, ResponseT> setWaitTimeout(Duration waitTimeout) {
      this.waitTimeout = waitTimeout;
      return this;
    }

    public Duration getWaitTimeout() {
      return waitTimeout;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * idleTimeout does.
     */
    public Builder<RequestT, ResponseT> setIdleTimeout(Duration idleTimeout) {
      this.idleTimeout = idleTimeout;
      return this;
    }

    public Duration getIdleTimeout() {
      return idleTimeout;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * retrySettings do.
     */
    public void setRetrySettings(RetrySettings retrySettings) {
      this.retrySettings = retrySettings;
    }

    public RetrySettings getRetrySettings() {
      return retrySettings;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * streamTrackerFactory does.
     */
    public Builder<RequestT, ResponseT> setStreamTrackerFactory(
        Factory<RequestT, ResponseT> streamTrackerFactory) {
      this.streamTrackerFactory = streamTrackerFactory;
      return this;
    }

    public StreamTracker.Factory<RequestT, ResponseT> getStreamTrackerFactory() {
      return streamTrackerFactory;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * retryableCodes do.
     */
    public Builder<RequestT, ResponseT> setRetryableCodes(Set<Code> retryableCodes) {
      this.retryableCodes = retryableCodes;
      return this;
    }

    public Set<Code> getRetryableCodes() {
      return retryableCodes;
    }

    @Override
    public ServerStreamingCallSettings<RequestT, ResponseT> build() {
      return new ServerStreamingCallSettings<>(this);
    }
  }
}
