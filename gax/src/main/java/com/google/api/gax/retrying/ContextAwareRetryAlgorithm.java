/*
 * Copyright 2021 Google LLC
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

import com.google.api.core.BetaApi;
import java.util.concurrent.CancellationException;

/**
 * The retry algorithm, which decides based either on the thrown exception or the returned response,
 * the execution time settings of the previous attempt, and the {@link RetrySettings} and retryable
 * codes supplied by a {@link RetryingContext}.
 *
 * <p>This class is thread-safe.
 *
 * @param <ResponseT> response type
 */
public class ContextAwareRetryAlgorithm<ResponseT> extends RetryAlgorithm<ResponseT> {
  /**
   * Creates a new retry algorithm instance, which uses thrown exception or returned response and
   * timed algorithms to make a decision. The result algorithm has higher priority than the timed
   * algorithm.
   *
   * @param resultAlgorithm result algorithm to use
   * @param timedAlgorithm timed algorithm to use
   */
  public ContextAwareRetryAlgorithm(
      ContextAwareResultRetryAlgorithm<ResponseT> resultAlgorithm,
      ContextAwareTimedRetryAlgorithm timedAlgorithm) {
    super(resultAlgorithm, timedAlgorithm);
  }

  /**
   * Creates a first attempt {@link TimedAttemptSettings}.
   *
   * @param context the {@link RetryingContext} that can be used to get the initial {@link
   *     RetrySettings}.
   * @return first attempt settings
   */
  public TimedAttemptSettings createFirstAttempt(RetryingContext context) {
    return getTimedAlgorithm().createFirstAttempt(context);
  }

  /**
   * Creates a next attempt {@link TimedAttemptSettings}. This method will return first non-null
   * value, returned by either result or timed retry algorithms in that particular order.
   *
   * @param context the {@link RetryingContext} that can be used to determine the {@link
   *     RetrySettings} for the next attempt
   * @param prevThrowable exception thrown by the previous attempt or null if a result was returned
   *     instead
   * @param prevResponse response returned by the previous attempt or null if an exception was
   *     thrown instead
   * @param prevSettings previous attempt settings
   * @return next attempt settings, can be {@code null}, if no there should be no new attempt
   */
  public TimedAttemptSettings createNextAttempt(
      RetryingContext context,
      Throwable prevThrowable,
      ResponseT prevResponse,
      TimedAttemptSettings prevSettings) {
    // a small optimization, which allows to avoid calling relatively heavy methods
    // like timedAlgorithm.createNextAttempt(), when it is not necessary.
    if (!getResultAlgorithm().shouldRetry(context, prevThrowable, prevResponse)) {
      return null;
    }

    TimedAttemptSettings newSettings =
        getResultAlgorithm().createNextAttempt(context, prevThrowable, prevResponse, prevSettings);
    if (newSettings == null) {
      newSettings = getTimedAlgorithm().createNextAttempt(context, prevSettings);
    }
    return newSettings;
  }

  /**
   * Returns {@code true} if another attempt should be made, or {@code false} otherwise.
   *
   * @param context the {@link RetryingContext} that can be used to determine whether another
   *     attempt should be made.
   * @param prevThrowable exception thrown by the previous attempt or null if a result was returned
   *     instead
   * @param prevResponse response returned by the previous attempt or null if an exception was
   *     thrown instead
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   *     accepted
   * @throws CancellationException if the retrying process should be canceled
   * @return {@code true} if another attempt should be made, or {@code false} otherwise
   */
  public boolean shouldRetry(
      RetryingContext context,
      Throwable prevThrowable,
      ResponseT prevResponse,
      TimedAttemptSettings nextAttemptSettings)
      throws CancellationException {
    return getResultAlgorithm().shouldRetry(context, prevThrowable, prevResponse)
        && nextAttemptSettings != null
        && getTimedAlgorithm().shouldRetry(context, nextAttemptSettings);
  }

  @Override
  @BetaApi("Surface for inspecting a RetryAlgorithm is not yet stable")
  public ContextAwareResultRetryAlgorithm<ResponseT> getResultAlgorithm() {
    return (ContextAwareResultRetryAlgorithm<ResponseT>) super.getResultAlgorithm();
  }

  @Override
  @BetaApi("Surface for inspecting a RetryAlgorithm is not yet stable")
  public ContextAwareTimedRetryAlgorithm getTimedAlgorithm() {
    return (ContextAwareTimedRetryAlgorithm) super.getTimedAlgorithm();
  }
}
