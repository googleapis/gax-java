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

import com.google.api.gax.tracing.ApiTracer;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.threeten.bp.Duration;

class FailingCallable implements Callable<String> {
  static final RetrySettings FAST_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setMaxAttempts(6)
          .setInitialRetryDelay(Duration.ofMillis(8L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofMillis(8L))
          .setInitialRpcTimeout(Duration.ofMillis(8L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofMillis(8L))
          .setTotalTimeout(Duration.ofMillis(400L))
          .build();
  static final RetrySettings FAILING_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setMaxAttempts(2)
          .setInitialRetryDelay(Duration.ofNanos(1L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofNanos(1L))
          .setInitialRpcTimeout(Duration.ofNanos(1L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofNanos(1L))
          .setTotalTimeout(Duration.ofNanos(1L))
          .build();

  private AtomicInteger attemptsCount = new AtomicInteger(0);
  private final ApiTracer tracer;
  private final int expectedFailuresCount;
  private final String result;
  private final CountDownLatch firstAttemptFinished = new CountDownLatch(1);

  FailingCallable(int expectedFailuresCount, String result, ApiTracer tracer) {
    this.tracer = tracer;
    this.expectedFailuresCount = expectedFailuresCount;
    this.result = result;
  }

  CountDownLatch getFirstAttemptFinishedLatch() {
    return firstAttemptFinished;
  }

  @Override
  public String call() throws Exception {
    try {
      int attemptNumber = attemptsCount.getAndIncrement();

      tracer.attemptStarted(attemptNumber);

      if (attemptNumber < expectedFailuresCount) {
        throw new CustomException();
      }

      return result;
    } finally {
      firstAttemptFinished.countDown();
    }
  }

  static class CustomException extends RuntimeException {

    private static final long serialVersionUID = -1543459008653697004L;
  }
}
