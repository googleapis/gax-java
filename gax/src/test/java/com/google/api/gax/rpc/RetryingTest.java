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
package com.google.api.gax.rpc;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.core.RecordingScheduler;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.api.gax.rpc.testing.FakeStatusException;
import com.google.api.gax.rpc.testing.FakeTransportDescriptor;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class RetryingTest {
  private CallableFactory callableFactory =
      CallableFactory.create(FakeTransportDescriptor.create());

  @SuppressWarnings("unchecked")
  private UnaryCallable<Integer, Integer> callInt = Mockito.mock(UnaryCallable.class);

  private RecordingScheduler executor;
  private FakeApiClock fakeClock;
  private ClientContext clientContext;

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

  @Before
  public void resetClock() {
    fakeClock = new FakeApiClock(System.nanoTime());
    executor = RecordingScheduler.create(fakeClock);
    clientContext = ClientContext.newBuilder().setExecutor(executor).setClock(fakeClock).build();
  }

  @After
  public void teardown() {
    executor.shutdownNow();
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  static <V> ApiFuture<V> immediateFailedFuture(Throwable t) {
    return ApiFutures.<V>immediateFailedFuture(t);
  }

  @Test
  public void retry() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNAVAILABLE));
    Throwable throwable = new FakeStatusException(FakeStatusCode.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test(expected = ApiException.class)
  public void retryTotalTimeoutExceeded() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNAVAILABLE));
    Throwable throwable = new FakeStatusException(FakeStatusCode.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .setMaxRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .build();
    SimpleCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test(expected = ApiException.class)
  public void retryMaxAttemptsExceeded() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNAVAILABLE));
    Throwable throwable = new FakeStatusException(FakeStatusCode.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings = FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(2).build();
    SimpleCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryWithinMaxAttempts() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNAVAILABLE));
    Throwable throwable = new FakeStatusException(FakeStatusCode.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings = FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(3).build();
    SimpleCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnStatusUnknown() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNKNOWN));
    Throwable throwable = new FakeStatusException(FakeStatusCode.Code.UNKNOWN);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));
    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnUnexpectedException() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNKNOWN));
    Throwable throwable = new RuntimeException("foobar");
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable));
    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryNoRecover() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNAVAILABLE));
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                new FakeStatusException("foobar", null, FakeStatusCode.Code.FAILED_PRECONDITION)))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));
    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryKeepFailing() {
    thrown.expect(UncheckedExecutionException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNAVAILABLE));
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                new FakeStatusException("foobar", null, FakeStatusCode.Code.UNAVAILABLE)));
    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    // Need to advance time inside the call.
    ApiFuture<Integer> future = callable.futureCall(1);
    Futures.getUnchecked(future);
  }

  @Test
  public void noSleepOnRetryTimeout() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(
            FakeStatusCode.of(FakeStatusCode.Code.UNAVAILABLE),
            FakeStatusCode.of(FakeStatusCode.Code.DEADLINE_EXCEEDED));
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                new FakeStatusException(
                    "DEADLINE_EXCEEDED", null, FakeStatusCode.Code.DEADLINE_EXCEEDED)))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
    Truth.assertThat(executor.getSleepDurations().size()).isEqualTo(1);
    Truth.assertThat(executor.getSleepDurations().get(0))
        .isEqualTo(ApiResultRetryAlgorithm.DEADLINE_SLEEP_DURATION);
  }

  @Test
  public void testKnownStatusCode() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(FakeStatusCode.of(FakeStatusCode.Code.UNAVAILABLE));
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                new FakeStatusException("known", null, FakeStatusCode.Code.FAILED_PRECONDITION)));
    SimpleCallSettings<Integer, Integer> callSettings =
        SimpleCallSettings.<Integer, Integer>newBuilder().setRetryableCodes(retryable).build();
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    try {
      callable.call(1);
    } catch (FailedPreconditionException exception) {
      Truth.assertThat(exception.getMessage())
          .isEqualTo(
              "com.google.api.gax.rpc.testing.FakeStatusException: FAILED_PRECONDITION: known");
    }
  }

  @Test
  public void testUnknownStatusCode() {
    ImmutableSet<StatusCode> retryable = ImmutableSet.<StatusCode>of();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(new RuntimeException("unknown")));
    SimpleCallSettings<Integer, Integer> callSettings =
        SimpleCallSettings.<Integer, Integer>newBuilder().setRetryableCodes(retryable).build();
    UnaryCallable<Integer, Integer> callable =
        callableFactory.create(callInt, callSettings, clientContext);
    try {
      callable.call(1);
    } catch (UnknownException exception) {
      Truth.assertThat(exception.getMessage()).isEqualTo("java.lang.RuntimeException: unknown");
    }
  }

  public static SimpleCallSettings<Integer, Integer> createSettings(
      Set<StatusCode> retryableCodes, RetrySettings retrySettings) {
    return SimpleCallSettings.<Integer, Integer>newBuilder()
        .setRetryableCodes(retryableCodes)
        .setRetrySettings(retrySettings)
        .build();
  }
}
