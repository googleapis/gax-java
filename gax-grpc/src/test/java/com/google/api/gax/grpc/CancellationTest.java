/*
 * Copyright 2016, Google Inc. All rights reserved.
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
package com.google.api.gax.grpc;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

import com.google.api.core.AbstractApiFuture;
import com.google.api.core.ApiFuture;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.retrying.RetrySettings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import io.grpc.Status;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class CancellationTest {
  @SuppressWarnings("unchecked")
  private FutureCallable<Integer, Integer> callInt = Mockito.mock(FutureCallable.class);

  private static final RetrySettings FAST_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(2L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofMillis(2L))
          .setInitialRpcTimeout(Duration.ofMillis(2L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofMillis(2L))
          .setTotalTimeout(Duration.ofMillis(10L))
          .build();

  private static final RetrySettings SLOW_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(3000L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofMillis(3000L))
          .setInitialRpcTimeout(Duration.ofMillis(3000L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofMillis(3000L))
          .setTotalTimeout(Duration.ofMillis(3000L))
          .build();

  private FakeApiClock fakeClock;
  private RecordingScheduler executor;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void resetClock() {
    fakeClock = new FakeApiClock(System.nanoTime());
    executor = RecordingScheduler.create(fakeClock);
  }

  @After
  public void teardown() {
    executor.shutdownNow();
  }

  @Test
  public void cancellationBeforeGetOnRetryingCallable() throws Exception {
    thrown.expect(CancellationException.class);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(SettableApiFuture.<Integer>create());

    ImmutableSet<Status.Code> retryable = ImmutableSet.of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.create(callInt)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);

    ApiFuture<Integer> resultFuture = callable.futureCall(0);
    resultFuture.cancel(true);
    resultFuture.get();
  }

  private abstract static class LatchCountDownScheduler implements ScheduledExecutorService {
    private static LatchCountDownScheduler get(final CountDownLatch latch) {
      LatchCountDownScheduler mock = Mockito.mock(LatchCountDownScheduler.class);

      // mock class fields:
      final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

      // mock class methods:
      // ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);
      when(mock.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
          .then(
              new Answer<ScheduledFuture<?>>() {
                @Override
                public ScheduledFuture<?> answer(InvocationOnMock invocation) throws Throwable {
                  Object[] args = invocation.getArguments();
                  latch.countDown();
                  return executor.schedule((Runnable) args[0], (Long) args[1], (TimeUnit) args[2]);
                }
              });
      // List<Runnable> shutdownNow()
      when(mock.shutdownNow())
          .then(
              new Answer<List<Runnable>>() {
                @Override
                public List<Runnable> answer(InvocationOnMock invocation) throws Throwable {
                  return executor.shutdownNow();
                }
              });

      return mock;
    }
  }

  private static class CancellationTrackingFuture<RespT> extends AbstractApiFuture<RespT> {
    private volatile boolean cancelled = false;

    public static <RespT> CancellationTrackingFuture<RespT> create() {
      return new CancellationTrackingFuture<>();
    }

    private CancellationTrackingFuture() {}

    @Override
    protected void interruptTask() {
      cancelled = true;
    }

    public boolean isCancelled() {
      return cancelled;
    }
  }

  private static class LatchCountDownFutureCallable<RequestT, ResponseT>
      implements FutureCallable<RequestT, ResponseT> {
    private CountDownLatch callLatch;
    private List<ApiFuture<ResponseT>> injectedFutures;

    @SuppressWarnings("unchecked")
    public LatchCountDownFutureCallable(
        CountDownLatch callLatch, ApiFuture<ResponseT> injectedFuture) {
      this(callLatch, Lists.newArrayList(injectedFuture));
    }

    public LatchCountDownFutureCallable(
        CountDownLatch callLatch, List<ApiFuture<ResponseT>> injectedFutures) {
      this.callLatch = callLatch;
      this.injectedFutures = Lists.newArrayList(injectedFutures);
    }

    @Override
    public ApiFuture<ResponseT> futureCall(RequestT request, CallContext context) {
      callLatch.countDown();
      return injectedFutures.remove(0);
    }
  }

  @Test
  public void cancellationDuringFirstCall() throws Exception {
    CancellationTrackingFuture<Integer> innerFuture = CancellationTrackingFuture.<Integer>create();
    CountDownLatch callIssuedLatch = new CountDownLatch(1);
    FutureCallable<Integer, Integer> innerCallable =
        new LatchCountDownFutureCallable<>(callIssuedLatch, innerFuture);

    ImmutableSet<Status.Code> retryable = ImmutableSet.of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.create(innerCallable)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, new ScheduledThreadPoolExecutor(1), fakeClock);

    ApiFuture<Integer> resultFuture = callable.futureCall(0);
    CancellationHelpers.cancelInThreadAfterLatchCountDown(resultFuture, callIssuedLatch);
    CancellationException gotException = null;
    try {
      resultFuture.get();
    } catch (CancellationException e) {
      gotException = e;
    }
    Truth.assertThat(gotException).isNotNull();
    Truth.assertThat(innerFuture.isCancelled()).isTrue();
  }

  @Test
  public void cancellationDuringRetryDelay() throws Exception {
    Throwable throwable = Status.UNAVAILABLE.asException();
    CancellationTrackingFuture<Integer> innerFuture = CancellationTrackingFuture.create();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(UnaryCallableTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(innerFuture);

    CountDownLatch retryScheduledLatch = new CountDownLatch(1);
    LatchCountDownScheduler scheduler = LatchCountDownScheduler.get(retryScheduledLatch);
    ImmutableSet<Status.Code> retryable = ImmutableSet.of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.create(callInt)
            .retryableOn(retryable)
            .retrying(SLOW_RETRY_SETTINGS, scheduler, fakeClock);

    ApiFuture<Integer> resultFuture = callable.futureCall(0);
    CancellationHelpers.cancelInThreadAfterLatchCountDown(resultFuture, retryScheduledLatch);
    CancellationException gotException = null;
    try {
      resultFuture.get();
    } catch (CancellationException e) {
      gotException = e;
    }
    Truth.assertThat(gotException).isNotNull();
    Truth.assertThat(resultFuture.isDone()).isTrue();
    Truth.assertThat(resultFuture.isCancelled()).isTrue();
    Truth.assertThat(innerFuture.isCancelled()).isFalse();
  }

  @Test
  public void cancellationDuringSecondCall() throws Exception {
    Throwable throwable = Status.UNAVAILABLE.asException();
    ApiFuture<Integer> failingFuture = UnaryCallableTest.immediateFailedFuture(throwable);
    CancellationTrackingFuture<Integer> innerFuture = CancellationTrackingFuture.create();
    CountDownLatch callIssuedLatch = new CountDownLatch(2);
    @SuppressWarnings("unchecked")
    FutureCallable<Integer, Integer> innerCallable =
        new LatchCountDownFutureCallable<>(
            callIssuedLatch, Lists.newArrayList(failingFuture, innerFuture));

    ImmutableSet<Status.Code> retryable = ImmutableSet.of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.create(innerCallable)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, new ScheduledThreadPoolExecutor(1), fakeClock);

    ApiFuture<Integer> resultFuture = callable.futureCall(0);
    CancellationHelpers.cancelInThreadAfterLatchCountDown(resultFuture, callIssuedLatch);
    CancellationException gotException = null;
    try {
      resultFuture.get();
    } catch (CancellationException e) {
      gotException = e;
    }
    Truth.assertThat(gotException).isNotNull();
    Truth.assertThat(resultFuture.isDone()).isTrue();
    Truth.assertThat(resultFuture.isCancelled()).isTrue();
    Truth.assertThat(innerFuture.isDone()).isTrue();
  }
}
