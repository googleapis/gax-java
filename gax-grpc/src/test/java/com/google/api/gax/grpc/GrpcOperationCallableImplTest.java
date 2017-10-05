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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.core.ListenableFutureToApiFuture;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.AbortedException;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.CancelledException;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.DataLossException;
import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.api.gax.rpc.EmptyRequestParamsExtractor;
import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.api.gax.rpc.InternalException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.OperationCallSettings;
import com.google.api.gax.rpc.OperationCallable;
import com.google.api.gax.rpc.OperationFuture;
import com.google.api.gax.rpc.OutOfRangeException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.RequestUrlParamsEncoder;
import com.google.api.gax.rpc.ResourceExhaustedException;
import com.google.api.gax.rpc.SimpleCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.UnauthenticatedException;
import com.google.api.gax.rpc.UnavailableException;
import com.google.api.gax.rpc.UnknownException;
import com.google.common.util.concurrent.Futures;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsSettings;
import com.google.longrunning.stub.GrpcOperationsStub;
import com.google.longrunning.stub.OperationsStub;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.Status.Code;
import java.io.IOException;
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
public class GrpcOperationCallableImplTest {

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

  private ManagedChannel initialChannel;
  private ManagedChannel pollChannel;
  private OperationsStub operationsStub;
  private RecordingScheduler executor;
  private ClientContext initialContext;
  private OperationCallSettings<Integer, Color, Money, Operation> callSettings;

  private FakeApiClock clock;
  private OperationTimedPollAlgorithm pollingAlgorithm;

  @Before
  public void setUp() throws IOException {
    initialChannel = mock(ManagedChannel.class);
    pollChannel = mock(ManagedChannel.class);
    ChannelProvider operationsChannelProvider = mock(ChannelProvider.class);
    when(operationsChannelProvider.getChannel()).thenReturn(pollChannel);

    clock = new FakeApiClock(0L);
    executor = RecordingScheduler.create(clock);
    pollingAlgorithm = new OperationTimedPollAlgorithm(FAST_RETRY_SETTINGS, clock);

    OperationsSettings.Builder settingsBuilder = OperationsSettings.newBuilder();
    settingsBuilder
        .getOperationSettings()
        .setRetrySettings(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(1).build());
    OperationsSettings settings =
        OperationsSettings.newBuilder()
            .setTransportProvider(
                GrpcTransportProvider.newBuilder()
                    .setChannelProvider(operationsChannelProvider)
                    .build())
            .build();
    operationsStub = GrpcOperationsStub.create(settings);

    SimpleCallSettings<Integer, Operation> initialCallSettings =
        SimpleCallSettings.<Integer, Operation>newBuilder()
            .setRetrySettings(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(1).build())
            .build();

    callSettings =
        OperationCallSettings.<Integer, Color, Money, Operation>newBuilder()
            .setInitialCallSettings(initialCallSettings)
            .setResponseClass(Color.class)
            .setMetadataClass(Money.class)
            .setPollingAlgorithm(pollingAlgorithm)
            .build();

    initialContext = getClientContext(initialChannel, executor);
  }

  @After
  public void tearDown() throws Exception {
    operationsStub.close();
    executor.shutdown();
  }

  @Test
  public void testCall() {
    Color resp = getColor(1.0f);
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation("testCall", resp, meta, true);
    mockResponse(initialChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    Color response = callable.call(2, GrpcCallContext.createDefault());
    assertThat(response).isEqualTo(resp);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testResumeFutureCall() throws Exception {
    String opName = "testResumeFutureCall";
    Color resp = getColor(0.5f);
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation(opName, resp, meta, true);
    mockResponse(pollChannel, Code.OK, resultOperation);

    ClientContext mockContext = getClientContext(pollChannel, executor);
    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, mockContext, operationsStub);

    OperationFuture<Color, Money, Operation> future = callable.resumeFutureCall(opName);

    assertFutureSuccessMetaSuccess(opName, future, resp, meta);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testCancelOperation() throws Exception {
    String opName = "testResumeFutureCall";
    Empty resp = Empty.getDefaultInstance();
    mockResponse(pollChannel, Code.OK, resp);

    ClientContext mockContext = getClientContext(pollChannel, executor);
    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, mockContext, operationsStub);

    ApiFuture<Void> future = callable.cancel(opName);
    assertThat(future.get()).isNull();
  }

  @Test
  public void testFutureCallInitialDone() throws Exception {
    String opName = "testFutureCallInitialDone";
    Color resp = getColor(0.5f);
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation(opName, resp, meta, true);
    mockResponse(initialChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureSuccessMetaSuccess(opName, future, resp, meta);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialError() throws Exception {
    String opName = "testFutureCallInitialError";
    Color resp = getColor(1.0f);
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation(opName, resp, meta, true);
    mockResponse(initialChannel, Code.UNAVAILABLE, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureFailMetaFail(future, null, GrpcStatusCode.of(Code.UNAVAILABLE));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialDoneWithError() throws Exception {
    String opName = "testFutureCallInitialDoneWithError";
    com.google.rpc.Status resp = getError(Code.ALREADY_EXISTS);
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation(opName, resp, meta, true);
    mockResponse(initialChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureFailMetaSuccess(future, meta, GrpcStatusCode.of(Code.ALREADY_EXISTS));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialDoneWrongType() throws Exception {
    String opName = "testFutureCallInitialDoneWrongType";
    Money resp = Money.getDefaultInstance();
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation(opName, resp, meta, true);
    mockResponse(initialChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureFailMetaSuccess(future, meta, GrpcStatusCode.of(Code.OK));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialDoneMetaWrongType() throws Exception {
    String opName = "testFutureCallInitialDoneMetaWrongType";
    Color resp = getColor(1.0f);
    Color meta = getColor(1.0f);
    Operation resultOperation = getOperation(opName, resp, meta, true);
    mockResponse(initialChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureSuccessMetaFail(future, resp, GrpcStatusCode.of(Code.OK));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialCancel() throws Exception {
    String opName = "testFutureCallInitialCancel";
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Code.OK, initialOperation);
    mockResponse(pollChannel, Code.OK, resultOperation);

    GrpcOperationCallableImpl<Integer, Color, Money> callableImpl =
        GrpcCallableFactory.createImpl(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callableImpl.futureCall(
            new ListenableFutureToApiFuture<>(Futures.<Operation>immediateCancelledFuture()));

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
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Code.OK, initialOperation);
    mockResponse(pollChannel, Code.OK, resultOperation);

    GrpcOperationCallableImpl<Integer, Color, Money> callableImpl =
        GrpcCallableFactory.createImpl(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    RuntimeException thrownException = new RuntimeException();

    OperationFuture<Color, Money, Operation> future =
        callableImpl.futureCall(
            new ListenableFutureToApiFuture<>(
                Futures.<Operation>immediateFailedFuture(thrownException)));

    assertFutureFailMetaFail(future, RuntimeException.class, null);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneOnFirst() throws Exception {
    String opName = "testFutureCallPollDoneOnFirst";
    Color resp = getColor(0.5f);
    Money meta = getMoney("UAH");
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, resp, meta, true);
    mockResponse(initialChannel, Code.OK, initialOperation);
    mockResponse(pollChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureSuccessMetaSuccess(opName, future, resp, meta);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneOnSecond() throws Exception {
    String opName = "testFutureCallPollDoneOnSecond";
    Color resp = getColor(0.5f);
    Money meta1 = getMoney("UAH");
    Money meta2 = getMoney("USD");
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation1 = getOperation(opName, null, meta1, false);
    Operation resultOperation2 = getOperation(opName, resp, meta2, true);
    mockResponse(initialChannel, Code.OK, initialOperation);
    mockResponse(pollChannel, Code.OK, resultOperation1, resultOperation2);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureSuccessMetaSuccess(opName, future, resp, meta2);
    assertThat(executor.getIterationsCount()).isEqualTo(1);
  }

  @Test
  public void testFutureCallPollDoneOnMany() throws Exception {
    final int iterationsCount = 1000;
    String opName = "testFutureCallPollDoneOnMany";
    Color resp = getColor(0.5f);
    Money meta = getMoney("UAH");

    Operation initialOperation = getOperation(opName, null, null, false);

    Operation[] pollOperations = new Operation[iterationsCount];
    for (int i = 0; i < iterationsCount - 1; i++) {
      pollOperations[i] = getOperation(opName, null, meta, false);
    }
    pollOperations[iterationsCount - 1] = getOperation(opName, resp, meta, true);
    mockResponse(initialChannel, Code.OK, initialOperation);
    mockResponse(pollChannel, Code.OK, (Object[]) pollOperations);

    pollingAlgorithm =
        new OperationTimedPollAlgorithm(
            FAST_RETRY_SETTINGS
                .toBuilder()
                .setTotalTimeout(Duration.ofMillis(iterationsCount))
                .build(),
            clock);
    callSettings = callSettings.toBuilder().setPollingAlgorithm(pollingAlgorithm).build();

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(resp);
    assertFutureSuccessMetaSuccess(opName, future, resp, meta);

    assertThat(executor.getIterationsCount()).isEqualTo(iterationsCount - 1);
  }

  @Test
  public void testFutureCallPollError() throws Exception {
    String opName = "testFutureCallPollError";
    Money meta = getMoney("UAH");
    Color resp = getColor(1.0f);
    Operation initialOperation = getOperation(opName, resp, meta, false);
    mockResponse(initialChannel, Code.OK, initialOperation);
    Operation resultOperation = getOperation(opName, resp, meta, false);
    mockResponse(pollChannel, Code.ALREADY_EXISTS, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);
    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureFailMetaFail(future, null, GrpcStatusCode.of(Code.ALREADY_EXISTS));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneWithError() throws Exception {
    String opName = "testFutureCallPollDoneWithError";
    Money meta = getMoney("UAH");
    Color resp = getColor(1.0f);
    Operation initialOperation = getOperation(opName, resp, meta, false);
    mockResponse(initialChannel, Code.OK, initialOperation);

    com.google.rpc.Status resp1 = getError(Code.ALREADY_EXISTS);
    Operation resultOperation = getOperation(opName, resp1, meta, true);
    mockResponse(pollChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);
    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureFailMetaSuccess(future, meta, GrpcStatusCode.of(Code.ALREADY_EXISTS));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollCancelOnTimeoutExceeded() throws Exception {
    String opName = "testFutureCallPollCancelOnPollingTimeoutExceeded";
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Code.OK, initialOperation);
    mockResponse(pollChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);
    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureCancelMetaCancel(future);
    assertThat(executor.getIterationsCount()).isEqualTo(5);
  }

  @Test
  public void testFutureCallPollCancelOnLongTimeoutExceeded() throws Exception {
    final int iterationsCount = 1000;
    String opName = "testFutureCallPollCancelOnLongTimeoutExceeded";
    Operation initialOperation = getOperation(opName, null, null, false);

    Operation[] pollOperations = new Operation[iterationsCount];
    for (int i = 0; i < iterationsCount; i++) {
      pollOperations[i] = getOperation(opName, null, null, false);
    }
    mockResponse(initialChannel, Code.OK, initialOperation);
    mockResponse(pollChannel, Code.OK, (Object[]) pollOperations);

    pollingAlgorithm =
        new OperationTimedPollAlgorithm(
            FAST_RETRY_SETTINGS.toBuilder().setTotalTimeout(Duration.ofMillis(1000L)).build(),
            clock);
    callSettings = callSettings.toBuilder().setPollingAlgorithm(pollingAlgorithm).build();

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureCancelMetaCancel(future);
    assertThat(executor.getIterationsCount()).isEqualTo(iterationsCount);
  }

  @Test
  public void testFutureCancelImmediately() throws Exception {
    String opName = "testCancelImmediately";
    Operation initialOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Code.OK, initialOperation);
    Operation resultOperation1 = getOperation(opName, null, null, false);
    Operation resultOperation2 = getOperation(opName, null, null, true);
    mockResponse(pollChannel, Code.OK, resultOperation1, resultOperation2);

    CountDownLatch retryScheduledLatch = new CountDownLatch(1);
    LatchCountDownScheduler scheduler = LatchCountDownScheduler.get(retryScheduledLatch, 0L, 20L);

    ClientContext schedulerContext = getClientContext(initialChannel, scheduler);
    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, schedulerContext, operationsStub);
    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    CancellationHelpers.cancelInThreadAfterLatchCountDown(future, retryScheduledLatch);

    assertFutureCancelMetaCancel(future);
    scheduler.shutdownNow();
  }

  @Test
  public void testFutureCancelInTheMiddle() throws Exception {
    int iterationsCount = 1000;
    String opName = "testCancelInTheMiddle";
    Color resp = getColor(0.5f);
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Code.OK, resultOperation);

    Operation[] pollOperations = new Operation[iterationsCount];
    for (int i = 0; i < iterationsCount; i++) {
      pollOperations[i] = getOperation(opName, null, null, false);
    }
    pollOperations[iterationsCount - 1] = getOperation(opName, resp, meta, true);
    mockResponse(pollChannel, Code.OK, (Object[]) pollOperations);

    CountDownLatch retryScheduledLatch = new CountDownLatch(10);
    LatchCountDownScheduler scheduler = LatchCountDownScheduler.get(retryScheduledLatch, 0L, 1L);

    ClientContext schedulerContext = getClientContext(initialChannel, scheduler);
    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, schedulerContext, operationsStub);
    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    CancellationHelpers.cancelInThreadAfterLatchCountDown(future, retryScheduledLatch);

    assertFutureCancelMetaCancel(future);
  }

  @Test
  public void testInitialServerSideCancel() throws Exception {
    String opName = "testInitialServerSideCancel";
    com.google.rpc.Status err = getError(Code.CANCELLED);
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation(opName, err, meta, true);
    mockResponse(initialChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureFailMetaSuccess(future, meta, GrpcStatusCode.of(Code.CANCELLED));
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testPollServerSideCancel() throws Exception {
    String opName = "testPollServerSideCancel";
    com.google.rpc.Status err = getError(Code.CANCELLED);
    Money meta = getMoney("UAH");
    Operation initialOperation = getOperation(opName, null, meta, false);
    mockResponse(initialChannel, Code.OK, initialOperation);
    Operation resultOperation1 = getOperation(opName, null, null, false);
    Operation resultOperation2 = getOperation(opName, err, meta, true);
    mockResponse(pollChannel, Code.OK, resultOperation1, resultOperation2);

    OperationCallable<Integer, Color, Money, Operation> callable =
        GrpcCallableFactory.create(
            createDirectCallable(), callSettings, initialContext, operationsStub);

    OperationFuture<Color, Money, Operation> future =
        callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureFailMetaSuccess(future, meta, GrpcStatusCode.of(Code.CANCELLED));
    assertThat(executor.getIterationsCount()).isEqualTo(1);
  }

  private void assertFutureSuccessMetaSuccess(
      String opName, OperationFuture<Color, Money, Operation> future, Color resp, Money meta)
      throws InterruptedException, ExecutionException, TimeoutException {
    assertThat(future.getName()).isEqualTo(opName);
    assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(resp);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.get()).isEqualTo(resp);

    assertThat(future.peekMetadata().get()).isEqualTo(meta);
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isFalse();

    assertThat(future.getMetadata().get()).isEqualTo(meta);
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isFalse();
  }

  private void assertFutureFailMetaFail(
      OperationFuture<Color, Money, Operation> future,
      Class<? extends Exception> exceptionClass,
      GrpcStatusCode statusCode)
      throws TimeoutException, InterruptedException {
    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    if (statusCode != null) {
      assertExceptionMatchesCode((GrpcStatusCode) statusCode, exception.getCause());
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
      assertExceptionMatchesCode((GrpcStatusCode) statusCode, exception.getCause());
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
      assertExceptionMatchesCode((GrpcStatusCode) statusCode, exception.getCause());
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
      OperationFuture<Color, Money, Operation> future, Money meta, GrpcStatusCode statusCode)
      throws TimeoutException, InterruptedException, ExecutionException {
    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertExceptionMatchesCode((GrpcStatusCode) statusCode, exception.getCause());
    ApiException cause = (ApiException) exception.getCause();
    assertThat(cause.getStatusCode()).isEqualTo(statusCode);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();

    assertThat(future.peekMetadata().get()).isEqualTo(meta);
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isFalse();

    assertThat(future.getMetadata().get()).isEqualTo(meta);
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isFalse();
  }

  private void assertFutureSuccessMetaFail(
      OperationFuture<Color, Money, Operation> future, Color resp, GrpcStatusCode statusCode)
      throws TimeoutException, InterruptedException, ExecutionException {
    Exception exception = null;
    assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(resp);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.get()).isEqualTo(resp);

    try {
      future.peekMetadata().get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(exception).isNotNull();
    assertExceptionMatchesCode((GrpcStatusCode) statusCode, exception.getCause());
    ApiException cause = (ApiException) exception.getCause();
    assertThat(((ApiException) cause).getStatusCode()).isEqualTo(statusCode);
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isFalse();

    try {
      future.getMetadata().get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(exception).isNotNull();
    assertExceptionMatchesCode((GrpcStatusCode) statusCode, exception.getCause());
    cause = (ApiException) exception.getCause();
    assertThat(cause.getStatusCode()).isEqualTo(statusCode);
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isFalse();
  }

  private void assertFutureCancelMetaCancel(OperationFuture<Color, Money, Operation> future)
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

  private com.google.rpc.Status getError(Code statusCode) {
    return com.google.rpc.Status.newBuilder().setCode(statusCode.value()).build();
  }

  private Color getColor(float blueValue) {
    return Color.newBuilder().setBlue(blueValue).build();
  }

  private Money getMoney(String currencyCode) {
    return Money.newBuilder().setCurrencyCode(currencyCode).build();
  }

  private ClientContext getClientContext(Channel channel, ScheduledExecutorService executor) {
    return ClientContext.newBuilder()
        .setTransportContext(GrpcTransport.newBuilder().setChannel(channel).build())
        .setExecutor(executor)
        .build();
  }

  private Operation getOperation(String name, Message response, Message metadata, boolean done) {
    Operation.Builder builder = Operation.newBuilder().setName(name).setDone(done);
    if (response instanceof com.google.rpc.Status) {
      builder.setError((com.google.rpc.Status) response);
    } else if (response != null) {
      builder.setResponse(Any.pack(response));
    }
    if (metadata != null) {
      builder.setMetadata(Any.pack(metadata));
    }
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private void mockResponse(ManagedChannel channel, Code statusCode, Object... results) {
    Status status = statusCode.toStatus();
    ClientCall<Integer, ?> clientCall = new MockClientCall<>(results[0], status);
    ClientCall<Integer, ?>[] moreCalls = new ClientCall[results.length - 1];
    for (int i = 0; i < results.length - 1; i++) {
      moreCalls[i] = new MockClientCall<>(results[i + 1], status);
    }
    when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
        .thenReturn(clientCall, moreCalls);
  }

  private UnaryCallable<Integer, Operation> createDirectCallable() {
    return new GrpcDirectCallable<>(
        FakeMethodDescriptor.<Integer, Operation>create(),
        new RequestUrlParamsEncoder<>(EmptyRequestParamsExtractor.<Integer>of(), false));
  }

  private void assertExceptionMatchesCode(GrpcStatusCode code, Throwable exception) {
    Class expectedClass;
    switch (code.getCode()) {
      case CANCELLED:
        expectedClass = CancelledException.class;
        break;
      case NOT_FOUND:
        expectedClass = NotFoundException.class;
        break;
      case UNKNOWN:
        expectedClass = UnknownException.class;
        break;
      case INVALID_ARGUMENT:
        expectedClass = InvalidArgumentException.class;
        break;
      case DEADLINE_EXCEEDED:
        expectedClass = DeadlineExceededException.class;
        break;
      case ALREADY_EXISTS:
        expectedClass = AlreadyExistsException.class;
        break;
      case PERMISSION_DENIED:
        expectedClass = PermissionDeniedException.class;
        break;
      case RESOURCE_EXHAUSTED:
        expectedClass = ResourceExhaustedException.class;
        break;
      case FAILED_PRECONDITION:
        expectedClass = FailedPreconditionException.class;
        break;
      case ABORTED:
        expectedClass = AbortedException.class;
        break;
      case OUT_OF_RANGE:
        expectedClass = OutOfRangeException.class;
        break;
      case INTERNAL:
        expectedClass = InternalException.class;
        break;
      case UNAVAILABLE:
        expectedClass = UnavailableException.class;
        break;
      case DATA_LOSS:
        expectedClass = DataLossException.class;
        break;
      case UNAUTHENTICATED:
        expectedClass = UnauthenticatedException.class;
        break;

      default:
        expectedClass = ApiException.class;
    }
    assertThat(exception).isInstanceOf(expectedClass);
  }
}
