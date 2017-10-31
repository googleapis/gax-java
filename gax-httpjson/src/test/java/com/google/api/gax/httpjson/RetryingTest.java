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
package com.google.api.gax.httpjson;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.core.RecordingScheduler;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.UnknownException;
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

  @SuppressWarnings("unchecked")
  private UnaryCallable<Integer, Integer> callInt = Mockito.mock(UnaryCallable.class);

  private RecordingScheduler executor;
  private FakeApiClock fakeClock;
  private ClientContext clientContext;

  private static int STATUS_SERVER_ERROR = 500;
  private static int STATUS_DEADLINE_EXCEEDED = 504;
  private static int STATUS_FAILED_PRECONDITION = 400;

  private static String DEADLINE_EXCEEDED = "DEADLINE_EXCEEDED";

  private HttpResponseException HTTP_SERVICE_UNAVAILABLE_EXCEPTION =
      new HttpResponseException.Builder(
              HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE,
              "server unavailable",
              new HttpHeaders())
          .build();

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
    clientContext =
        ClientContext.newBuilder()
            .setExecutor(executor)
            .setClock(fakeClock)
            .setDefaultCallContext(HttpJsonCallContext.createDefault())
            .build();
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
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(HTTP_SERVICE_UNAVAILABLE_EXCEPTION))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(HTTP_SERVICE_UNAVAILABLE_EXCEPTION))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(HTTP_SERVICE_UNAVAILABLE_EXCEPTION))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    UnaryCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test(expected = ApiException.class)
  public void retryTotalTimeoutExceeded() {
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNAVAILABLE);
    HttpResponseException httpResponseException =
        new HttpResponseException.Builder(
                HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE,
                "server unavailable",
                new HttpHeaders())
            .build();
    ApiException apiException =
        ApiExceptionFactory.createException(
            "foobar",
            httpResponseException,
            HttpJsonStatusCode.of(Code.FAILED_PRECONDITION),
            false);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(apiException))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .setMaxRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .build();
    UnaryCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test(expected = ApiException.class)
  public void retryMaxAttemptsExceeded() {
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(HTTP_SERVICE_UNAVAILABLE_EXCEPTION))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(HTTP_SERVICE_UNAVAILABLE_EXCEPTION))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings = FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(2).build();
    UnaryCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryWithinMaxAttempts() {
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(HTTP_SERVICE_UNAVAILABLE_EXCEPTION))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(HTTP_SERVICE_UNAVAILABLE_EXCEPTION))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings = FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(3).build();
    UnaryCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    callable.call(1);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnStatusUnknown() {
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNKNOWN);
    HttpResponseException throwable =
        new HttpResponseException.Builder(
                STATUS_SERVER_ERROR, "server unavailable", new HttpHeaders())
            .setMessage("UNKNOWN")
            .build();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));
    UnaryCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnUnexpectedException() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNKNOWN);
    Throwable throwable = new RuntimeException("foobar");
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable));
    UnaryCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryNoRecover() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNAVAILABLE);
    HttpResponseException httpResponseException =
        new HttpResponseException.Builder(STATUS_FAILED_PRECONDITION, "foobar", new HttpHeaders())
            .build();
    ApiException apiException =
        ApiExceptionFactory.createException(
            "foobar",
            httpResponseException,
            HttpJsonStatusCode.of(Code.FAILED_PRECONDITION),
            false);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(apiException))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));
    UnaryCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryKeepFailing() {
    thrown.expect(UncheckedExecutionException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                new HttpResponseException.Builder(
                        HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE,
                        "foobar",
                        new HttpHeaders())
                    .build()));
    UnaryCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    // Need to advance time inside the call.
    ApiFuture<Integer> future = callable.futureCall(1);
    Futures.getUnchecked(future);
  }

  @Test
  public void noSleepOnRetryTimeout() {
    ImmutableSet<StatusCode.Code> retryable =
        ImmutableSet.of(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                new HttpResponseException.Builder(
                        STATUS_DEADLINE_EXCEEDED, DEADLINE_EXCEEDED, new HttpHeaders())
                    .build()))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    UnaryCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    callable.call(1);
    Truth.assertThat(executor.getSleepDurations().size()).isEqualTo(1);
    Truth.assertThat(executor.getSleepDurations().get(0)).isEqualTo(Duration.ofMillis(1));
  }

  @Test
  public void testKnownStatusCode() {
    ImmutableSet<StatusCode.Code> retryable = ImmutableSet.of(Code.UNAVAILABLE);
    String throwableMessage =
        "{\n"
            + " \"error\": {\n"
            + "  \"errors\": [\n"
            + "   {\n"
            + "    \"domain\": \"global\",\n"
            + "    \"reason\": \"FAILED_PRECONDITION\",\n"
            + "   }\n"
            + "  ],\n"
            + "  \"code\": 400,\n"
            + "  \"message\": \"Failed precondition.\"\n"
            + " }\n"
            + "}";
    HttpResponseException throwable =
        new HttpResponseException.Builder(
                STATUS_FAILED_PRECONDITION,
                HttpJsonStatusCode.FAILED_PRECONDITION,
                new HttpHeaders())
            .setMessage(throwableMessage)
            .build();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable));
    UnaryCallSettings<Integer, Integer> callSettings =
        UnaryCallSettings.<Integer, Integer>newUnaryCallSettingsBuilder()
            .setRetryableCodes(retryable)
            .build();
    UnaryCallable<Integer, Integer> callable =
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    try {
      callable.call(1);
    } catch (FailedPreconditionException exception) {
      Truth.assertThat(((HttpJsonStatusCode) exception.getStatusCode()).getTransportCode())
          .isEqualTo(STATUS_FAILED_PRECONDITION);
      Truth.assertThat(exception.getMessage()).contains(HttpJsonStatusCode.FAILED_PRECONDITION);
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
        HttpJsonCallableFactory.createUnaryCallable(callInt, callSettings, clientContext);
    try {
      callable.call(1);
    } catch (UnknownException exception) {
      Truth.assertThat(exception.getMessage()).isEqualTo("java.lang.RuntimeException: unknown");
    }
  }

  public static UnaryCallSettings<Integer, Integer> createSettings(
      Set<StatusCode.Code> retryableCodes, RetrySettings retrySettings) {
    return UnaryCallSettings.<Integer, Integer>newUnaryCallSettingsBuilder()
        .setRetryableCodes(retryableCodes)
        .setRetrySettings(retrySettings)
        .build();
  }
}
