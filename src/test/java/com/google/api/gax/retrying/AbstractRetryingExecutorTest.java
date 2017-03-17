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

import static com.google.api.gax.retrying.FailingCallable.FAST_RETRY_SETTINGS;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.api.gax.core.RetrySettings;
import com.google.api.gax.retrying.FailingCallable.CustomException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public abstract class AbstractRetryingExecutorTest {

  protected abstract RetryingExecutor<String> getRetryingExecutor(RetrySettings retrySettings);

  protected ExceptionRetryAlgorithm getNoOpExceptionRetryAlgorithm() {
    return new ExceptionRetryAlgorithm() {
      @Override
      public TimedAttemptSettings createNextAttempt(
          Throwable prevThrowable, TimedAttemptSettings prevSettings) {
        return null;
      }

      @Override
      public boolean accept(Throwable prevThrowable) {
        return true;
      }
    };
  }

  @Test
  public void testNoFailures() throws ExecutionException, InterruptedException {
    FailingCallable callable = new FailingCallable(0, "SUCCESS");
    RetryingExecutor<String> executor = getRetryingExecutor(FAST_RETRY_SETTINGS);
    RetryingFuture<String> future = executor.createFuture(callable);
    executor.submit(future);

    assertEquals("SUCCESS", future.get());
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());
    assertEquals(0, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testSuccessWithFailures() throws ExecutionException, InterruptedException {
    FailingCallable callable = new FailingCallable(5, "SUCCESS");
    RetryingExecutor<String> executor = getRetryingExecutor(FAST_RETRY_SETTINGS);
    RetryingFuture<String> future = executor.createFuture(callable);
    executor.submit(future);

    assertEquals("SUCCESS", future.get());
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());
    assertEquals(5, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testMaxRetriesExcceeded() {
    FailingCallable callable = new FailingCallable(6, "FAILURE");
    RetryingExecutor<String> executor = getRetryingExecutor(FAST_RETRY_SETTINGS);
    RetryingFuture<String> future = executor.createFuture(callable);
    executor.submit(future);

    CustomException exception = null;
    try {
      future.get();
    } catch (Exception e) {
      exception = (CustomException) e.getCause();
    }
    assertEquals(CustomException.class, exception.getClass());
    assertEquals(5, future.getAttemptSettings().getAttemptCount());
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());
  }

  @Test
  public void testTotalTimeoutExcceeded() throws Exception {
    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.millis(Integer.MAX_VALUE))
            .setMaxRetryDelay(Duration.millis(Integer.MAX_VALUE))
            .build();
    RetryingExecutor<String> executor = getRetryingExecutor(retrySettings);
    FailingCallable callable = new FailingCallable(6, "FAILURE");
    RetryingFuture<String> future = executor.createFuture(callable);
    executor.submit(future);

    CustomException exception = null;
    try {
      future.get();
    } catch (Exception e) {
      exception = (CustomException) e.getCause();
    }
    assertEquals(CustomException.class, exception.getClass());
    assertTrue(future.getAttemptSettings().getAttemptCount() < 4);
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());
  }

  @Test(expected = CancellationException.class)
  public void testCancelOuterFuture() throws ExecutionException, InterruptedException {
    FailingCallable callable = new FailingCallable(4, "SUCCESS");

    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.millis(1_000L))
            .setMaxRetryDelay(Duration.millis(1_000L))
            .setTotalTimeout(Duration.millis(10_0000L))
            .build();
    RetryingExecutor<String> executor = getRetryingExecutor(retrySettings);
    RetryingFuture<String> future = executor.createFuture(callable);
    future.cancel(false);
    executor.submit(future);

    assertTrue(future.isDone());
    assertTrue(future.isCancelled());
    assertTrue(future.getAttemptSettings().getAttemptCount() < 4);
    future.get();
  }
}
