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

import com.google.api.core.ApiClock;
import com.google.api.core.ApiFuture;
import com.google.api.core.ListenableFutureToApiFuture;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.grpc.OperationPollingCallable.OperationTimedAlgorithm;
import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.TimedAttemptSettings;
import com.google.common.util.concurrent.Futures;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.longrunning.OperationsSettings;
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
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class OperationCallableTest {

  private static final RetrySettings FAST_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(1L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofMillis(1L))
          .setInitialRpcTimeout(Duration.ofMillis(1L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofMillis(1L))
          .setTotalTimeout(Duration.ofMillis(5L))
          .setMaxAttempts(0)
          .build();

  private ManagedChannel initialChannel;
  private ManagedChannel pollChannel;
  private OperationsClient operationsClient;
  private RecordingScheduler executor;
  private OperationCallSettings<Integer, Color> callSettings;

  private FakeApiClock clock;
  private DefiniteExponentialRetryAlgorithm pollingAlgorithm;

  @Rule public ExpectedException thrown = ExpectedException.none();

  private Color getMessage(float blueValue) {
    return Color.newBuilder().setBlue(blueValue).build();
  }

  private Operation getOperation(String name, Status error, Message response, boolean done) {
    Operation.Builder builder = Operation.newBuilder().setName(name).setDone(done);
    if (error != null) {
      builder.setError(com.google.rpc.Status.newBuilder().setCode(error.getCode().value()).build());
    }
    if (response != null) {
      builder.setResponse(Any.pack(response));
    }
    return builder.build();
  }

  private void mockResponse(ManagedChannel channel, Status status, Object... results) {
    ClientCall<Integer, ?> clientCall = new MockClientCall<>(results[0], status);
    @SuppressWarnings("unchecked")
    ClientCall<Integer, ?>[] moreCalls = new ClientCall[results.length - 1];
    for (int i = 0; i < results.length - 1; i++) {
      moreCalls[i] = new MockClientCall<>(results[i + 1], status);
    }
    when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
        .thenReturn(clientCall, moreCalls);
  }

  @Before
  public void setUp() throws IOException {
    initialChannel = mock(ManagedChannel.class);
    pollChannel = mock(ManagedChannel.class);
    ChannelProvider operationsChannelProvider = mock(ChannelProvider.class);
    when(operationsChannelProvider.getChannel()).thenReturn(pollChannel);

    OperationsSettings.Builder settingsBuilder = OperationsSettings.defaultBuilder();
    settingsBuilder
        .getOperationSettings()
        .setRetrySettingsBuilder(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(1));
    OperationsSettings settings =
        settingsBuilder.setChannelProvider(operationsChannelProvider).build();

    operationsClient = OperationsClient.create(settings);
    clock = new FakeApiClock(0L);
    executor = RecordingScheduler.create(clock);
    pollingAlgorithm = new DefiniteExponentialRetryAlgorithm(FAST_RETRY_SETTINGS, clock);

    @SuppressWarnings("unchecked")
    SimpleCallSettings<Integer, Operation> initialCallSettings =
        SimpleCallSettings.newBuilder(FakeMethodDescriptor.<Integer, Operation>create())
            .setRetrySettingsBuilder(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(1))
            .build();

    callSettings =
        OperationCallSettings.<Integer, Color>newBuilder()
            .setInitialCallSettings(initialCallSettings)
            .setResponseClass(Color.class)
            .setPollingAlgorithm(pollingAlgorithm)
            .build();
  }

  @After
  public void tearDown() throws Exception {
    operationsClient.close();
    executor.shutdown();
  }

  @Test
  public void testCall() {
    Color injectedResponse = getMessage(1.0f);
    Operation resultOperation = getOperation("testCall", null, injectedResponse, true);
    mockResponse(initialChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    Color response = callable.call(2, CallContext.createDefault());
    assertThat(response).isEqualTo(injectedResponse);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

//  @Test
//  public void testBind() {
//    Color injectedResponse = getMessage(1.0f);
//    Operation resultOperation = getOperation("testBind", null, injectedResponse, true);
//    mockResponse(initialChannel, Status.OK, resultOperation);
//
//    OperationCallable<Integer, Color> callable =
//        OperationCallable.create(callSettings, null, executor, operationsClient);
//    callable = callable.bind(initialChannel);
//
//    Color response = callable.call(2);
//    assertThat(response).isEqualTo(injectedResponse);
//    assertThat(executor.getIterationsCount()).isEqualTo(0);
//  }

  @Test
  public void testResumeFutureCall() throws Exception {
    String opName = "testResumeFutureCall";
    Color injectedResponse = getMessage(0.5f);
    Operation resultOperation = getOperation(opName, null, injectedResponse, true);
    mockResponse(pollChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, mock(Channel.class), executor, operationsClient);

    OperationFuture<Color> future = callable.resumeFutureCall(opName);
    assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(injectedResponse);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.get()).isEqualTo(injectedResponse);
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
    assertThat(executor.getIterationsCount()).isEqualTo(0);

    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testCancelOperation() throws Exception {
    String opName = "testResumeFutureCall";
    Empty injectedResponse = Empty.getDefaultInstance();
    mockResponse(pollChannel, Status.OK, injectedResponse);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, mock(Channel.class), executor, operationsClient);

    ApiFuture<Empty> future = callable.cancel(opName);
    assertThat(future.get()).isEqualTo(injectedResponse);
  }

  @Test
  public void testFutureCallInitialDone() throws Exception {
    String opName = "testFutureCallInitialDone";
    Color injectedResponse = getMessage(0.5f);
    Operation resultOperation = getOperation(opName, null, injectedResponse, true);
    mockResponse(initialChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(injectedResponse);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialDoneWithError() throws Exception {
    String opName = "testFutureCallInitialDoneWithError";
    Operation resultOperation = getOperation(opName, Status.ALREADY_EXISTS, null, true);
    mockResponse(initialChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertThat(exception.getCause()).isInstanceOf(ApiException.class);
    ApiException cause = (ApiException) exception.getCause();
    assertThat(cause.getStatusCode()).isEqualTo(Code.ALREADY_EXISTS);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialDoneWrongType() throws Exception {
    String opName = "testFutureCallInitialDoneWrongType";
    Money injectedResponse = Money.getDefaultInstance();
    Operation resultOperation = getOperation(opName, null, injectedResponse, true);
    mockResponse(initialChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertThat(exception.getCause()).isInstanceOf(ClassCastException.class);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialCancel() throws Exception {
    String opName = "testFutureCallInitialCancel";
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Status.OK, initialOperation);
    mockResponse(pollChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future =
        callable.futureCall(
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
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallInitialOperationUnexpectedFail() throws Exception {
    String opName = "testFutureCallInitialOperationUnexpectedFail";
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Status.OK, initialOperation);
    mockResponse(pollChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    RuntimeException thrownException = new RuntimeException();

    OperationFuture<Color> future =
        callable.futureCall(
            new ListenableFutureToApiFuture<>(
                Futures.<Operation>immediateFailedFuture(thrownException)));

    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (Exception e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertThat(exception.getCause()).isSameAs(thrownException);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneOnFirst() throws Exception {
    String opName = "testFutureCallPollDoneOnFirst";
    Color injectedResponse = getMessage(0.5f);
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, null, injectedResponse, true);
    mockResponse(initialChannel, Status.OK, initialOperation);
    mockResponse(pollChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(injectedResponse);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneOnSecond() throws Exception {
    String opName = "testFutureCallPollDoneOnSecond";
    Color injectedResponse = getMessage(0.5f);
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation1 = getOperation(opName, null, null, false);
    Operation resultOperation2 = getOperation(opName, null, injectedResponse, true);
    mockResponse(initialChannel, Status.OK, initialOperation);
    mockResponse(pollChannel, Status.OK, resultOperation1, resultOperation2);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(injectedResponse);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
    assertThat(executor.getIterationsCount()).isEqualTo(1);
  }

  @Test
  public void testFutureCallPollDoneOnMany() throws Exception {
    final int iterationsCount = 1000;
    String opName = "testFutureCallPollDoneOnMany";
    Color injectedResponse = getMessage(0.5f);
    Operation initialOperation = getOperation(opName, null, null, false);

    Operation[] pollOperations = new Operation[iterationsCount];
    for (int i = 0; i < iterationsCount - 1; i++) {
      pollOperations[i] = getOperation(opName, null, null, false);
    }
    pollOperations[iterationsCount - 1] = getOperation(opName, null, injectedResponse, true);
    mockResponse(initialChannel, Status.OK, initialOperation);
    mockResponse(pollChannel, Status.OK, pollOperations);

    pollingAlgorithm =
        new DefiniteExponentialRetryAlgorithm(
            FAST_RETRY_SETTINGS
                .toBuilder()
                .setTotalTimeout(Duration.ofMillis(iterationsCount))
                .build(),
            clock);
    callSettings = callSettings.toBuilder().setPollingAlgorithm(pollingAlgorithm).build();

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(injectedResponse);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
    assertThat(executor.getIterationsCount()).isEqualTo(iterationsCount - 1);
  }

  @Test
  public void testFutureCallPollDoneWithError() throws Exception {
    String opName = "testFutureCallPollingOperationDoneWithError";
    Operation initialOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Status.OK, initialOperation);
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(pollChannel, Status.UNAVAILABLE, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);
    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    Exception exception = null;
    try {
      future.get(3, TimeUnit.SECONDS);
    } catch (Exception e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertThat(exception.getCause()).isInstanceOf(ApiException.class);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollCancelOnTimeoutExceeded() throws Exception {
    String opName = "testFutureCallPollCancelOnPollingTimeoutExceeded";
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Status.OK, initialOperation);
    mockResponse(pollChannel, Status.OK, resultOperation);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

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
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
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
    mockResponse(initialChannel, Status.OK, initialOperation);
    mockResponse(pollChannel, Status.OK, pollOperations);

    pollingAlgorithm =
        new DefiniteExponentialRetryAlgorithm(
            FAST_RETRY_SETTINGS.toBuilder().setTotalTimeout(Duration.ofMillis(1000L)).build(),
            clock);
    callSettings = callSettings.toBuilder().setPollingAlgorithm(pollingAlgorithm).build();

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, executor, operationsClient);

    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    Exception exception = null;
    try {
      future.get(5, TimeUnit.SECONDS);
    } catch (CancellationException e) {
      exception = e;
    }

    assertThat(exception).isNotNull();
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isTrue();
    assertThat(future.getInitialFuture().isDone()).isTrue();
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
    assertThat(executor.getIterationsCount()).isEqualTo(iterationsCount);
  }

  @Test
  public void testCancelImmediately() throws Exception {
    String opName = "testCancelImmediately";
    Color injectedResponse = getMessage(0.5f);
    Operation initialOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Status.OK, initialOperation);
    Operation resultOperation1 = getOperation(opName, null, null, false);
    Operation resultOperation2 = getOperation(opName, null, injectedResponse, true);
    mockResponse(pollChannel, Status.OK, resultOperation1, resultOperation2);

    CountDownLatch retryScheduledLatch = new CountDownLatch(1);
    LatchCountDownScheduler scheduler = LatchCountDownScheduler.get(retryScheduledLatch, 20L);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, scheduler, operationsClient);
    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    CancellationHelpers.cancelInThreadAfterLatchCountDown(future, retryScheduledLatch);

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
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
  }

  @Test
  public void testCancelInTheMiddle() throws Exception {
    int iterationsCount = 1000;
    String opName = "testCancelInTheMiddle";
    Color injectedResponse = getMessage(0.5f);
    Operation resultOperation = getOperation(opName, null, null, false);
    mockResponse(initialChannel, Status.OK, resultOperation);

    Operation[] pollOperations = new Operation[iterationsCount];
    for (int i = 0; i < iterationsCount; i++) {
      pollOperations[i] = getOperation(opName, null, null, false);
    }
    pollOperations[iterationsCount - 1] = getOperation(opName, null, injectedResponse, true);
    mockResponse(pollChannel, Status.OK, pollOperations);

    CountDownLatch retryScheduledLatch = new CountDownLatch(10);
    LatchCountDownScheduler scheduler = LatchCountDownScheduler.get(retryScheduledLatch, 1L);

    OperationCallable<Integer, Color> callable =
        OperationCallable.create(callSettings, initialChannel, scheduler, operationsClient);
    OperationFuture<Color> future = callable.futureCall(2, CallContext.createDefault());

    CancellationHelpers.cancelInThreadAfterLatchCountDown(future, retryScheduledLatch);

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
    assertThat(future.getInitialFuture().isCancelled()).isFalse();
    assertThat(future.getInitialFuture().get().getName()).isEqualTo(opName);
  }

  private static class DefiniteExponentialRetryAlgorithm extends OperationTimedAlgorithm {

    public DefiniteExponentialRetryAlgorithm(RetrySettings globalSettings, ApiClock clock) {
      super(globalSettings, clock);
    }

    @Override
    public TimedAttemptSettings createNextAttempt(TimedAttemptSettings prevSettings) {
      return super.createNextAttempt(prevSettings);
    }

    @Override
    protected long nextRandomLong(long bound) {
      return bound;
    }
  }
}
