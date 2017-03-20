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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The retry algorithm, which makes decision based on the thrown exception and execution time
 * settings of the previous attempt.
 *
 * This class is thread-safe.
 */
public class RetryAlgorithm {
  private final TimedRetryAlgorithm timedAlgorithm;
  private final ExceptionRetryAlgorithm exceptionAlgorithm;

  /**
   * Creates a new retry algorithm instance, which uses {@code exceptionAlgorithm} and
   * {@code timedAlgorithm} to make a decision. {@code exceptionAlgorithm} has higher priority than
   * the {@code timedAlgorithm}.
   *
   * @param timedAlgorithm timed algorithm to use
   * @param exceptionAlgorithm exception algorithm to use
   */
  public RetryAlgorithm(
      ExceptionRetryAlgorithm exceptionAlgorithm, TimedRetryAlgorithm timedAlgorithm) {
    this.timedAlgorithm = checkNotNull(timedAlgorithm);
    this.exceptionAlgorithm = checkNotNull(exceptionAlgorithm);
  }

  /**
   * Creates a first attempt {@link TimedAttemptSettings}.
   *
   * @return first attempt settings
   */
  public TimedAttemptSettings createFirstAttempt() {
    return timedAlgorithm.createFirstAttempt();
  }

  /**
   * Creates a next attempt {@link TimedAttemptSettings}. This method will return the
   * exception-specific next attempt settings, if there are any, otherwise it will default to the
   * time-specific settings.
   *
   * @param prevThrowable exception thrown by the previous attempt
   * @param prevSettings previous attempt settings
   * @return next attempt settings
   */
  public TimedAttemptSettings createNextAttempt(
      Throwable prevThrowable, TimedAttemptSettings prevSettings) {
    TimedAttemptSettings newSettings =
        exceptionAlgorithm.createNextAttempt(prevThrowable, prevSettings);
    if (newSettings == null) {
      newSettings = timedAlgorithm.createNextAttempt(prevSettings);
    }
    return newSettings;
  }

  /**
   * Returns {@code true} if another attempt should be made, or {@code false} otherwise. This method
   * will return {@code true} only if both timed and exception algorithms return true.
   *
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   * accepted
   * @return {@code true} if another attempt should be made, or {@code false} otherwise
   */
  boolean accept(Throwable prevThrowable, TimedAttemptSettings nextAttemptSettings) {
    return exceptionAlgorithm.accept(prevThrowable) && timedAlgorithm.accept(nextAttemptSettings);
  }
}
