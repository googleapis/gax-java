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

import com.google.api.core.BetaApi;

/**
 * The retry algorithm, which makes decision based either on the thrown exception or the returned
 * response, and the execution time settings of the previous attempt.
 *
 * <p>This class is thread-safe.
 *
 * @param <ResponseT> response type
 */
@BetaApi
public class RetryAlgorithm<ResponseT> {
  private final ExceptionRetryAlgorithm exceptionAlgorithm;
  private final ResponseRetryAlgorithm<ResponseT> responseAlgorithm;
  private final TimedRetryAlgorithm timedAlgorithm;

  /**
   * Creates a new retry algorithm instance, which uses exception, response and/or timed algorithms
   * to make a decision. When applicable, the algorithms have the following priority (from higher to
   * lower): {@code exceptionAlgorithm}, {@code responseAlgorithm}, {@code timedAlgorithm}.
   *
   * @param timedAlgorithm timed algorithm to use
   * @param timedAlgorithm timed algorithm to use
   * @param exceptionAlgorithm exception algorithm to use
   */
  public RetryAlgorithm(
      ExceptionRetryAlgorithm exceptionAlgorithm,
      ResponseRetryAlgorithm<ResponseT> responseAlgorithm,
      TimedRetryAlgorithm timedAlgorithm) {
    this.exceptionAlgorithm = checkNotNull(exceptionAlgorithm);
    this.responseAlgorithm = checkNotNull(responseAlgorithm);
    this.timedAlgorithm = checkNotNull(timedAlgorithm);
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
   * Creates a next attempt {@link TimedAttemptSettings}. This method will return first non-null
   * value, returned by either exception (if was thrown), response (if was returned) or timed retry
   * algorithms in that particular order.
   *
   * @param prevThrowable exception thrown by the previous attempt or null if a result was returned
   *     instead
   * @param prevResponse response returned by the previous attempt or null if an exception was
   *     thrown instead
   * @param prevSettings previous attempt settings
   * @return next attempt settings
   */
  public TimedAttemptSettings createNextAttempt(
      Throwable prevThrowable, ResponseT prevResponse, TimedAttemptSettings prevSettings) {
    TimedAttemptSettings newSettings = null;

    if (prevThrowable != null) {
      newSettings = exceptionAlgorithm.createNextAttempt(prevThrowable, prevSettings);
    }
    if (newSettings == null) {
      newSettings = responseAlgorithm.createNextAttempt(prevResponse, prevSettings);
    }
    if (newSettings == null) {
      newSettings = timedAlgorithm.createNextAttempt(prevSettings);
    }
    return newSettings;
  }

  /**
   * Returns {@code true} if another attempt should be made, or {@code false} otherwise.
   *
   * @param prevThrowable exception thrown by the previous attempt or null if a result was returned
   *     instead
   * @param prevResponse response returned by the previous attempt or null if an exception was
   *     thrown instead
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   *     accepted
   * @return {@code true} if another attempt should be made, or {@code false} otherwise
   */
  public boolean shouldRetry(
      Throwable prevThrowable, ResponseT prevResponse, TimedAttemptSettings nextAttemptSettings) {
    boolean result;
    if (prevThrowable != null) {
      result = exceptionAlgorithm.shouldRetry(prevThrowable);
    } else {
      result = responseAlgorithm.shouldRetry(prevResponse);
    }
    return result && timedAlgorithm.shouldRetry(nextAttemptSettings);
  }

  /**
   * Returns {@code true} if another attempt should be made, or {@code false} otherwise.
   *
   * @param prevThrowable exception thrown by the previous attempt or null if a result was returned
   *     instead
   * @param prevResponse response returned by the previous attempt or null if an exception was
   *     thrown instead
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   *     accepted
   * @return {@code true} if retrying process should be canceled, or {@code false} otherwise
   */
  public boolean shouldCancel(
      Throwable prevThrowable, ResponseT prevResponse, TimedAttemptSettings nextAttemptSettings) {
    boolean result;
    if (prevThrowable != null) {
      result = exceptionAlgorithm.shouldCancel(prevThrowable);
    } else {
      result = responseAlgorithm.shouldCancel(prevResponse);
    }

    return result || timedAlgorithm.shouldCancel(nextAttemptSettings);
  }
}
