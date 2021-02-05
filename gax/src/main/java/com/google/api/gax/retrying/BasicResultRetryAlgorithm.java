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
 * A {@link ResultRetryAlgorithm} that can use a {@link RetryingContext} to determine whether to
 * retry a call.
 *
 * <p>This implementation retries all exceptions, all responses are accepted (including {@code
 * null}) and no retrying process will ever be cancelled.
 *
 * @param <ResponseT> attempt response type
 */
public class BasicResultRetryAlgorithm<ResponseT> implements ResultRetryAlgorithm<ResponseT> {
  /**
   * Always returns null, indicating that this algorithm does not provide any specific settings for
   * the next attempt.
   *
   * @param previousThrowable exception thrown by the previous attempt ({@code null}, if none)
   * @param previousResponse response returned by the previous attempt
   * @param previousSettings previous attempt settings
   */
  @Override
  public TimedAttemptSettings createNextAttempt(
      Throwable previousThrowable,
      ResponseT previousResponse,
      TimedAttemptSettings previousSettings) {
    return null;
  }

  /**
   * Always returns null, indicating that this algorithm does not provide any specific settings for
   * the next attempt.
   *
   * @param context the retrying context of this invocation that can be used to determine the
   *     settings for the next attempt
   * @param previousThrowable exception thrown by the previous attempt ({@code null}, if none)
   * @param previousResponse response returned by the previous attempt
   * @param previousSettings previous attempt settings
   */
  public TimedAttemptSettings createNextAttempt(
      RetryingContext context,
      Throwable previousThrowable,
      ResponseT previousResponse,
      TimedAttemptSettings previousSettings) {
    return createNextAttempt(previousThrowable, previousResponse, previousSettings);
  }

  /**
   * Returns {@code true} if an exception was thrown ({@code previousThrowable != null}), {@code
   * false} otherwise.
   *
   * @param previousThrowable exception thrown by the previous attempt ({@code null}, if none)
   * @param previousResponse response returned by the previous attempt
   */
  @Override
  public boolean shouldRetry(Throwable previousThrowable, ResponseT previousResponse) {
    return previousThrowable != null;
  }

  /**
   * Returns {@code true} if an exception was thrown ({@code previousThrowable != null}), {@code
   * false} otherwise.
   *
   * @param context the retrying context of this invocation that can be used to determine whether
   *     the call should be retried
   * @param previousThrowable exception thrown by the previous attempt ({@code null}, if none)
   * @param previousResponse response returned by the previous attempt
   */
  public boolean shouldRetry(
      RetryingContext context, Throwable previousThrowable, ResponseT previousResponse)
      throws CancellationException {
    return shouldRetry(previousThrowable, previousResponse);
  }
}
