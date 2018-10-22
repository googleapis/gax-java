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
package com.google.api.gax.retrying;

import com.google.api.core.ApiClock;
import com.google.api.core.BetaApi;
import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.Builder;
import org.threeten.bp.Duration;

/** Timed attempt execution settings. Defines time-specific properties of a retry attempt. */
@BetaApi
@AutoValue
public abstract class TimedAttemptSettings {

  /** Returns global (attempt-independent) retry settings. */
  public abstract RetrySettings getGlobalSettings();

  /**
   * Returns the calculated retry delay. Note that the actual delay used for retry scheduling may be
   * different (randomized, based on this value).
   */
  public abstract Duration getRetryDelay();

  /** Returns rpc timeout used for this attempt. */
  public abstract Duration getRpcTimeout();

  /**
   * Returns randomized attempt delay. By default this value is calculated based on the {@code
   * retryDelay} value, and is used as the actual attempt execution delay.
   */
  public abstract Duration getRandomizedRetryDelay();

  /** The attempt count. It is a zero-based value (first attempt will have this value set to 0). */
  public abstract int getAttemptCount();

  /**
   * The start time of the first attempt. Note that this value is dependent on the actual {@link
   * ApiClock} used during the process.
   */
  public abstract long getFirstAttemptStartTimeNanos();

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new AutoValue_TimedAttemptSettings.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets global (attempt-independent) retry settings. */
    public abstract Builder setGlobalSettings(RetrySettings value);

    /**
     * Sets the calculated retry delay. Note that the actual delay used for retry scheduling may be
     * different (randomized, based on this value).
     */
    public abstract Builder setRetryDelay(Duration value);

    /** Sets rpc timeout used for this attempt. */
    public abstract Builder setRpcTimeout(Duration value);

    /**
     * Sets randomized attempt delay. By default this value is calculated based on the {@code
     * retryDelay} value, and is used as the actual attempt execution delay.
     */
    public abstract Builder setRandomizedRetryDelay(Duration value);

    /**
     * Set the attempt count. It is a zero-based value (first attempt will have this value set to
     * 0).
     */
    public abstract Builder setAttemptCount(int value);

    /**
     * Set the start time of the first attempt. Note that this value is dependent on the actual
     * {@link ApiClock} used during the process.
     */
    public abstract Builder setFirstAttemptStartTimeNanos(long value);

    public abstract TimedAttemptSettings build();
  }
}
