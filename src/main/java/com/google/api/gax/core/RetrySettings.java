/*
 * Copyright 2015, Google Inc.
 * All rights reserved.
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

package com.google.api.gax.core;

import com.google.auto.value.AutoValue;

import org.joda.time.Duration;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Holds the parameters for retry and timeout logic with exponential backoff. Actual
 * implementation of the logic is elsewhere.
 *
 * The intent of these settings is to be used with a call to a remote server, which
 * could either fail (and return an error code) or not respond (and cause a timeout).
 * When there is a failure or timeout, the logic should keep trying until the total
 * timeout has passed.
 *
 * The "total timeout" parameter has ultimate control over how long the logic should
 * keep trying the remote call until it gives up completely. The higher the total
 * timeout, the more retries can be attempted. The other settings are considered
 * more advanced.
 *
 * Retry delay and timeout start at specific values, and are tracked separately from
 * each other. The very first call (before any retries) will use the initial timeout.
 *
 * If the last remote call is a failure, then the retrier will wait for the current
 * retry delay before attempting another call, and then the retry delay will be
 * multiplied by the retry delay multiplier for the next failure. The timeout will
 * not be affected, except in the case where the timeout would result in a deadline
 * past the total timeout; in that circumstance, a new timeout value is computed
 * which will terminate the call when the total time is up.
 *
 * If the last remote call is a timeout, then the retrier will compute a new timeout
 * and make another call. The new timeout is computed by multiplying the current
 * timeout by the timeout multiplier, but if that results in a deadline after the
 * total timeout, then a new timeout value is computed which will terminate the call
 * when the total time is up.
 */
@AutoValue
public abstract class RetrySettings {

  public abstract Duration getTotalTimeout();

  public abstract Duration getInitialRetryDelay();
  public abstract double getRetryDelayMultiplier();
  public abstract Duration getMaxRetryDelay();

  public abstract Duration getInitialRpcTimeout();
  public abstract double getRpcTimeoutMultiplier();
  public abstract Duration getMaxRpcTimeout();

  public static Builder newBuilder() {
    return new AutoValue_RetrySettings.Builder();
  }

  public Builder toBuilder() {
    return new AutoValue_RetrySettings.Builder(this);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setInitialRetryDelay(Duration initialDelay);
    public abstract Builder setRetryDelayMultiplier(double multiplier);
    public abstract Builder setMaxRetryDelay(Duration maxDelay);

    public abstract Builder setInitialRpcTimeout(Duration initialTimeout);
    public abstract Builder setRpcTimeoutMultiplier(double multiplier);
    public abstract Builder setMaxRpcTimeout(Duration maxTimeout);

    public abstract Builder setTotalTimeout(Duration totalTimeout);

    public abstract Duration getInitialRetryDelay();
    public abstract double getRetryDelayMultiplier();
    public abstract Duration getMaxRetryDelay();

    public abstract Duration getInitialRpcTimeout();
    public abstract double getRpcTimeoutMultiplier();
    public abstract Duration getMaxRpcTimeout();

    public abstract Duration getTotalTimeout();

    abstract RetrySettings autoBuild();

    public RetrySettings build() {
      RetrySettings params = autoBuild();
      if (params.getTotalTimeout().getMillis() < 0) {
        throw new IllegalStateException("total timeout must not be negative");
      }
      if (params.getInitialRetryDelay().getMillis() < 0) {
        throw new IllegalStateException("initial retry delay must not be negative");
      }
      if (params.getInitialRpcTimeout().getMillis() < 0) {
        throw new IllegalStateException("initial rpc timeout must not be negative");
      }
      if (params.getRetryDelayMultiplier() < 1.0 || params.getRpcTimeoutMultiplier() < 1.0) {
        throw new IllegalStateException("multiplier must be at least 1");
      }
      if (params.getMaxRetryDelay().compareTo(params.getInitialRetryDelay()) < 0) {
        throw new IllegalStateException("max retry delay must not be shorter than initial delay");
      }
      if (params.getMaxRpcTimeout().compareTo(params.getInitialRpcTimeout()) < 0) {
        throw new IllegalStateException("max rpc timeout must not be shorter than initial timeout");
      }
      return params;
    }

    public RetrySettings.Builder merge(RetrySettings.Builder newSettings) {
      if (newSettings.getInitialRetryDelay() != null) {
        setInitialRetryDelay(newSettings.getInitialRetryDelay());
      }
      if (newSettings.getRetryDelayMultiplier() >= 1) {
        setRetryDelayMultiplier(newSettings.getRetryDelayMultiplier());
      }
      if (newSettings.getMaxRetryDelay() != null) {
        setMaxRetryDelay(newSettings.getMaxRetryDelay());
      }
      if (newSettings.getInitialRpcTimeout() != null) {
        setInitialRpcTimeout(newSettings.getInitialRpcTimeout());
      }
      if (newSettings.getRpcTimeoutMultiplier() >= 1) {
        setRpcTimeoutMultiplier(newSettings.getRpcTimeoutMultiplier());
      }
      if (newSettings.getMaxRpcTimeout() != null) {
        setMaxRpcTimeout(newSettings.getMaxRpcTimeout());
      }
      return this;
    }
  }
}
