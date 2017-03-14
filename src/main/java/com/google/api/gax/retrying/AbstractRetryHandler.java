/*
 * Copyright 2017, Google Inc. All rights reserved.
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
package com.google.api.gax.retrying;

import com.google.api.gax.core.NanoClock;
import com.google.api.gax.core.RetrySettings;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.joda.time.Duration;

/**
 * Basic implementation of the {@link RetryHandler} interface. It is responsible for defining and
 * checking attempt's basic properties (execution time and count limits).
 *
 * This class is thread-safe, and all inheriting classes are required to be thread-safe.
 *
 * @param <ResponseT> response type
 */
public abstract class AbstractRetryHandler<ResponseT> implements RetryHandler<ResponseT> {

  private final NanoClock clock;

  protected AbstractRetryHandler(NanoClock clock) {
    this.clock = clock;
  }

  /**
   * Ensures that the retry logic hasn't exceeded either maximum number of retries or the total
   * execution timeout.
   *
   * @param e exception thrown by the previous attempt
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   * accepted
   * @return {@code true} if none of the retry limits are exceeded
   */
  @Override
  public boolean accept(Throwable e, RetryAttemptSettings nextAttemptSettings) {
    RetrySettings globalSettings = nextAttemptSettings.getGlobalSettings();
    long randRetryDelayMillis = nextAttemptSettings.getRandomizedRetryDelay().getMillis();
    long totalTimeSpentNanos =
        clock.nanoTime()
            - nextAttemptSettings.getFirstAttemptStartTime()
            + TimeUnit.NANOSECONDS.convert(randRetryDelayMillis, TimeUnit.MILLISECONDS);

    long totalTimeoutMillis = globalSettings.getTotalTimeout().getMillis();
    long totalTimeoutNanos =
        TimeUnit.NANOSECONDS.convert(totalTimeoutMillis, TimeUnit.MILLISECONDS);

    return totalTimeSpentNanos <= totalTimeoutNanos
        && (globalSettings.getMaxAttempts() <= 0
            || nextAttemptSettings.getAttemptCount() < globalSettings.getMaxAttempts());
  }

  /**
   * Creates next attempt settings. It increments the current attempt count and uses randomized
   * exponential backoff factor for calculating next attempt execution time.
   *
   * @param e exception thrown by the previous attempt
   * @param prevSettings previous attempt settings
   * @return next attempt settings
   */
  @Override
  public RetryAttemptSettings createNextAttemptSettings(
      Throwable e, RetryAttemptSettings prevSettings) {
    RetrySettings settings = prevSettings.getGlobalSettings();

    long newRetryDelay = settings.getInitialRetryDelay().getMillis();
    long newRpcTimeout = settings.getInitialRpcTimeout().getMillis();

    if (prevSettings.getAttemptCount() > 0) {
      newRetryDelay =
          (long) (settings.getRetryDelayMultiplier() * prevSettings.getRetryDelay().getMillis());
      newRetryDelay = Math.min(newRetryDelay, settings.getMaxRetryDelay().getMillis());
      newRpcTimeout =
          (long) (settings.getRpcTimeoutMultiplier() * prevSettings.getRpcTimeout().getMillis());
      newRpcTimeout = Math.min(newRpcTimeout, settings.getMaxRpcTimeout().getMillis());
    }

    return new RetryAttemptSettings(
        prevSettings.getGlobalSettings(),
        Duration.millis(newRetryDelay),
        Duration.millis(newRpcTimeout),
        Duration.millis(ThreadLocalRandom.current().nextLong(newRetryDelay)),
        prevSettings.getAttemptCount() + 1,
        prevSettings.getFirstAttemptStartTime());
  }

  /**
   * Creates first attempt future. By default the first attempt is configured to be executed
   * immediately.
   *
   * @param callable the actual callable, which should be executed in a retriable context
   * @param globalSettings global retry settings (attempt independent)
   */
  @Override
  public RetryFuture<ResponseT> createFirstAttempt(
      Callable<ResponseT> callable, RetrySettings globalSettings) {
    RetryAttemptSettings firstAttemptSettings =
        new RetryAttemptSettings(
            globalSettings,
            Duration.ZERO,
            globalSettings.getTotalTimeout(),
            Duration.ZERO,
            0,
            clock.nanoTime());

    return new RetryFutureImpl<>(callable, firstAttemptSettings, this);
  }
}
