/*
 * Copyright 2017, Google LLC All rights reserved.
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
 * A result retry algorithm is responsible for the following operations (based on the response
 * returned by the previous attempt or on the thrown exception):
 *
 * <ol>
 *   <li>Accepting a task for retry so another attempt will be made.
 *   <li>Canceling retrying process so the related {@link java.util.concurrent.Future} will be
 *       canceled.
 *   <li>Creating {@link TimedAttemptSettings} for each subsequent retry attempt.
 * </ol>
 *
 * Implementations of this interface must be thread-safe.
 *
 * @param <ResponseT> response type
 */
public interface ResultRetryAlgorithm<ResponseT> {
  /**
   * Creates a next attempt {@link TimedAttemptSettings}.
   *
   * @param prevThrowable exception thrown by the previous attempt ({@code null}, if none)
   * @param prevResponse response returned by the previous attempt
   * @param prevSettings previous attempt settings
   * @return next attempt settings or {@code null}, if the implementing algorithm does not provide
   *     specific settings for the next attempt
   */
  TimedAttemptSettings createNextAttempt(
      Throwable prevThrowable, ResponseT prevResponse, TimedAttemptSettings prevSettings);

  /**
   * Returns {@code true} if another attempt should be made, or {@code false} otherwise.
   *
   * @param prevThrowable exception thrown by the previous attempt ({@code null}, if none)
   * @param prevResponse response returned by the previous attempt
   * @throws CancellationException if the retrying process should be canceled
   */
  boolean shouldRetry(Throwable prevThrowable, ResponseT prevResponse) throws CancellationException;
}
