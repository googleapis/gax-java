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

import java.util.concurrent.CancellationException;

/**
 * A timed retry algorithm is responsible for the following operations, based on the previous
 * attempt settings and current time:
 *
 * <ol>
 *   <li>Creating first attempt {@link TimedAttemptSettings}.
 *   <li>Accepting a task for retry so another attempt will be made.
 *   <li>Canceling retrying process so the related {@link java.util.concurrent.Future} will be
 *       canceled.
 *   <li>Creating {@link TimedAttemptSettings} for each subsequent retry attempt.
 * </ol>
 *
 * Implementations of this interface must be be thread-save.
 */
public interface TimedRetryAlgorithm {

  /**
   * Creates a first attempt {@link TimedAttemptSettings}.
   *
   * @return first attempt settings
   */
  TimedAttemptSettings createFirstAttempt();

  /**
   * Creates a next attempt {@link TimedAttemptSettings}, which defines properties of the next
   * attempt.
   *
   * @param prevSettings previous attempt settings
   * @return next attempt settings or {@code null} if the implementing algorithm does not provide
   *     specific settings for the next attempt
   */
  TimedAttemptSettings createNextAttempt(TimedAttemptSettings prevSettings);

  /**
   * Returns {@code true} if another attempt should be made, or {@code false} otherwise.
   *
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   *     accepted
   * @throws CancellationException if the retrying process should be canceled
   */
  boolean shouldRetry(TimedAttemptSettings nextAttemptSettings) throws CancellationException;
}
