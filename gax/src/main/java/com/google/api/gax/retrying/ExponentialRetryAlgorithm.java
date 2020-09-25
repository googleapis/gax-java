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
        .setOverallAttemptCount(0)
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
    Duration randomDelay = Duration.ofMillis(nextRandomLong(newRetryDelay));

    // The rpc timeout is determined as follows:
    //     attempt #0  - use the initialRpcTimeout;
    //     attempt #1+ - use the calculated value, or the time remaining in totalTimeout if the
    //                   calculated value would exceed the totalTimeout.
    long newRpcTimeout =
        (long) (settings.getRpcTimeoutMultiplier() * prevSettings.getRpcTimeout().toMillis());
    newRpcTimeout = Math.min(newRpcTimeout, settings.getMaxRpcTimeout().toMillis());

    // The totalTimeout could be zero if a callable is only using maxAttempts to limit retries.
    // If set, calculate time remaining in the totalTimeout since the start, taking into account the
    // next attempt's delay, in order to truncate the RPC timeout should it exceed the totalTimeout.
    if (!settings.getTotalTimeout().isZero()) {
      Duration timeElapsed =
          Duration.ofNanos(clock.nanoTime())
              .minus(Duration.ofNanos(prevSettings.getFirstAttemptStartTimeNanos()));
      Duration timeLeft = globalSettings.getTotalTimeout().minus(timeElapsed).minus(randomDelay);

      // If timeLeft at this point is < 0, the shouldRetry logic will prevent
      // the attempt from being made as it would exceed the totalTimeout. A negative RPC timeout
      // will result in a deadline in the past, which should will always fail prior to making a
      // network call.
      newRpcTimeout = Math.min(newRpcTimeout, timeLeft.toMillis());
    }

    return TimedAttemptSettings.newBuilder()
        .setGlobalSettings(prevSettings.getGlobalSettings())
        .setRetryDelay(Duration.ofMillis(newRetryDelay))
        .setRpcTimeout(Duration.ofMillis(newRpcTimeout))
        .setRandomizedRetryDelay(randomDelay)
        .setAttemptCount(prevSettings.getAttemptCount() + 1)
        .setOverallAttemptCount(prevSettings.getOverallAttemptCount() + 1)
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

    int maxAttempts = globalSettings.getMaxAttempts();
    long totalTimeout = globalSettings.getTotalTimeout().toNanos();

    // If total timeout and maxAttempts is not set then do not attempt retry.
    if (totalTimeout == 0 && maxAttempts == 0) {
      return false;
    }

    long totalTimeSpentNanos =
        clock.nanoTime()
            - nextAttemptSettings.getFirstAttemptStartTimeNanos()
            + nextAttemptSettings.getRandomizedRetryDelay().toNanos();

    // If totalTimeout limit is defined, check that it hasn't been crossed.
    //
    // Note: if the potential time spent is exactly equal to the totalTimeout,
    // the attempt will still be allowed. This might not be desired, but if we
    // enforce it, it could have potentially negative side effects on LRO polling.
    // Specifically, if a polling retry attempt is denied, the LRO is canceled, and
    // if a polling retry attempt is denied because its delay would *reach* the
    // totalTimeout, the LRO would be canceled prematurely. The problem here is that
    // totalTimeout doubles as the polling threshold and also the time limit for an
    // operation to finish.
    if (totalTimeout > 0 && totalTimeSpentNanos > totalTimeout) {
      return false;
    }

    // If maxAttempts limit is defined, check that it hasn't been crossed
    if (maxAttempts > 0 && nextAttemptSettings.getAttemptCount() >= maxAttempts) {
      return false;
    }

    // No limits crossed
    return true;
  }

  // Injecting Random is not possible here, as Random does not provide nextLong(long bound) method
  protected long nextRandomLong(long bound) {
    return bound > 0 && globalSettings.isJittered()
        ? ThreadLocalRandom.current().nextLong(bound)
        : bound;
  }
}
