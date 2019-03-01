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

import static com.google.api.gax.retrying.FailingCallable.FAST_RETRY_SETTINGS;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.api.core.ApiFuture;
import com.google.api.core.NanoClock;
import com.google.api.gax.retrying.FailingCallable.CustomException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.threeten.bp.Duration;

@RunWith(MockitoJUnitRunner.class)
public class ScheduledRetryingExecutorTest extends AbstractRetryingExecutorTest {
  private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  // Number of test runs, essential for multithreaded tests.
  private static final int EXECUTIONS_COUNT = 5;

  @Override
  protected RetryingExecutorWithContext<String> getExecutor(RetryAlgorithm<String> retryAlgorithm) {
    return getRetryingExecutor(retryAlgorithm, scheduler);
  }

  @Override
  protected RetryAlgorithm<String> getAlgorithm(
      RetrySettings retrySettings, int apocalypseCountDown, RuntimeException apocalypseException) {
    return new RetryAlgorithm<>(
        new TestResultRetryAlgorithm<String>(apocalypseCountDown, apocalypseException),
        new ExponentialRetryAlgorithm(retrySettings, NanoClock.getDefaultClock()));
  }

  private RetryingExecutorWithContext<String> getRetryingExecutor(
      RetryAlgorithm<String> retryAlgorithm, ScheduledExecutorService scheduler) {

    return new ScheduledRetryingExecutor<>(retryAlgorithm, scheduler);
  }

  @After
  public void after() {
    scheduler.shutdownNow();
  }

  @Test
  public void testSuccessWithFailuresPeekAttempt() throws Exception {
    for (int executionsCount = 0; executionsCount < EXECUTIONS_COUNT; executionsCount++) {
      final int maxRetries = 100;

      ScheduledExecutorService localExecutor = Executors.newSingleThreadScheduledExecutor();
      FailingCallable callable = new FailingCallable(15, "SUCCESS", tracer);

      RetrySettings retrySettings =
          FAST_RETRY_SETTINGS
              .toBuilder()
              .setTotalTimeout(Duration.ofMillis(1000L))
              .setMaxAttempts(maxRetries)
              .build();

      RetryingExecutorWithContext<String> executor =
          getRetryingExecutor(getAlgorithm(retrySettings, 0, null), localExecutor);
      RetryingFuture<String> future = executor.createFuture(callable, retryingContext);

      assertNull(future.peekAttemptResult());
      assertSame(future.peekAttemptResult(), future.peekAttemptResult());
      assertFalse(future.getAttemptResult().isDone());
      assertFalse(future.getAttemptResult().isCancelled());

      future.setAttemptFuture(executor.submit(future));

      int failedAttempts = 0;
      while (!future.isDone()) {
        ApiFuture<String> attemptResult = future.peekAttemptResult();
        if (attemptResult != null) {
          assertTrue(attemptResult.isDone());
          assertFalse(attemptResult.isCancelled());
          try {
            attemptResult.get();
          } catch (ExecutionException e) {
            if (e.getCause() instanceof CustomException) {
              failedAttempts++;
            }
          }
        }
        Thread.sleep(0L, 100);
      }

      assertFutureSuccess(future);
      assertEquals(15, future.getAttemptSettings().getAttemptCount());
      assertTrue(failedAttempts > 0);
      localExecutor.shutdownNow();
    }
  }

  @Test
  public void testSuccessWithFailuresGetAttempt() throws Exception {
    for (int executionsCount = 0; executionsCount < EXECUTIONS_COUNT; executionsCount++) {
      final int maxRetries = 100;

      ScheduledExecutorService localExecutor = Executors.newSingleThreadScheduledExecutor();
      FailingCallable callable = new FailingCallable(15, "SUCCESS", tracer);
      RetrySettings retrySettings =
          FAST_RETRY_SETTINGS
              .toBuilder()
              .setTotalTimeout(Duration.ofMillis(1000L))
              .setMaxAttempts(maxRetries)
              .build();

      RetryingExecutorWithContext<String> executor =
          getRetryingExecutor(getAlgorithm(retrySettings, 0, null), localExecutor);
      RetryingFuture<String> future = executor.createFuture(callable, retryingContext);

      assertNull(future.peekAttemptResult());
      assertSame(future.getAttemptResult(), future.getAttemptResult());
      assertFalse(future.getAttemptResult().isDone());
      assertFalse(future.getAttemptResult().isCancelled());

      future.setAttemptFuture(executor.submit(future));

      CustomException exception;
      int checks = 0;
      do {
        exception = null;
        checks++;
        Future<String> attemptResult = future.getAttemptResult();
        try {
          // testing that the gotten attempt result is non-cancelable
          assertFalse(attemptResult.cancel(false));
          assertFalse(attemptResult.cancel(true));
          attemptResult.get();
          assertNotNull(future.peekAttemptResult());
        } catch (ExecutionException e) {
          exception = (CustomException) e.getCause();
        }
        assertTrue(attemptResult.isDone());
        assertFalse(attemptResult.isCancelled());
      } while (exception != null && checks < maxRetries + 1);

      assertTrue(future.isDone());
      assertFutureSuccess(future);
      assertEquals(15, future.getAttemptSettings().getAttemptCount());
      assertTrue("checks is equal to " + checks, checks > 1 && checks <= maxRetries);
      localExecutor.shutdownNow();
    }
  }

  @Test
  public void testCancelGetAttempt() throws Exception {
    for (int executionsCount = 0; executionsCount < EXECUTIONS_COUNT; executionsCount++) {
      ScheduledExecutorService localExecutor = Executors.newSingleThreadScheduledExecutor();
      final int maxRetries = 100;

      FailingCallable callable = new FailingCallable(maxRetries - 1, "SUCCESS", tracer);
      RetrySettings retrySettings =
          FAST_RETRY_SETTINGS
              .toBuilder()
              .setTotalTimeout(Duration.ofMillis(1000L))
              .setMaxAttempts(maxRetries)
              .build();

      RetryingExecutorWithContext<String> executor =
          getRetryingExecutor(getAlgorithm(retrySettings, 0, null), localExecutor);
      RetryingFuture<String> future = executor.createFuture(callable, retryingContext);

      assertNull(future.peekAttemptResult());
      assertSame(future.getAttemptResult(), future.getAttemptResult());
      assertFalse(future.getAttemptResult().isDone());
      assertFalse(future.getAttemptResult().isCancelled());

      future.setAttemptFuture(executor.submit(future));

      CustomException exception;
      CancellationException cancellationException = null;
      int checks = 0;
      int failedCancelations = 0;
      do {
        exception = null;
        checks++;
        Future<String> attemptResult = future.getAttemptResult();
        try {
          attemptResult.get();
          assertNotNull(future.peekAttemptResult());
        } catch (CancellationException e) {
          cancellationException = e;
        } catch (ExecutionException e) {
          exception = (CustomException) e.getCause();
        }
        assertTrue(attemptResult.isDone());
        if (!future.cancel(true)) {
          failedCancelations++;
        }
      } while (exception != null && checks < maxRetries);

      assertTrue(future.isDone());
      assertNotNull(cancellationException);
      // future.cancel(true) may return false sometimes, which is ok. Also, the every cancellation
      // of
      // an already cancelled future should return false (this is what -1 means here)
      assertEquals(2, checks - (failedCancelations - 1));
      assertTrue(future.getAttemptSettings().getAttemptCount() > 0);
      assertFutureCancel(future);
      localExecutor.shutdownNow();
    }
  }

  @Test
  public void testCancelOuterFutureAfterStart() throws Exception {
    for (int executionsCount = 0; executionsCount < EXECUTIONS_COUNT; executionsCount++) {
      ScheduledExecutorService localExecutor = Executors.newSingleThreadScheduledExecutor();
      FailingCallable callable = new FailingCallable(4, "SUCCESS", tracer);
      RetrySettings retrySettings =
          FAST_RETRY_SETTINGS
              .toBuilder()
              .setInitialRetryDelay(Duration.ofMillis(1_000L))
              .setMaxRetryDelay(Duration.ofMillis(1_000L))
              .setTotalTimeout(Duration.ofMillis(10_0000L))
              .build();
      RetryingExecutorWithContext<String> executor =
          getRetryingExecutor(getAlgorithm(retrySettings, 0, null), localExecutor);
      RetryingFuture<String> future = executor.createFuture(callable, retryingContext);
      future.setAttemptFuture(executor.submit(future));

      Thread.sleep(30L);

      boolean res = future.cancel(false);
      assertTrue(res);
      assertFutureCancel(future);
      assertTrue(future.getAttemptSettings().getAttemptCount() < 4);
      localExecutor.shutdownNow();
    }
  }

  @Test
  public void testCancelIsTraced() throws Exception {
    ScheduledExecutorService localExecutor = Executors.newSingleThreadScheduledExecutor();
    FailingCallable callable = new FailingCallable(4, "SUCCESS", tracer);
    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(1_000L))
            .setMaxRetryDelay(Duration.ofMillis(1_000L))
            .setTotalTimeout(Duration.ofMillis(10_0000L))
            .build();
    RetryingExecutorWithContext<String> executor =
        getRetryingExecutor(getAlgorithm(retrySettings, 0, null), localExecutor);
    RetryingFuture<String> future = executor.createFuture(callable, retryingContext);
    future.setAttemptFuture(executor.submit(future));

    Thread.sleep(30L);

    boolean res = future.cancel(false);
    assertTrue(res);
    assertFutureCancel(future);

    Mockito.verify(tracer).attemptCancelled();
    localExecutor.shutdownNow();
  }

  @Test
  public void testCancelProxiedFutureAfterStart() throws Exception {
    // this is a heavy test, which takes a lot of time, so only few executions.
    for (int executionsCount = 0; executionsCount < 2; executionsCount++) {
      ScheduledExecutorService localExecutor = Executors.newSingleThreadScheduledExecutor();
      FailingCallable callable = new FailingCallable(5, "SUCCESS", tracer);
      RetrySettings retrySettings =
          FAST_RETRY_SETTINGS
              .toBuilder()
              .setInitialRetryDelay(Duration.ofMillis(1_000L))
              .setMaxRetryDelay(Duration.ofMillis(1_000L))
              .setTotalTimeout(Duration.ofMillis(10_0000L))
              .build();
      RetryingExecutorWithContext<String> executor =
          getRetryingExecutor(getAlgorithm(retrySettings, 0, null), localExecutor);
      RetryingFuture<String> future = executor.createFuture(callable, retryingContext);
      future.setAttemptFuture(executor.submit(future));

      Thread.sleep(50L);

      // Note that shutdownNow() will not cancel internal FutureTasks automatically, which
      // may potentially cause another thread handing on RetryingFuture#get() call forever.
      // Canceling the tasks returned by shutdownNow() also does not help, because of missing
      // feature
      // in guava's ListenableScheduledFuture, which does not cancel itself, when its delegate is
      // canceled.
      // So only the graceful shutdown() is supported properly.
      localExecutor.shutdown();

      assertFutureFail(future, RejectedExecutionException.class);
      localExecutor.shutdownNow();
    }
  }
}
