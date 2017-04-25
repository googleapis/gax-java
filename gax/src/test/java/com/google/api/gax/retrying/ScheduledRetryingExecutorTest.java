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
import static org.junit.Assert.assertTrue;

import com.google.api.core.NanoClock;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class ScheduledRetryingExecutorTest extends AbstractRetryingExecutorTest {
  private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

  @After
  public void after() {
    executorService.shutdownNow();
  }

  @Override
  protected RetryingExecutor<String> getRetryingExecutor(RetrySettings retrySettings) {
    RetryAlgorithm retryAlgorithm =
        new RetryAlgorithm(
            getNoOpExceptionRetryAlgorithm(),
            new ExponentialRetryAlgorithm(retrySettings, NanoClock.getDefaultClock()));

    return new ScheduledRetryingExecutor<>(retryAlgorithm, executorService);
  }

  @Test(expected = CancellationException.class)
  public void testCancelOuterFutureAfterStart() throws ExecutionException, InterruptedException {
    FailingCallable callable = new FailingCallable(4, "SUCCESS");
    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(1_000L))
            .setMaxRetryDelay(Duration.ofMillis(1_000L))
            .setTotalTimeout(Duration.ofMillis(10_0000L))
            .build();
    RetryingExecutor<String> executor = getRetryingExecutor(retrySettings);
    RetryingFuture<String> future = executor.createFuture(callable);
    executor.submit(future);

    future.cancel(false);
    assertTrue(future.isDone());
    assertTrue(future.isCancelled());
    assertTrue(future.getAttemptSettings().getAttemptCount() < 4);
    future.get();
  }

  @Test(expected = CancellationException.class)
  public void testCancelProxiedFutureAfterStart() throws ExecutionException, InterruptedException {
    FailingCallable callable = new FailingCallable(5, "SUCCESS");
    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(1_000L))
            .setMaxRetryDelay(Duration.ofMillis(1_000L))
            .setTotalTimeout(Duration.ofMillis(10_0000L))
            .build();
    RetryingExecutor<String> executor = getRetryingExecutor(retrySettings);
    RetryingFuture<String> future = executor.createFuture(callable);
    executor.submit(future);

    Thread.sleep(50L);
    RetryingExecutor<String> handler = getRetryingExecutor(retrySettings);

    //Note that shutdownNow() will not cancel internal FutureTasks automatically, which
    //may potentially cause another thread handing on RetryingFuture#get() call forever.
    //Canceling the tasks returned by shutdownNow() also does not help, because of missing feature
    //in guava's ListenableScheduledFuture, which does not cancel itself, when its delegate is canceled.
    //So only the graceful shutdown() is supported properly.
    executorService.shutdown();

    try {
      future.get();
    } catch (CancellationException e) {
      //Used to wait for cancellation to propagate, so isDone() is guaranteed to return true.
    }
    assertTrue(future.isDone());
    assertTrue(future.isCancelled());
    assertTrue(future.getAttemptSettings().getAttemptCount() < 4);
    future.get();
  }
}
