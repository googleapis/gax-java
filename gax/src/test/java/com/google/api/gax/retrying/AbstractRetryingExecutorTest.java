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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public abstract class AbstractRetryingExecutorTest {
  protected abstract RetryingExecutor<String> getExecutor(RetryAlgorithm<String> retryAlgorithm);

  protected abstract RetryAlgorithm<String> getAlgorithm(
      RetrySettings retrySettings, int apocalypseCountDown, RuntimeException apocalypseException);

  @Test
  public void testSuccess() throws Exception {
    FailingCallable callable = new FailingCallable(0, "SUCCESS");
    RetryingExecutor<String> executor = getExecutor(getAlgorithm(FAST_RETRY_SETTINGS, 0, null));
    RetryingFuture<String> future = executor.createFuture(callable);
    future.setAttemptFuture(executor.submit(future));

    assertFutureSuccess(future);
    assertEquals(0, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testSuccessWithFailures() throws Exception {
    FailingCallable callable = new FailingCallable(5, "SUCCESS");
    RetryingExecutor<String> executor = getExecutor(getAlgorithm(FAST_RETRY_SETTINGS, 0, null));
    RetryingFuture<String> future = executor.createFuture(callable);
    future.setAttemptFuture(executor.submit(future));

    assertFutureSuccess(future);
    assertEquals(5, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testSuccessWithFailuresPeekGetAttempt() throws Exception {
    FailingCallable callable = new FailingCallable(5, "SUCCESS");
    RetryingExecutor<String> executor = getExecutor(getAlgorithm(FAST_RETRY_SETTINGS, 0, null));
    RetryingFuture<String> future = executor.createFuture(callable);

    assertNull(future.peekAttemptResult());
    assertSame(future.peekAttemptResult(), future.peekAttemptResult());
    assertFalse(future.getAttemptResult().isDone());
    assertFalse(future.getAttemptResult().isCancelled());

    Exception exception = null;
    try {
      future.get(1L, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      exception = e;
    }
    assertNotNull(exception);

    future.setAttemptFuture(executor.submit(future));

    assertFutureSuccess(future);
    assertEquals(5, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testMaxRetriesExceeded() throws Exception {
    FailingCallable callable = new FailingCallable(6, "FAILURE");
    RetryingExecutor<String> executor = getExecutor(getAlgorithm(FAST_RETRY_SETTINGS, 0, null));
    RetryingFuture<String> future = executor.createFuture(callable);
    future.setAttemptFuture(executor.submit(future));

    assertFutureFail(future, CustomException.class);
    assertEquals(5, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testTotalTimeoutExceeded() throws Exception {
    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .setMaxRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .build();
    RetryingExecutor<String> executor = getExecutor(getAlgorithm(retrySettings, 0, null));
    FailingCallable callable = new FailingCallable(6, "FAILURE");
    RetryingFuture<String> future = executor.createFuture(callable);
    future.setAttemptFuture(executor.submit(future));

    assertFutureFail(future, CustomException.class);
    assertTrue(future.getAttemptSettings().getAttemptCount() < 4);
  }

  @Test
  public void testCancelOuterFutureBeforeStart() throws Exception {
    FailingCallable callable = new FailingCallable(4, "SUCCESS");

    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(1_000L))
            .setMaxRetryDelay(Duration.ofMillis(1_000L))
            .setTotalTimeout(Duration.ofMillis(10_0000L))
            .build();
    RetryingExecutor<String> executor = getExecutor(getAlgorithm(retrySettings, 0, null));
    RetryingFuture<String> future = executor.createFuture(callable);
    boolean res = future.cancel(false);

    assertTrue(res);

    future.setAttemptFuture(executor.submit(future));

    assertFutureCancel(future);
    assertEquals(0, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testCancelByRetryingAlgorithm() throws Exception {
    FailingCallable callable = new FailingCallable(6, "FAILURE");
    RetryingExecutor<String> executor =
        getExecutor(getAlgorithm(FAST_RETRY_SETTINGS, 5, new CancellationException()));
    RetryingFuture<String> future = executor.createFuture(callable);
    future.setAttemptFuture(executor.submit(future));

    assertFutureCancel(future);
    assertEquals(4, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testUnexpectedExceptionFromRetryAlgorithm() throws Exception {
    FailingCallable callable = new FailingCallable(6, "FAILURE");
    RetryingExecutor<String> executor =
        getExecutor(getAlgorithm(FAST_RETRY_SETTINGS, 5, new RuntimeException()));
    RetryingFuture<String> future = executor.createFuture(callable);
    future.setAttemptFuture(executor.submit(future));

    assertFutureFail(future, RuntimeException.class);
    assertEquals(4, future.getAttemptSettings().getAttemptCount());
  }

  @Test
  public void testPollExceptionByPollAlgorithm() throws Exception {
    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .setMaxRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .build();

    RetryAlgorithm<String> retryAlgorithm =
        new RetryAlgorithm<>(
            new TestResultRetryAlgorithm<String>(0, null),
            new ExponentialPollAlgorithm(retrySettings, NanoClock.getDefaultClock()));

    RetryingExecutor<String> executor = getExecutor(retryAlgorithm);
    FailingCallable callable = new FailingCallable(6, "FAILURE");
    RetryingFuture<String> future = executor.createFuture(callable);
    future.setAttemptFuture(executor.submit(future));

    assertFutureFail(future, PollException.class);
    assertTrue(future.getAttemptSettings().getAttemptCount() < 4);
  }

  protected static class TestResultRetryAlgorithm<ResponseT>
      extends BasicResultRetryAlgorithm<ResponseT> {
    private AtomicInteger apocalypseCountDown;
    private RuntimeException apocalypseException;

    TestResultRetryAlgorithm(int apocalypseCountDown, RuntimeException apocalypseException) {
      this.apocalypseCountDown =
          apocalypseCountDown > 0
              ? new AtomicInteger(apocalypseCountDown * 2)
              : new AtomicInteger(Integer.MAX_VALUE);
      this.apocalypseException = apocalypseException;
    }

    @Override
    public boolean shouldRetry(Throwable prevThrowable, ResponseT prevResponse) {
      if (apocalypseCountDown.decrementAndGet() == 0) {
        throw apocalypseException;
      }
      return super.shouldRetry(prevThrowable, prevResponse);
    }
  }

  void assertFutureSuccess(RetryingFuture<String> future)
      throws ExecutionException, InterruptedException, TimeoutException {
    assertEquals("SUCCESS", future.get(3, TimeUnit.SECONDS));
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());

    assertEquals("SUCCESS", future.peekAttemptResult().get(3, TimeUnit.SECONDS));
    assertSame(future.peekAttemptResult(), future.peekAttemptResult());
    assertTrue(future.peekAttemptResult().isDone());
    assertFalse(future.peekAttemptResult().isCancelled());

    assertEquals("SUCCESS", future.getAttemptResult().get(3, TimeUnit.SECONDS));
    assertSame(future.getAttemptResult(), future.getAttemptResult());
    assertTrue(future.getAttemptResult().isDone());
    assertFalse(future.getAttemptResult().isCancelled());

    String res = future.get();
    ApiFuture<?> gottentAttempt = future.getAttemptResult();
    ApiFuture<?> peekedAttempt = future.peekAttemptResult();

    // testing completed immutability
    assertFalse(future.cancel(true));
    assertFalse(future.cancel(false));
    assertSame(gottentAttempt, future.getAttemptResult());
    assertSame(peekedAttempt, future.peekAttemptResult());
    assertSame(res, future.get());
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());
  }

  void assertFutureFail(RetryingFuture<?> future, Class<? extends Throwable> exceptionClass)
      throws TimeoutException, InterruptedException {
    Throwable exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e.getCause();
    }
    assertNotNull(exception);
    assertEquals(exception.getClass(), exceptionClass);
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());

    try {
      future.peekAttemptResult().get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e.getCause();
    }
    assertNotNull(exception);
    assertEquals(exception.getClass(), exceptionClass);
    assertSame(future.peekAttemptResult(), future.peekAttemptResult());
    assertTrue(future.peekAttemptResult().isDone());
    assertFalse(future.peekAttemptResult().isCancelled());

    try {
      future.getAttemptResult().get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e.getCause();
    }
    assertNotNull(exception);
    assertEquals(exception.getClass(), exceptionClass);
    assertSame(future.getAttemptResult(), future.getAttemptResult());
    assertTrue(future.getAttemptResult().isDone());
    assertFalse(future.getAttemptResult().isCancelled());

    ApiFuture<?> gottentAttempt = future.getAttemptResult();
    ApiFuture<?> peekedAttempt = future.peekAttemptResult();

    // testing completed immutability
    assertFalse(future.cancel(true));
    assertFalse(future.cancel(false));
    assertSame(gottentAttempt, future.getAttemptResult());
    assertSame(peekedAttempt, future.peekAttemptResult());
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e.getCause();
    }
    assertNotNull(exception);
    assertEquals(exception.getClass(), exceptionClass);
    assertTrue(future.isDone());
    assertFalse(future.isCancelled());
  }

  void assertFutureCancel(RetryingFuture<?> future)
      throws ExecutionException, InterruptedException, TimeoutException {
    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (CancellationException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertTrue(future.isDone());
    assertTrue(future.isCancelled());

    try {
      future.getAttemptResult().get(3, TimeUnit.SECONDS);
    } catch (CancellationException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertSame(future.getAttemptResult(), future.getAttemptResult());
    assertTrue(future.getAttemptResult().isDone());
    assertTrue(future.getAttemptResult().isCancelled());
    try {
      future.peekAttemptResult().get(3, TimeUnit.SECONDS);
    } catch (CancellationException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertSame(future.peekAttemptResult(), future.peekAttemptResult());
    assertTrue(future.peekAttemptResult().isDone());
    assertTrue(future.peekAttemptResult().isCancelled());

    ApiFuture<?> gottentAttempt = future.getAttemptResult();
    ApiFuture<?> peekedAttempt = future.peekAttemptResult();

    // testing completed immutability
    assertFalse(future.cancel(true));
    assertFalse(future.cancel(false));
    assertSame(gottentAttempt, future.getAttemptResult());
    assertSame(peekedAttempt, future.peekAttemptResult());
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (CancellationException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertTrue(future.isDone());
    assertTrue(future.isCancelled());
  }
}
