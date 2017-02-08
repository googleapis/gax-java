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

import com.google.api.gax.core.RetrySettings;
import com.google.api.gax.core.RpcFuture;
import com.google.api.gax.grpc.UnaryCallable.Scheduler;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.SettableFuture;
import io.grpc.Status;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class CancellationTest {
  @SuppressWarnings("unchecked")
  private FutureCallable<Integer, Integer> callInt = Mockito.mock(FutureCallable.class);

  private static final RetrySettings FAST_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.millis(2L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.millis(2L))
          .setInitialRpcTimeout(Duration.millis(2L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.millis(2L))
          .setTotalTimeout(Duration.millis(10L))
          .build();

  private static final RetrySettings SLOW_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.millis(3000L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.millis(3000L))
          .setInitialRpcTimeout(Duration.millis(3000L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.millis(3000L))
          .setTotalTimeout(Duration.millis(3000L))
          .build();

  private FakeNanoClock fakeClock;
  private RecordingScheduler executor;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void resetClock() {
    fakeClock = new FakeNanoClock(System.nanoTime());
    executor = new RecordingScheduler(fakeClock);
  }

  @After
  public void teardown() {
    executor.shutdownNow();
  }

  @Test
  public void cancellationBeforeGetOnRetryingCallable() throws Exception {
    thrown.expect(CancellationException.class);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(new ListenableFutureDelegate(SettableFuture.<Integer>create()));

    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);

    RpcFuture<Integer> resultFuture = callable.futureCall(0);
    resultFuture.cancel(true);
    resultFuture.get();
  }

  private static class LatchCountDownScheduler implements UnaryCallable.Scheduler {
    private final ScheduledExecutorService executor;
    private final CountDownLatch latch;

    LatchCountDownScheduler(CountDownLatch latch) {
      this.executor = new ScheduledThreadPoolExecutor(1);
      this.latch = latch;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
      latch.countDown();
      return executor.schedule(runnable, delay, unit);
    }

    @Override
    public List<Runnable> shutdownNow() {
      return executor.shutdownNow();
    }
  }

  private static class CancellationTrackingFuture<RespT> extends AbstractRpcFuture<RespT> {
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
    private List<RpcFuture<ResponseT>> injectedFutures;

    @SuppressWarnings("unchecked")
    public LatchCountDownFutureCallable(
        CountDownLatch callLatch, RpcFuture<ResponseT> injectedFuture) {
      this(callLatch, Lists.newArrayList(injectedFuture));
    }

    public LatchCountDownFutureCallable(
        CountDownLatch callLatch, List<RpcFuture<ResponseT>> injectedFutures) {
      this.callLatch = callLatch;
      this.injectedFutures = Lists.newArrayList(injectedFutures);
    }

    @Override
    public RpcFuture<ResponseT> futureCall(RequestT request, CallContext context) {
      callLatch.countDown();
      return injectedFutures.remove(0);
    }
  }

  @Test
  public void cancellationDuringFirstCall() throws Exception {
    CancellationTrackingFuture<Integer> innerFuture = CancellationTrackingFuture.<Integer>create();
    CountDownLatch callIssuedLatch = new CountDownLatch(1);
    FutureCallable<Integer, Integer> innerCallable =
        new LatchCountDownFutureCallable<Integer, Integer>(callIssuedLatch, innerFuture);

    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(innerCallable)
            .retryableOn(retryable)
            .retrying(
                FAST_RETRY_SETTINGS,
                new UnaryCallable.DelegatingScheduler(new ScheduledThreadPoolExecutor(1)),
                fakeClock);

    RpcFuture<Integer> resultFuture = callable.futureCall(0);
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
    CancellationTrackingFuture<Integer> innerFuture = CancellationTrackingFuture.<Integer>create();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(UnaryCallableTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(innerFuture);

    CountDownLatch retryScheduledLatch = new CountDownLatch(1);
    Scheduler scheduler = new LatchCountDownScheduler(retryScheduledLatch);
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(SLOW_RETRY_SETTINGS, scheduler, fakeClock);

    RpcFuture<Integer> resultFuture = callable.futureCall(0);
    CancellationHelpers.cancelInThreadAfterLatchCountDown(resultFuture, retryScheduledLatch);
    CancellationException gotException = null;
    try {
      resultFuture.get();
    } catch (CancellationException e) {
      gotException = e;
    }
    Truth.assertThat(gotException).isNotNull();
    Truth.assertThat(innerFuture.isCancelled()).isFalse();
  }

  @Test
  public void cancellationDuringSecondCall() throws Exception {
    Throwable throwable = Status.UNAVAILABLE.asException();
    RpcFuture<Integer> failingFuture = UnaryCallableTest.immediateFailedFuture(throwable);
    CancellationTrackingFuture<Integer> innerFuture = CancellationTrackingFuture.<Integer>create();
    CountDownLatch callIssuedLatch = new CountDownLatch(2);
    @SuppressWarnings("unchecked")
    FutureCallable<Integer, Integer> innerCallable =
        new LatchCountDownFutureCallable<Integer, Integer>(
            callIssuedLatch, Lists.<RpcFuture<Integer>>newArrayList(failingFuture, innerFuture));

    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(innerCallable)
            .retryableOn(retryable)
            .retrying(
                FAST_RETRY_SETTINGS,
                new UnaryCallable.DelegatingScheduler(new ScheduledThreadPoolExecutor(1)),
                fakeClock);

    RpcFuture<Integer> resultFuture = callable.futureCall(0);
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
}
