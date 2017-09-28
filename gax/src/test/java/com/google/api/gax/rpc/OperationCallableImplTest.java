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
package com.google.api.gax.rpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.ListenableFutureToApiFuture;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.core.RecordingScheduler;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationFutureImpl;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.longrunning.OperationTimedPollAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.testing.FakeApiCallContext;
import com.google.api.gax.rpc.testing.FakeApiExceptionFactory;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeOperationSnapshot;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.api.gax.rpc.testing.FakeStatusCode.Code;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.api.gax.rpc.testing.FakeTransportDescriptor;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import java.awt.Color;
import java.io.IOException;
import java.util.Currency;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class OperationCallableImplTest {
  private CallableFactory callableFactory =
      CallableFactory.create(FakeTransportDescriptor.create());

  private static final RetrySettings FAST_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(1L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofMillis(1L))
          .setInitialRpcTimeout(Duration.ofMillis(1L))
          .setMaxAttempts(0)
          .setJittered(false)
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofMillis(1L))
          .setTotalTimeout(Duration.ofMillis(5L))
          .build();

  private FakeChannel initialChannel;
  private TransportChannel pollTransportChannel;
  private RecordingScheduler executor;
  private ClientContext initialContext;
  private OperationCallSettings<Integer, Color, Currency> callSettings;

  private FakeApiClock clock;
  private OperationTimedPollAlgorithm pollingAlgorithm;

  @Before
  public void setUp() throws IOException {
    initialChannel = mock(FakeChannel.class);
    pollTransportChannel = mock(TransportChannel.class);
    TransportChannelProvider operationsChannelProvider = mock(TransportChannelProvider.class);
    when(operationsChannelProvider.getTransportChannel()).thenReturn(pollTransportChannel);

    clock = new FakeApiClock(0L);
    executor = RecordingScheduler.create(clock);
    pollingAlgorithm = new OperationTimedPollAlgorithm(FAST_RETRY_SETTINGS, clock);

    SimpleCallSettings<Integer, OperationSnapshot> initialCallSettings =
        SimpleCallSettings.<Integer, OperationSnapshot>newBuilder()
            .setRetrySettings(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(1).build())
            .build();

    callSettings =
        OperationCallSettings.<Integer, Color, Currency>newBuilder()
            .setInitialCallSettings(initialCallSettings)
            .setResponseTransformer(new ResponseTransformer())
            .setMetadataTransformer(new MetadataTransformer())
            .setPollingAlgorithm(pollingAlgorithm)
            .build();

    initialContext = getClientContext(initialChannel, executor);
  }

  private static class ResponseTransformer implements ApiFunction<OperationSnapshot, Color> {
    @Override
    public Color apply(OperationSnapshot operationSnapshot) {
      FakeStatusCode fakeStatusCode = (FakeStatusCode) operationSnapshot.getErrorCode();
      Code statusCode = fakeStatusCode.getCode();
      if (!statusCode.equals(Code.OK)) {
        throw FakeApiExceptionFactory.createException(
            "Operation with name \""
                + operationSnapshot.getName()
                + "\" failed with status = "
                + statusCode,
            null,
            statusCode,
            false);
      }
      if (operationSnapshot.getResponse() == null) {
        return null;
      }
      if (!(operationSnapshot.getResponse() instanceof Color)) {
        String errorMessage =
            "type mismatch: expected "
                + Color.class.getName()
                + ", found "
                + operationSnapshot.getResponse().getClass().getName();
        throw new ApiException(errorMessage, null, FakeStatusCode.of(Code.OK), false);
      } else {
        return (Color) operationSnapshot.getResponse();
      }
    }
  }

  private static class MetadataTransformer implements ApiFunction<OperationSnapshot, Currency> {
    @Override
    public Currency apply(OperationSnapshot operationSnapshot) {
      if (operationSnapshot.getMetadata() == null) {
        return null;
      }
      if (!(operationSnapshot.getMetadata() instanceof Currency)) {
        String errorMessage =
            "type mismatch: expected "
                + Currency.class.getName()
                + ", found "
                + operationSnapshot.getMetadata().getClass().getName();
        throw new ApiException(errorMessage, null, FakeStatusCode.of(Code.OK), false);
      } else {
        return (Currency) operationSnapshot.getMetadata();
      }
    }
  }

  @After
  public void tearDown() throws Exception {
    executor.shutdown();
  }

  @Test
  public void testCall() {
    Color resp = getColor(1.0f);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot resultOperation = getOperation("testCall", resp, null, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, resultOperation);
    LongRunningClient longRunningClient = new UnsupportedOperationApi();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    Color response = callable.call(2, FakeApiCallContext.of());
    Truth.assertThat(response).isEqualTo(resp);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testResumeFutureCall() throws Exception {
    String opName = "testResumeFutureCall";
    Color resp = getColor(0.5f);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot resultOperation = getOperation(opName, resp, null, meta, true);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, resultOperation);

    ClientContext mockContext = getClientContext(new FakeChannel(), executor);
    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(
            getUnexpectedStartCallable(), callSettings, mockContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.resumeFutureCall(opName);

    assertFutureSuccessMetaSuccess(opName, future, resp, meta);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testCancelOperation() throws Exception {
    String opName = "testCancelOperation";
    LongRunningClient longRunningClient = mockCancelOperation(Code.OK);

    ClientContext mockContext = getClientContext(new FakeChannel(), executor);
    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(
            getUnexpectedStartCallable(), callSettings, mockContext, longRunningClient);

    ApiFuture<Void> future = callable.cancel(opName);
    assertThat(future.get()).isNull();
  }

  @Test
  public void testFutureCallInitialDone() throws Exception {
    String opName = "testFutureCallInitialDone";
    Color resp = getColor(0.5f);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot resultOperation = getOperation(opName, resp, null, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, resultOperation);
    LongRunningClient longRunningClient = new UnsupportedOperationApi();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureSuccessMetaSuccess(opName, future, resp, meta);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialError() throws Exception {
    String opName = "testFutureCallInitialError";
    Color resp = getColor(1.0f);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot resultOperation = getOperation(opName, resp, null, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.UNAVAILABLE, resultOperation);
    LongRunningClient longRunningClient = new UnsupportedOperationApi();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureFailMetaFail(future, null, FakeStatusCode.of(Code.UNAVAILABLE));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialDoneWithError() throws Exception {
    String opName = "testFutureCallInitialDoneWithError";
    StatusCode errorCode = FakeStatusCode.of(Code.ALREADY_EXISTS);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot resultOperation = getOperation(opName, null, errorCode, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, resultOperation);
    LongRunningClient longRunningClient = new UnsupportedOperationApi();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureFailMetaSuccess(future, meta, FakeStatusCode.of(Code.ALREADY_EXISTS));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialDoneWrongType() throws Exception {
    String opName = "testFutureCallInitialDoneWrongType";
    Currency resp = Currency.getInstance("USD");
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot resultOperation = getOperation(opName, resp, null, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, resultOperation);
    LongRunningClient longRunningClient = new UnsupportedOperationApi();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureFailMetaSuccess(future, meta, FakeStatusCode.of(Code.OK));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialDoneMetaWrongType() throws Exception {
    String opName = "testFutureCallInitialDoneMetaWrongType";
    Color resp = getColor(1.0f);
    Color meta = getColor(1.0f);
    OperationSnapshot resultOperation = getOperation(opName, resp, null, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, resultOperation);
    LongRunningClient longRunningClient = new UnsupportedOperationApi();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureSuccessMetaFail(future, resp, FakeStatusCode.of(Code.OK));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialCancel() throws Exception {
    String opName = "testFutureCallInitialCancel";
    OperationSnapshot initialOperation = getOperation(opName, null, null, null, false);
    OperationSnapshot resultOperation = getOperation(opName, null, null, null, false);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, resultOperation);

    OperationCallableImpl<Integer, Color, Currency> callableImpl =
        callableFactory.createImpl(
            initialCallable, callSettings, initialContext, longRunningClient);

    OperationFutureImpl<Color, Currency> future =
        callableImpl.futureCall(
            new ListenableFutureToApiFuture<>(
                Futures.<OperationSnapshot>immediateCancelledFuture()));

    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (CancellationException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isTrue();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isTrue();

    assertFutureCancelMetaCancel(future);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialOperationUnexpectedFail() throws Exception {
    String opName = "testFutureCallInitialOperationUnexpectedFail";
    OperationSnapshot initialOperation = getOperation(opName, null, null, null, false);
    OperationSnapshot resultOperation = getOperation(opName, null, null, null, false);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, resultOperation);

    OperationCallableImpl<Integer, Color, Currency> callableImpl =
        callableFactory.createImpl(
            initialCallable, callSettings, initialContext, longRunningClient);

    RuntimeException thrownException = new RuntimeException();

    OperationFuture<Color, Currency> future =
        callableImpl.futureCall(
            new ListenableFutureToApiFuture<>(
                Futures.<OperationSnapshot>immediateFailedFuture(thrownException)));

    assertFutureFailMetaFail(future, RuntimeException.class, null);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneOnFirst() throws Exception {
    String opName = "testFutureCallPollDoneOnFirst";
    Color resp = getColor(0.5f);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot initialOperation = getOperation(opName, null, null, null, false);
    OperationSnapshot resultOperation = getOperation(opName, resp, null, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, resultOperation);

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureSuccessMetaSuccess(opName, future, resp, meta);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneOnSecond() throws Exception {
    String opName = "testFutureCallPollDoneOnSecond";
    Color resp = getColor(0.5f);
    Currency meta1 = Currency.getInstance("UAH");
    Currency meta2 = Currency.getInstance("USD");
    OperationSnapshot initialOperation = getOperation(opName, null, null, null, false);
    OperationSnapshot resultOperation1 = getOperation(opName, null, null, meta1, false);
    OperationSnapshot resultOperation2 = getOperation(opName, resp, null, meta2, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    LongRunningClient longRunningClient =
        mockGetOperation(Code.OK, resultOperation1, resultOperation2);

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureSuccessMetaSuccess(opName, future, resp, meta2);
    assertThat(executor.getIterationsCount()).isEqualTo(1);
  }

  @Test
  public void testFutureCallPollDoneOnMany() throws Exception {
    final int iterationsCount = 1000;
    String opName = "testFutureCallPollDoneOnMany";
    Color resp = getColor(0.5f);
    Currency meta = Currency.getInstance("UAH");

    OperationSnapshot initialOperation = getOperation(opName, null, null, null, false);

    OperationSnapshot[] pollOperations = new OperationSnapshot[iterationsCount];
    for (int i = 0; i < iterationsCount - 1; i++) {
      pollOperations[i] = getOperation(opName, null, null, meta, false);
    }
    pollOperations[iterationsCount - 1] = getOperation(opName, resp, null, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, pollOperations);

    pollingAlgorithm =
        new OperationTimedPollAlgorithm(
            FAST_RETRY_SETTINGS
                .toBuilder()
                .setTotalTimeout(Duration.ofMillis(iterationsCount))
                .build(),
            clock);
    callSettings = callSettings.toBuilder().setPollingAlgorithm(pollingAlgorithm).build();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    Truth.assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(resp);
    assertFutureSuccessMetaSuccess(opName, future, resp, meta);

    assertThat(executor.getIterationsCount()).isEqualTo(iterationsCount - 1);
  }

  @Test
  public void testFutureCallPollError() throws Exception {
    String opName = "testFutureCallPollError";
    Currency meta = Currency.getInstance("UAH");
    Color resp = getColor(1.0f);
    OperationSnapshot initialOperation = getOperation(opName, resp, null, meta, false);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    OperationSnapshot resultOperation = getOperation(opName, resp, null, meta, false);
    LongRunningClient longRunningClient = mockGetOperation(Code.ALREADY_EXISTS, resultOperation);

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);
    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureFailMetaFail(future, null, FakeStatusCode.of(Code.ALREADY_EXISTS));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneWithError() throws Exception {
    String opName = "testFutureCallPollDoneWithError";
    Currency meta = Currency.getInstance("UAH");
    Color resp = getColor(1.0f);
    OperationSnapshot initialOperation = getOperation(opName, resp, null, meta, false);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);

    StatusCode errorCode = FakeStatusCode.of(Code.ALREADY_EXISTS);
    OperationSnapshot resultOperation = getOperation(opName, null, errorCode, meta, true);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, resultOperation);

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);
    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureFailMetaSuccess(future, meta, FakeStatusCode.of(Code.ALREADY_EXISTS));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollCancelOnTimeoutExceeded() throws Exception {
    String opName = "testFutureCallPollCancelOnPollingTimeoutExceeded";
    OperationSnapshot initialOperation = getOperation(opName, null, null, null, false);
    OperationSnapshot resultOperation = getOperation(opName, null, null, null, false);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, resultOperation);

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);
    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureCancelMetaCancel(future);
    assertThat(executor.getIterationsCount()).isEqualTo(5);
  }

  @Test
  public void testFutureCallPollCancelOnLongTimeoutExceeded() throws Exception {
    final int iterationsCount = 1000;
    String opName = "testFutureCallPollCancelOnLongTimeoutExceeded";
    OperationSnapshot initialOperation = getOperation(opName, null, null, null, false);

    OperationSnapshot[] pollOperations = new OperationSnapshot[iterationsCount];
    for (int i = 0; i < iterationsCount; i++) {
      pollOperations[i] = getOperation(opName, null, null, null, false);
    }
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, pollOperations);

    pollingAlgorithm =
        new OperationTimedPollAlgorithm(
            FAST_RETRY_SETTINGS.toBuilder().setTotalTimeout(Duration.ofMillis(1000L)).build(),
            clock);
    callSettings = callSettings.toBuilder().setPollingAlgorithm(pollingAlgorithm).build();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureCancelMetaCancel(future);
    assertThat(executor.getIterationsCount()).isEqualTo(iterationsCount);
  }

  @Test
  public void testFutureCancelImmediately() throws Exception {
    String opName = "testCancelImmediately";
    OperationSnapshot initialOperation = getOperation(opName, null, null, null, false);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    OperationSnapshot resultOperation1 = getOperation(opName, null, null, null, false);
    OperationSnapshot resultOperation2 = getOperation(opName, null, null, null, true);
    LongRunningClient longRunningClient =
        mockGetOperation(Code.OK, resultOperation1, resultOperation2);

    CountDownLatch retryScheduledLatch = new CountDownLatch(1);
    LatchCountDownScheduler scheduler = LatchCountDownScheduler.get(retryScheduledLatch, 0L, 20L);

    ClientContext schedulerContext = getClientContext(initialChannel, scheduler);
    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, schedulerContext, longRunningClient);
    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    CancellationHelpers.cancelInThreadAfterLatchCountDown(future, retryScheduledLatch);

    assertFutureCancelMetaCancel(future);
    scheduler.shutdownNow();
  }

  @Test
  public void testFutureCancelInTheMiddle() throws Exception {
    int iterationsCount = 1000;
    String opName = "testCancelInTheMiddle";
    Color resp = getColor(0.5f);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot resultOperation = getOperation(opName, null, null, null, false);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, resultOperation);

    OperationSnapshot[] pollOperations = new OperationSnapshot[iterationsCount];
    for (int i = 0; i < iterationsCount; i++) {
      pollOperations[i] = getOperation(opName, null, null, null, false);
    }
    pollOperations[iterationsCount - 1] = getOperation(opName, resp, null, meta, true);
    LongRunningClient longRunningClient = mockGetOperation(Code.OK, pollOperations);

    CountDownLatch retryScheduledLatch = new CountDownLatch(10);
    LatchCountDownScheduler scheduler = LatchCountDownScheduler.get(retryScheduledLatch, 0L, 1L);

    ClientContext schedulerContext = getClientContext(initialChannel, scheduler);
    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, schedulerContext, longRunningClient);
    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    CancellationHelpers.cancelInThreadAfterLatchCountDown(future, retryScheduledLatch);

    assertFutureCancelMetaCancel(future);
  }

  @Test
  public void testInitialServerSideCancel() throws Exception {
    String opName = "testInitialServerSideCancel";
    StatusCode errorCode = FakeStatusCode.of(Code.CANCELLED);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot resultOperation = getOperation(opName, null, errorCode, meta, true);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, resultOperation);
    LongRunningClient longRunningClient = new UnsupportedOperationApi();

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureFailMetaSuccess(future, meta, FakeStatusCode.of(Code.CANCELLED));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testPollServerSideCancel() throws Exception {
    String opName = "testPollServerSideCancel";
    StatusCode errorCode = FakeStatusCode.of(Code.CANCELLED);
    Currency meta = Currency.getInstance("UAH");
    OperationSnapshot initialOperation = getOperation(opName, null, null, meta, false);
    UnaryCallable<Integer, OperationSnapshot> initialCallable =
        mockGetOpSnapshotCallable(Code.OK, initialOperation);
    OperationSnapshot resultOperation1 = getOperation(opName, null, null, null, false);
    OperationSnapshot resultOperation2 = getOperation(opName, null, errorCode, meta, true);
    LongRunningClient longRunningClient =
        mockGetOperation(Code.OK, resultOperation1, resultOperation2);

    OperationCallable<Integer, Color, Currency> callable =
        callableFactory.create(initialCallable, callSettings, initialContext, longRunningClient);

    OperationFuture<Color, Currency> future = callable.futureCall(2, FakeApiCallContext.of());

    assertFutureFailMetaSuccess(future, meta, FakeStatusCode.of(Code.CANCELLED));
    assertThat(executor.getIterationsCount()).isEqualTo(1);
  }

  private void assertFutureSuccessMetaSuccess(
      String opName, OperationFuture<Color, Currency> future, Color resp, Currency meta)
      throws InterruptedException, ExecutionException, TimeoutException {
    assertThat(future.getName()).isEqualTo(opName);
    Truth.assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(resp);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    Truth.assertThat(future.get()).isEqualTo(resp);

    Truth.assertThat(future.peekMetadata().get()).isEqualTo(meta);
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isFalse();

    Truth.assertThat(future.getMetadata().get()).isEqualTo(meta);
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isFalse();
  }

  private void assertFutureFailMetaFail(
      OperationFuture<Color, Currency> future,
      Class<? extends Exception> exceptionClass,
      FakeStatusCode statusCode)
      throws TimeoutException, InterruptedException {
    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    if (statusCode != null) {
      assertExceptionMatchesCode(statusCode, exception.getCause());
      ApiException cause = (ApiException) exception.getCause();
      assertThat(cause.getStatusCode()).isEqualTo(statusCode);
    } else {
      assertThat(exception.getCause().getClass()).isEqualTo(exceptionClass);
    }
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();

    try {
      future.peekMetadata().get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }
    assertThat(exception).isNotNull();
    if (statusCode != null) {
      assertExceptionMatchesCode(statusCode, exception.getCause());
      ApiException cause = (ApiException) exception.getCause();
      assertThat(cause.getStatusCode()).isEqualTo(statusCode);
    } else {
      assertThat(exception.getCause().getClass()).isEqualTo(exceptionClass);
    }
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isFalse();

    try {
      future.getMetadata().get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }
    assertThat(exception).isNotNull();
    if (statusCode != null) {
      assertExceptionMatchesCode(statusCode, exception.getCause());
      ApiException cause = (ApiException) exception.getCause();
      assertThat(cause.getStatusCode()).isEqualTo(statusCode);
    } else {
      assertThat(exception.getCause().getClass()).isEqualTo(exceptionClass);
    }
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isFalse();
  }

  private void assertFutureFailMetaSuccess(
      OperationFuture<Color, Currency> future, Currency meta, FakeStatusCode statusCode)
      throws TimeoutException, InterruptedException, ExecutionException {
    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertExceptionMatchesCode(statusCode, exception.getCause());
    ApiException cause = (ApiException) exception.getCause();
    assertThat(cause.getStatusCode()).isEqualTo(statusCode);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();

    Truth.assertThat(future.peekMetadata().get()).isEqualTo(meta);
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isFalse();

    Truth.assertThat(future.getMetadata().get()).isEqualTo(meta);
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isFalse();
  }

  private void assertFutureSuccessMetaFail(
      OperationFuture<Color, Currency> future, Color resp, FakeStatusCode statusCode)
      throws TimeoutException, InterruptedException, ExecutionException {
    Exception exception = null;
    Truth.assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(resp);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    Truth.assertThat(future.get()).isEqualTo(resp);

    try {
      future.peekMetadata().get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(exception).isNotNull();
    assertExceptionMatchesCode(statusCode, exception.getCause());
    ApiException cause = (ApiException) exception.getCause();
    assertThat(cause.getStatusCode()).isEqualTo(statusCode);
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isFalse();

    try {
      future.getMetadata().get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(exception).isNotNull();
    assertExceptionMatchesCode(statusCode, exception.getCause());
    cause = (ApiException) exception.getCause();
    assertThat(cause.getStatusCode()).isEqualTo(statusCode);
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isFalse();
  }

  private void assertFutureCancelMetaCancel(OperationFuture<Color, Currency> future)
      throws InterruptedException, ExecutionException, TimeoutException {
    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (CancellationException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isTrue();

    try {
      future.peekMetadata().get();
    } catch (CancellationException e) {
      exception = e;
    }
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(exception).isNotNull();
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isTrue();

    try {
      future.getMetadata().get();
    } catch (CancellationException e) {
      exception = e;
    }
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(exception).isNotNull();
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isTrue();
  }

  private Color getColor(float blueValue) {
    return new Color(0.0f, 0.0f, blueValue);
  }

  private ClientContext getClientContext(FakeChannel channel, ScheduledExecutorService executor) {
    return ClientContext.newBuilder()
        .setTransportChannel(FakeTransportChannel.of(channel))
        .setExecutor(executor)
        .build();
  }

  private OperationSnapshot getOperation(
      String name, Object response, StatusCode errorCode, Object metadata, boolean done) {
    FakeOperationSnapshot.Builder builder =
        FakeOperationSnapshot.newBuilder().setName(name).setDone(done);
    if (response != null) {
      builder.setResponse(response);
    }
    if (errorCode != null) {
      builder.setErrorCode(errorCode);
    } else {
      builder.setErrorCode(FakeStatusCode.of(Code.OK));
    }
    if (metadata != null) {
      builder.setMetadata(metadata);
    }
    return builder.build();
  }

  private <RequestT> UnaryCallable<RequestT, OperationSnapshot> mockGetOpSnapshotCallable(
      final Code returnStatusCode, final OperationSnapshot... results) {
    return new UnaryCallable<RequestT, OperationSnapshot>() {
      private int index = 0;

      @Override
      public ApiFuture<OperationSnapshot> futureCall(RequestT request, ApiCallContext context) {
        OperationSnapshot response = results[index];
        if (index < results.length - 1) {
          index += 1;
        }
        return newFuture(returnStatusCode, response);
      }
    };
  }

  private UnaryCallable<Integer, OperationSnapshot> getUnexpectedStartCallable() {
    return new UnaryCallable<Integer, OperationSnapshot>() {
      @Override
      public ApiFuture<OperationSnapshot> futureCall(Integer request, ApiCallContext context) {
        return ApiFutures.immediateFailedFuture(
            new UnsupportedOperationException("Unexpected call to start operation"));
      }
    };
  }

  private class UnsupportedOperationApi implements LongRunningClient {
    @Override
    public UnaryCallable<String, OperationSnapshot> getOperationCallable() {
      throw new UnsupportedOperationException("Didn't expect call to getOperationCallable()");
    }

    @Override
    public UnaryCallable<String, Void> cancelOperationCallable() {
      throw new UnsupportedOperationException("Didn't expect call to cancelOperationCallable()");
    }

    @Override
    public UnaryCallable<String, Void> deleteOperationCallable() {
      throw new UnsupportedOperationException("Didn't expect call to deleteOperationCallable()");
    }
  }

  private LongRunningClient mockGetOperation(
      final Code returnStatusCode, final OperationSnapshot... results) {
    return new UnsupportedOperationApi() {
      private UnaryCallable<String, OperationSnapshot> getOperationCallable =
          mockGetOpSnapshotCallable(returnStatusCode, results);

      @Override
      public UnaryCallable<String, OperationSnapshot> getOperationCallable() {
        return getOperationCallable;
      }
    };
  }

  private LongRunningClient mockCancelOperation(final Code returnStatusCode) {
    return new UnsupportedOperationApi() {
      private UnaryCallable<String, Void> cancelOperationCallable =
          new UnaryCallable<String, Void>() {
            @Override
            public ApiFuture<Void> futureCall(String request, ApiCallContext context) {
              return newFuture(returnStatusCode, null);
            }
          };

      @Override
      public UnaryCallable<String, Void> cancelOperationCallable() {
        return cancelOperationCallable;
      }
    };
  }

  private <ResponseT> ApiFuture<ResponseT> newFuture(Code returnStatusCode, ResponseT response) {
    if (Code.OK.equals(returnStatusCode)) {
      return ApiFutures.immediateFuture(response);
    } else {
      return ApiFutures.immediateFailedFuture(
          FakeApiExceptionFactory.createException(null, returnStatusCode, false));
    }
  }

  private void assertExceptionMatchesCode(FakeStatusCode code, Throwable exception) {
    Class expectedClass;
    switch (code.getCode()) {
      case CANCELLED:
        expectedClass = CancelledException.class;
        break;
        //      case NOT_FOUND:
        //        expectedClass = NotFoundException.class;
        //        break;
      case UNKNOWN:
        expectedClass = UnknownException.class;
        break;
        //      case INVALID_ARGUMENT:
        //        expectedClass = InvalidArgumentException.class;
        //        break;
      case DEADLINE_EXCEEDED:
        expectedClass = DeadlineExceededException.class;
        break;
      case ALREADY_EXISTS:
        expectedClass = AlreadyExistsException.class;
        break;
        //      case PERMISSION_DENIED:
        //        expectedClass = PermissionDeniedException.class;
        //        break;
        //      case RESOURCE_EXHAUSTED:
        //        expectedClass = ResourceExhaustedException.class;
        //        break;
      case FAILED_PRECONDITION:
        expectedClass = FailedPreconditionException.class;
        break;
        //      case ABORTED:
        //        expectedClass = AbortedException.class;
        //        break;
        //      case OUT_OF_RANGE:
        //        expectedClass = OutOfRangeException.class;
        //        break;
        //      case INTERNAL:
        //        expectedClass = InternalException.class;
        //        break;
      case UNAVAILABLE:
        expectedClass = UnavailableException.class;
        break;
        //      case DATA_LOSS:
        //        expectedClass = DataLossException.class;
        //        break;
        //      case UNAUTHENTICATED:
        //        expectedClass = UnauthenticatedException.class;
        //        break;

      default:
        expectedClass = ApiException.class;
    }
    assertThat(exception).isInstanceOf(expectedClass);
  }
}
