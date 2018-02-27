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
package com.google.api.gax.rpc;

import com.google.api.gax.retrying.ResultRetryAlgorithm;
import com.google.api.gax.retrying.TimedAttemptSettings;
import org.threeten.bp.Duration;

/* Package-private for internal use. */
class ApiResultRetryAlgorithm<ResponseT> implements ResultRetryAlgorithm<ResponseT> {
  // Duration to sleep on if the error is DEADLINE_EXCEEDED.
  public static final Duration DEADLINE_SLEEP_DURATION = Duration.ofMillis(1);

  @Override
  public TimedAttemptSettings createNextAttempt(
      Throwable prevThrowable, ResponseT prevResponse, TimedAttemptSettings prevSettings) {
    if (prevThrowable != null && prevThrowable instanceof DeadlineExceededException) {
      return TimedAttemptSettings.newBuilder()
          .setGlobalSettings(prevSettings.getGlobalSettings())
          .setRetryDelay(prevSettings.getRetryDelay())
          .setRpcTimeout(prevSettings.getRpcTimeout())
          .setRandomizedRetryDelay(DEADLINE_SLEEP_DURATION)
          .setAttemptCount(prevSettings.getAttemptCount() + 1)
          .setFirstAttemptStartTimeNanos(prevSettings.getFirstAttemptStartTimeNanos())
          .build();
    }
    return null;
  }

  @Override
  public boolean shouldRetry(Throwable prevThrowable, ResponseT prevResponse) {
    return (prevThrowable instanceof ApiException) && ((ApiException) prevThrowable).isRetryable();
  }
}
