/*
 * Copyright 2016, Google Inc.
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

/**
 * {@code BackoffParams} encapsulates parameters for exponential backoff.
 */
@AutoValue
public abstract class BackoffParams {
  public abstract Duration getInitialDelay();

  public abstract double getDelayMultiplier();

  public abstract Duration getMaxDelay();

  public static Builder newBuilder() {
    return new AutoValue_BackoffParams.Builder();
  }

  public Builder toBuilder() {
    return new AutoValue_BackoffParams.Builder(this);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setInitialDelay(Duration initialDelayDuration);

    public abstract Builder setDelayMultiplier(double delayMultiplier);

    public abstract Builder setMaxDelay(Duration maxDelayDuration);

    abstract BackoffParams autoBuild();

    public BackoffParams build() {
      BackoffParams backoff = autoBuild();
      if (backoff.getInitialDelay().getMillis() < 0) {
        throw new IllegalStateException("initial delay must not be negative");
      }
      if (backoff.getDelayMultiplier() < 1.0) {
        throw new IllegalStateException("delay multiplier must be at least 1");
      }
      if (backoff.getMaxDelay().compareTo(backoff.getInitialDelay()) < 0) {
        throw new IllegalStateException("max delay must not be smaller than initial delay");
      }
      return backoff;
    }
  }
}
