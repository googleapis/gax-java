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
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.SimpleStreamResumptionStrategy;
import com.google.api.gax.retrying.StreamResumptionStrategy;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import java.util.Set;
import org.threeten.bp.Duration;

/**
 * A settings class to configure a {@link ServerStreamingCallable}.
 *
 * <p>This class includes settings that are applicable to all server streaming calls, which
 * currently just includes retries.
 *
 * <p>Retry configuration allows for the stream to be restarted and resumed. it is composed of 3
 * parts: the retryable codes, the retry settings and the stream resumption strategy. The retryable
 * codes indicate which codes cause a retry to occur, the retry settings configure the retry logic
 * when the retry needs to happen, and the stream resumption strategy composes the request to resume
 * the stream. To turn off retries, set the retryable codes to the empty set.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public final class ServerStreamingCallSettings<RequestT, ResponseT>
    extends StreamingCallSettings<RequestT, ResponseT> {

  private final Set<Code> retryableCodes;
  private final RetrySettings retrySettings;
  private final StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;

  private final Duration timeoutCheckInterval;
  private final Duration idleTimeout;

  private ServerStreamingCallSettings(Builder<RequestT, ResponseT> builder) {
    this.retryableCodes = ImmutableSet.copyOf(builder.retryableCodes);
    this.retrySettings = builder.retrySettings;
    this.resumptionStrategy = builder.resumptionStrategy;
    this.timeoutCheckInterval = builder.timeoutCheckInterval;
    this.idleTimeout = builder.idleTimeout;
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
   * retryableCodes do.
   */
  public Set<Code> getRetryableCodes() {
    return retryableCodes;
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
   * retrySettings do.
   */
  public RetrySettings getRetrySettings() {
    return retrySettings;
  }

  /**
   * See the class documentation of {@link ServerStreamingCallSettings} and {@link
   * StreamResumptionStrategy} for a description of what the StreamResumptionStrategy does.
   */
  public StreamResumptionStrategy<RequestT, ResponseT> getResumptionStrategy() {
    return resumptionStrategy;
  }

  public Duration getTimeoutCheckInterval() {
    return timeoutCheckInterval;
  }

  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<>();
  }

  public static class Builder<RequestT, ResponseT>
      extends StreamingCallSettings.Builder<RequestT, ResponseT> {
    private Set<StatusCode.Code> retryableCodes;
    private RetrySettings retrySettings;
    private StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;

    private Duration timeoutCheckInterval;
    private Duration idleTimeout;

    /** Initialize the builder with default settings */
    private Builder() {
      this.retryableCodes = ImmutableSet.of();
      this.retrySettings = RetrySettings.newBuilder().build();
      this.resumptionStrategy = new SimpleStreamResumptionStrategy<>();

      this.timeoutCheckInterval = Duration.ZERO;
      this.idleTimeout = Duration.ZERO;
    }

    private Builder(ServerStreamingCallSettings<RequestT, ResponseT> settings) {
      super(settings);
      this.retryableCodes = settings.retryableCodes;
      this.retrySettings = settings.retrySettings;
      this.resumptionStrategy = settings.resumptionStrategy;

      this.timeoutCheckInterval = settings.timeoutCheckInterval;
      this.idleTimeout = settings.idleTimeout;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * retryableCodes do.
     */
    public Builder<RequestT, ResponseT> setRetryableCodes(StatusCode.Code... codes) {
      this.setRetryableCodes(Sets.newHashSet(codes));
      return this;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * retryableCodes do.
     */
    public Builder<RequestT, ResponseT> setRetryableCodes(Set<Code> retryableCodes) {
      this.retryableCodes = Sets.newHashSet(retryableCodes);
      return this;
    }

    public Set<Code> getRetryableCodes() {
      return retryableCodes;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * retrySettings do.
     */
    public Builder<RequestT, ResponseT> setRetrySettings(RetrySettings retrySettings) {
      this.retrySettings = retrySettings;
      return this;
    }

    public RetrySettings getRetrySettings() {
      return retrySettings;
    }

    /** Disables retries and sets the RPC timeout. */
    public Builder<RequestT, ResponseT> setSimpleTimeoutNoRetries(Duration timeout) {
      setRetryableCodes();
      setRetrySettings(
          RetrySettings.newBuilder()
              .setTotalTimeout(timeout)
              .setInitialRetryDelay(Duration.ZERO)
              .setRetryDelayMultiplier(1)
              .setMaxRetryDelay(Duration.ZERO)
              .setInitialRpcTimeout(timeout)
              .setRpcTimeoutMultiplier(1)
              .setMaxRpcTimeout(timeout)
              .setMaxAttempts(1)
              .build());

      // enable watchdog
      Duration checkInterval = Ordering.natural().max(timeout.dividedBy(2), Duration.ofSeconds(10));
      setTimeoutCheckInterval(checkInterval);

      return this;
    }

    /**
     * See the class documentation of {@link ServerStreamingCallSettings} for a description of what
     * StreamResumptionStrategy does.
     */
    public Builder<RequestT, ResponseT> setResumptionStrategy(
        StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy) {
      this.resumptionStrategy = resumptionStrategy;
      return this;
    }

    public StreamResumptionStrategy<RequestT, ResponseT> getResumptionStrategy() {
      return resumptionStrategy;
    }

    public Duration getTimeoutCheckInterval() {
      return timeoutCheckInterval;
    }

    public Builder<RequestT, ResponseT> setTimeoutCheckInterval(Duration timeoutCheckInterval) {
      this.timeoutCheckInterval = timeoutCheckInterval;
      return this;
    }

    public Duration getIdleTimeout() {
      return idleTimeout;
    }

    public Builder<RequestT, ResponseT> setIdleTimeout(Duration idleTimeout) {
      this.idleTimeout = idleTimeout;
      return this;
    }

    @Override
    public ServerStreamingCallSettings<RequestT, ResponseT> build() {
      return new ServerStreamingCallSettings<>(this);
    }
  }
}
