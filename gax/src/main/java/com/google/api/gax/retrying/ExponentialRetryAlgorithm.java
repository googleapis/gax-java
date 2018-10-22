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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiClock;
import java.util.concurrent.ThreadLocalRandom;
import org.threeten.bp.Duration;

/**
 * The timed retry algorithm which uses jittered exponential backoff factor for calculating the next
 * attempt execution time.
 *
 * <p>This class is thread-safe.
 */
public class ExponentialRetryAlgorithm implements TimedRetryAlgorithm {

  private final RetrySettings globalSettings;
  private final ApiClock clock;

  /**
   * Creates a new exponential retry algorithm instance.
   *
   * @param globalSettings global retry settings (attempt independent)
   * @param clock clock to use for time-specific calculations
   * @throws NullPointerException if either {@code globalSettings} or {@code clock} is null
   */
  public ExponentialRetryAlgorithm(RetrySettings globalSettings, ApiClock clock) {
    this.globalSettings = checkNotNull(globalSettings);
    this.clock = checkNotNull(clock);
  }

  /**
   * Creates a first attempt {@link TimedAttemptSettings}. The first attempt is configured to be
   * executed immediately.
   *
   * @return first attempt settings
   */
  @Override
  public TimedAttemptSettings createFirstAttempt() {
    return TimedAttemptSettings.newBuilder()
        .setGlobalSettings(globalSettings)
        .setRetryDelay(Duration.ZERO)
        .setRpcTimeout(globalSettings.getInitialRpcTimeout())
        .setRandomizedRetryDelay(Duration.ZERO)
        .setAttemptCount(0)
        .setFirstAttemptStartTimeNanos(clock.nanoTime())
        .build();
  }

  /**
   * Creates a next attempt {@link TimedAttemptSettings}. The implementation increments the current
   * attempt count and uses randomized exponential backoff factor for calculating next attempt
   * execution time.
   *
   * @param prevSettings previous attempt settings
   * @return next attempt settings
   */
  @Override
  public TimedAttemptSettings createNextAttempt(TimedAttemptSettings prevSettings) {
    RetrySettings settings = prevSettings.getGlobalSettings();

    // The retry delay is determined as follows:
    //     attempt #0  - not used (initial attempt is always made immediately);
    //     attempt #1  - use initialRetryDelay;
    //     attempt #2+ - use the calculated value (i.e. the following if statement is true only
    //                   if we are about to calculate the value for the upcoming 2nd+ attempt).
    long newRetryDelay = settings.getInitialRetryDelay().toMillis();
    if (prevSettings.getAttemptCount() > 0) {
      newRetryDelay =
          (long) (settings.getRetryDelayMultiplier() * prevSettings.getRetryDelay().toMillis());
      newRetryDelay = Math.min(newRetryDelay, settings.getMaxRetryDelay().toMillis());
    }

    // The rpc timeout is determined as follows:
    //     attempt #0  - use the initialRpcTimeout;
    //     attempt #1+ - use the calculated value.
    long newRpcTimeout =
        (long) (settings.getRpcTimeoutMultiplier() * prevSettings.getRpcTimeout().toMillis());
    newRpcTimeout = Math.min(newRpcTimeout, settings.getMaxRpcTimeout().toMillis());

    return TimedAttemptSettings.newBuilder()
        .setGlobalSettings(prevSettings.getGlobalSettings())
        .setRetryDelay(Duration.ofMillis(newRetryDelay))
        .setRpcTimeout(Duration.ofMillis(newRpcTimeout))
        .setRandomizedRetryDelay(Duration.ofMillis(nextRandomLong(newRetryDelay)))
        .setAttemptCount(prevSettings.getAttemptCount() + 1)
        .setFirstAttemptStartTimeNanos(prevSettings.getFirstAttemptStartTimeNanos())
        .build();
  }

  /**
   * Returns {@code true} if another attempt should be made, or {@code false} otherwise.
   *
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   *     accepted
   * @return {@code true} if {@code nextAttemptSettings} does not exceed either maxAttempts limit or
   *     totalTimeout limit, or {@code false} otherwise
   */
  @Override
  public boolean shouldRetry(TimedAttemptSettings nextAttemptSettings) {
    RetrySettings globalSettings = nextAttemptSettings.getGlobalSettings();
    long totalTimeSpentNanos =
        clock.nanoTime()
            - nextAttemptSettings.getFirstAttemptStartTimeNanos()
            + nextAttemptSettings.getRandomizedRetryDelay().toNanos();

    return totalTimeSpentNanos <= globalSettings.getTotalTimeout().toNanos()
        && (globalSettings.getMaxAttempts() <= 0
            || nextAttemptSettings.getAttemptCount() < globalSettings.getMaxAttempts());
  }

  // Injecting Random is not possible here, as Random does not provide nextLong(long bound) method
  protected long nextRandomLong(long bound) {
    return bound > 0 && globalSettings.isJittered()
        ? ThreadLocalRandom.current().nextLong(bound)
        : bound;
  }
}
