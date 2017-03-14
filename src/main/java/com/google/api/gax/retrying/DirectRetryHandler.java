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
import com.google.common.util.concurrent.Futures;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.joda.time.Duration;

/**
 * The retry handler which executes attempts in the current thread, potentially causing the current
 * thread to sleep for a specified amount of time before execution.
 *
 * This class is thread-safe.
 *
 * @param <ResponseT> response type
 */
public class DirectRetryHandler<ResponseT> extends AbstractRetryHandler<ResponseT> {

  /**
   * Creates a new default direct retry handler
   *
   * @param clock clock to use during execution scheduling
   */
  public DirectRetryHandler(NanoClock clock) {
    super(clock);
  }

  /**
   * Executes attempt in the current thread. Causes the current thread to sleep, if it is not the
   * first attempt.
   *
   * @param callable the actual callable to execute
   * @param attemptSettings current attempt settings
   */
  @Override
  public Future<ResponseT> executeAttempt(
      Callable<ResponseT> callable, RetryAttemptSettings attemptSettings) {
    try {
      if (Duration.ZERO.compareTo(attemptSettings.getRandomizedRetryDelay()) < 0) {
        Thread.sleep(attemptSettings.getRandomizedRetryDelay().getMillis());
      }
      return Futures.immediateFuture(callable.call());
    } catch (InterruptedException | InterruptedIOException | ClosedByInterruptException e) {
      Thread.currentThread().interrupt();
      return Futures.immediateFailedFuture(e);
    } catch (Throwable e) {
      return Futures.immediateFailedFuture(e);
    }
  }
}
