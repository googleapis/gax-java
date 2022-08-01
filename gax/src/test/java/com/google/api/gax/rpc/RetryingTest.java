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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.core.RecordingScheduler;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeCallableFactory;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class RetryingTest {

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
  private static final RetrySettings FAILING_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setMaxAttempts(2)
          .setInitialRetryDelay(Duration.ofNanos(0L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofMillis(0L))
          .setInitialRpcTimeout(Duration.ofNanos(1L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofNanos(1L))
          .setTotalTimeout(Duration.ofNanos(1L))
          .build();

  @Before
  public void resetClock() {
    fakeClock = new FakeApiClock(System.nanoTime());
    executor = RecordingScheduler.create(fakeClock);
    clientContext =
        ClientContext.newBuilder()
            .setExecutor(executor)
            .setClock(fakeClock)
            .setDefaultCallContext(FakeCallContext.createDefault())
            .setTransportChannel(FakeTransportChannel.create(new FakeChannel()))
            .build();
  }

  @After
  public void teardown() {
    executor.shutdownNow();
  }

  static <V> ApiFuture<V> immediateFailedFuture(Throwable t) {
    return ApiFutures.<V>immediateFailedFuture(t);
  }

  @Test
  public void retry() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.UNAVAILABLE), true);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    assertRetrying(FAST_RETRY_SETTINGS);
  }

  @Test
  public void retryUsingContext() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.INTERNAL), false);
    Mockito.when(callInt.futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    assertRetryingUsingContext(
        FAILING_RETRY_SETTINGS,
        FakeCallContext.createDefault()
            .withRetrySettings(FAST_RETRY_SETTINGS)
            .withRetryableCodes(Sets.newHashSet(StatusCode.Code.INTERNAL)));
  }

  @Test(expected = ApiException.class)
  public void retryTotalTimeoutExceeded() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.UNAVAILABLE), true);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .setMaxRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .build();

    assertRetrying(retrySettings);
  }

  @Test
  public void retryUsingContextTotalTimeoutExceeded() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.INTERNAL), false);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .setMaxRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .build();

    try {
      assertRetryingUsingContext(
          FAILING_RETRY_SETTINGS,
          FakeCallContext.createDefault()
              .withRetrySettings(retrySettings)
              .withRetryableCodes(Sets.newHashSet(StatusCode.Code.INTERNAL)));
      fail("missing expected exception");
    } catch (ApiException e) {
      assertEquals(Code.INTERNAL, e.getStatusCode().getCode());
    }
  }

  @Test(expected = ApiException.class)
  public void retryMaxAttemptsExceeded() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.UNAVAILABLE), true);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    assertRetrying(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(2).build());
  }

  @Test
  public void retryUsingContextMaxAttemptsExceeded() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.INTERNAL), false);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    try {
      assertRetryingUsingContext(
          FAILING_RETRY_SETTINGS,
          FakeCallContext.createDefault()
              .withRetrySettings(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(2).build())
              .withRetryableCodes(Sets.newHashSet(StatusCode.Code.INTERNAL)));
      fail("missing expected exception");
    } catch (ApiException e) {
      assertEquals(Code.INTERNAL, e.getStatusCode().getCode());
    }
  }

  @Test
  public void retryWithinMaxAttempts() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.UNAVAILABLE), true);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    assertRetrying(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(3).build());
  }

  @Test
  public void retryUsingContextWithinMaxAttempts() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.INTERNAL), false);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    assertRetryingUsingContext(
        FAILING_RETRY_SETTINGS,
        FakeCallContext.createDefault()
            .withRetrySettings(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(3).build())
            .withRetryableCodes(Sets.newHashSet(StatusCode.Code.INTERNAL)));
  }

  @Test
  public void retryWithOnlyMaxAttempts() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.UNAVAILABLE), true);
    Mockito.when(callInt.futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.immediateFuture(2));

    RetrySettings retrySettings = RetrySettings.newBuilder().setMaxAttempts(3).build();

    assertRetrying(retrySettings);
    Mockito.verify(callInt, Mockito.times(3))
        .futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any());
  }

  @Test
  public void retryUsingContextWithOnlyMaxAttempts() {
    Throwable throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.INTERNAL), false);
    Mockito.when(callInt.futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.immediateFuture(2));

    RetrySettings retrySettings = RetrySettings.newBuilder().setMaxAttempts(3).build();

    assertRetryingUsingContext(
        FAILING_RETRY_SETTINGS,
        FakeCallContext.createDefault()
            .withRetrySettings(retrySettings)
            .withRetryableCodes(Sets.newHashSet(StatusCode.Code.INTERNAL)));
    Mockito.verify(callInt, Mockito.times(3))
        .futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any());
  }

  @Test
  public void retryWithoutRetrySettings() {
    Mockito.when(callInt.futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any()))
        .thenReturn(ApiFutures.immediateFuture(2));

    RetrySettings retrySettings = RetrySettings.newBuilder().build();

    assertRetrying(retrySettings);
    Mockito.verify(callInt).futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any());
  }

  @Test
  public void retryUsingContextWithoutRetrySettings() {
    Mockito.when(callInt.futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any()))
        .thenReturn(ApiFutures.immediateFuture(2));

    RetrySettings retrySettings = RetrySettings.newBuilder().build();

    assertRetryingUsingContext(
        FAILING_RETRY_SETTINGS, FakeCallContext.createDefault().withRetrySettings(retrySettings));
    Mockito.verify(callInt).futureCall(Mockito.<Integer>any(), Mockito.<ApiCallContext>any());
  }

  @Test
  public void retryOnStatusUnknown() {
    Throwable throwable =
        new UnknownException(null, FakeStatusCode.of(StatusCode.Code.UNKNOWN), true);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    assertRetrying(FAST_RETRY_SETTINGS);
  }

  @Test
  public void retryOnUnexpectedException() {
    Throwable throwable =
        new UnknownException("foobar", null, FakeStatusCode.of(StatusCode.Code.UNKNOWN), false);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable));
    try {
      assertRetrying(FAST_RETRY_SETTINGS);
      Assert.fail("Callable should have thrown an exception");
    } catch (ApiException expected) {
      Truth.assertThat(expected).isSameInstanceAs(throwable);
    }
  }

  @Test
  public void retryNoRecover() {
    Throwable throwable =
        new FailedPreconditionException(
            "foobar", null, FakeStatusCode.of(StatusCode.Code.FAILED_PRECONDITION), false);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));
    try {
      assertRetrying(FAST_RETRY_SETTINGS);
      Assert.fail("Callable should have thrown an exception");
    } catch (ApiException expected) {
      Truth.assertThat(expected).isSameInstanceAs(throwable);
    }
  }

  @Test
  public void retryUsingContextNoRecover() {
    Throwable throwable =
        new FailedPreconditionException(
            "foobar", null, FakeStatusCode.of(StatusCode.Code.FAILED_PRECONDITION), false);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));
    try {
      assertRetryingUsingContext(
          FAILING_RETRY_SETTINGS,
          FakeCallContext.createDefault()
              .withRetrySettings(FAST_RETRY_SETTINGS)
              .withRetryableCodes(
                  Sets.newHashSet(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.UNKNOWN)));
      Assert.fail("Callable should have thrown an exception");
    } catch (ApiException expected) {
      Truth.assertThat(expected).isSameInstanceAs(throwable);
    }
  }

  @Test
  public void retryKeepFailing() {
    Throwable throwable =
        new UnavailableException(
            "foobar", null, FakeStatusCode.of(StatusCode.Code.UNAVAILABLE), true);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable));
    UnaryCallSettings<Integer, Integer> callSettings = createSettings(FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        FakeCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    // Need to advance time inside the call.
    ApiFuture<Integer> future = callable.futureCall(1);
    try {
      Futures.getUnchecked(future);
      Assert.fail("Callable should have thrown an exception");
    } catch (UncheckedExecutionException expected) {
      Truth.assertThat(expected).hasCauseThat().isSameInstanceAs(throwable);
    }
  }

  @Test
  public void testKnownStatusCode() {
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(StatusCode.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                new FailedPreconditionException(
                    "known", null, FakeStatusCode.of(StatusCode.Code.FAILED_PRECONDITION), false)));
    UnaryCallSettings<Integer, Integer> callSettings =
        UnaryCallSettings.<Integer, Integer>newUnaryCallSettingsBuilder()
            .setRetryableCodes(retryable)
            .build();
    UnaryCallable<Integer, Integer> callable =
        FakeCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    try {
      callable.call(1);
      Assert.fail("Callable should have thrown an exception");
    } catch (FailedPreconditionException expected) {
      Truth.assertThat(expected.getMessage()).isEqualTo("known");
    }
  }

  @Test
  public void testUnknownStatusCode() {
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(new RuntimeException("unknown")));
    UnaryCallSettings<Integer, Integer> callSettings =
        UnaryCallSettings.<Integer, Integer>newUnaryCallSettingsBuilder()
            .setRetryableCodes(retryable)
            .build();
    UnaryCallable<Integer, Integer> callable =
        FakeCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    try {
      callable.call(1);
      Assert.fail("Callable should have thrown an exception");
    } catch (RuntimeException expected) {
      Truth.assertThat(expected).isInstanceOf(RuntimeException.class);
    }
  }

  public static UnaryCallSettings<Integer, Integer> createSettings(RetrySettings retrySettings) {
    return UnaryCallSettings.<Integer, Integer>newUnaryCallSettingsBuilder()
        .setRetryableCodes(Code.UNAVAILABLE)
        .setRetrySettings(retrySettings)
        .build();
  }

  private void assertRetrying(RetrySettings retrySettings) {
    UnaryCallSettings<Integer, Integer> callSettings = createSettings(retrySettings);
    UnaryCallable<Integer, Integer> callable =
        FakeCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  private void assertRetryingUsingContext(RetrySettings retrySettings, ApiCallContext context) {
    UnaryCallSettings<Integer, Integer> callSettings = createSettings(retrySettings);
    UnaryCallable<Integer, Integer> callable =
        FakeCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    Truth.assertThat(callable.call(1, context)).isEqualTo(2);
  }
}
