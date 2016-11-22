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

import com.google.api.gax.testing.MockGrpcService;
import com.google.api.gax.testing.MockServiceHelper;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.SettableFuture;
import com.google.longrunning.CancelOperationRequest;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.longrunning.OperationsSettings;
import com.google.protobuf.Any;
import com.google.protobuf.GeneratedMessageV3;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.Status;
import io.grpc.Status.Code;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
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

@RunWith(JUnit4.class)
public class OperationFutureTest {
  private MockOperationsEx mockOperations;
  private MockServiceHelper serviceHelper;
  private OperationsClient operationsClient;

  private ScheduledExecutorService executor;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws IOException {
    mockOperations = new MockOperationsEx();
    serviceHelper =
        new MockServiceHelper("in-process-1", Arrays.<MockGrpcService>asList(mockOperations));
    serviceHelper.start();
    OperationsSettings settings =
        OperationsSettings.defaultBuilder()
            .setChannelProvider(serviceHelper.createChannelProvider())
            .build();
    operationsClient = OperationsClient.create(settings);
    executor = new ScheduledThreadPoolExecutor(1);
  }

  @After
  public void tearDown() throws Exception {
    operationsClient.close();
    executor.shutdown();
    serviceHelper.stop();
  }

  @Test
  public void testOperationDoneImmediately() throws Exception {
    Color expectedResult = Color.getDefaultInstance();
    String opName = "testOperationDoneImmediately";

    Operation firstOperationResult =
        Operation.newBuilder()
            .setName(opName)
            .setDone(true)
            .setResponse(Any.pack(expectedResult))
            .build();

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Color> opFuture =
        OperationFuture.create(operationsClient, startOperationFuture, executor, Color.class);

    Truth.assertThat(opFuture.isDone()).isFalse();

    startOperationFuture.set(firstOperationResult);

    Color result = opFuture.get();
    Truth.assertThat(result).isNotSameAs(expectedResult);
    Truth.assertThat(result).isEqualTo(expectedResult);
    Truth.assertThat(opFuture.isDone()).isTrue();
    Truth.assertThat(opFuture.isCancelled()).isFalse();

    Truth.assertThat(opFuture.getOperationName()).isEqualTo(opName);

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }

  @Test
  public void testOperationDoneWithError() throws Exception {
    String opName = "testOperationDoneWithError";

    com.google.rpc.Status alreadyExistsStatus =
        com.google.rpc.Status.newBuilder().setCode(Status.ALREADY_EXISTS.getCode().value()).build();
    Operation firstOperationResult =
        Operation.newBuilder().setName(opName).setDone(true).setError(alreadyExistsStatus).build();

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Color> opFuture =
        OperationFuture.create(operationsClient, startOperationFuture, executor, Color.class);

    startOperationFuture.set(firstOperationResult);

    ExecutionException gotException = null;
    try {
      opFuture.get();
    } catch (ExecutionException e) {
      gotException = e;
    }
    Truth.assertThat(gotException.getCause()).isInstanceOf(ApiException.class);
    ApiException cause = (ApiException) gotException.getCause();
    Truth.assertThat(cause.getStatusCode()).isEqualTo(Code.ALREADY_EXISTS);
    Truth.assertThat(opFuture.isDone()).isTrue();
    Truth.assertThat(opFuture.isCancelled()).isFalse();
    Truth.assertThat(opFuture.getOperationName()).isEqualTo(opName);

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }

  @Test
  public void testOperationDoneWrongType() throws Exception {
    Color returnedResult = Color.getDefaultInstance();
    String opName = "testOperationDoneImmediately";

    Operation firstOperationResult =
        Operation.newBuilder()
            .setName(opName)
            .setDone(true)
            .setResponse(Any.pack(returnedResult))
            .build();

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Money> opFuture =
        OperationFuture.create(operationsClient, startOperationFuture, executor, Money.class);

    startOperationFuture.set(firstOperationResult);

    ExecutionException gotException = null;
    try {
      opFuture.get();
    } catch (ExecutionException e) {
      gotException = e;
    }
    Truth.assertThat(gotException.getCause()).isInstanceOf(ClassCastException.class);
    Truth.assertThat(opFuture.isDone()).isTrue();
    Truth.assertThat(opFuture.isCancelled()).isFalse();

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }

  @Test
  public void testStartOperationThrowsApiException() throws Exception {
    thrown.expect(ExecutionException.class);

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Color> opFuture =
        OperationFuture.create(operationsClient, startOperationFuture, executor, Color.class);

    startOperationFuture.setException(new ApiException(null, Code.UNAVAILABLE, true));

    opFuture.get();

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }

  @Test
  public void testOperationDoneOnFirstUpdate() throws Exception {
    Color expectedResult = Color.getDefaultInstance();
    String opName = "testOperationDoneOnFirstUpdate";

    Operation firstOperationResult = Operation.newBuilder().setName(opName).setDone(false).build();

    Operation operation =
        Operation.newBuilder()
            .setName(opName)
            .setDone(true)
            .setResponse(Any.pack(expectedResult))
            .build();
    mockOperations.addResponse(operation);

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Color> opFuture =
        OperationFuture.create(
            operationsClient, startOperationFuture, executor, Color.class, Duration.millis(0));

    startOperationFuture.set(firstOperationResult);

    Color result = opFuture.get();
    Truth.assertThat(result).isNotSameAs(expectedResult);
    Truth.assertThat(result).isEqualTo(expectedResult);
    Truth.assertThat(opFuture.isDone()).isTrue();
    Truth.assertThat(opFuture.isCancelled()).isFalse();

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }

  @Test
  public void testFirstUpdateThrowsApiException() throws Exception {
    thrown.expect(ExecutionException.class);

    String opName = "testFirstUpdateThrowsApiException";

    Operation firstOperationResult = Operation.newBuilder().setName(opName).setDone(false).build();

    mockOperations.addException(new ApiException(null, Code.UNAVAILABLE, true));

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Color> opFuture =
        OperationFuture.create(
            operationsClient, startOperationFuture, executor, Color.class, Duration.millis(0));

    startOperationFuture.set(firstOperationResult);

    opFuture.get();

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }

  @Test
  public void testOperationDoneOnSecondUpdate() throws Exception {
    Color expectedResult = Color.getDefaultInstance();
    String opName = "testOperationDoneOnSecondUpdate";

    Operation firstOperationResult = Operation.newBuilder().setName(opName).setDone(false).build();

    // response 1: not done
    mockOperations.addResponse(firstOperationResult.toBuilder().build());

    // response 2: done
    Operation operationDone =
        Operation.newBuilder()
            .setName(opName)
            .setDone(true)
            .setResponse(Any.pack(expectedResult))
            .build();
    mockOperations.addResponse(operationDone);

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Color> opFuture =
        OperationFuture.create(
            operationsClient, startOperationFuture, executor, Color.class, Duration.millis(0));

    startOperationFuture.set(firstOperationResult);

    Color result = opFuture.get();
    Truth.assertThat(result).isNotSameAs(expectedResult);
    Truth.assertThat(result).isEqualTo(expectedResult);
    Truth.assertThat(opFuture.isDone()).isTrue();
    Truth.assertThat(opFuture.isCancelled()).isFalse();

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }

  @Test
  public void testCancelImmediately() throws Exception {
    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Color> opFuture =
        OperationFuture.create(operationsClient, startOperationFuture, executor, Color.class);

    Truth.assertThat(opFuture.isDone()).isFalse();
    opFuture.cancel(true);

    CancellationException gotException = null;
    try {
      opFuture.get();
    } catch (CancellationException e) {
      gotException = e;
    }
    Truth.assertThat(gotException).isNotNull();
    Truth.assertThat(opFuture.isDone()).isTrue();
    Truth.assertThat(opFuture.isCancelled()).isTrue();

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }

  private static class LatchCountDownWaiter extends OperationFuture.Waiter {
    private final CountDownLatch waitStartedLatch;
    private final CountDownLatch endWaitLatch;

    public LatchCountDownWaiter(CountDownLatch waitStartedLatch, CountDownLatch endWaitLatch) {
      this.waitStartedLatch = waitStartedLatch;
      this.endWaitLatch = endWaitLatch;
    }

    public void wait(Duration duration) throws InterruptedException {
      System.err.println("LatchCountDownWaiter wait");
      waitStartedLatch.countDown();
      endWaitLatch.await();
    }
  }

  @Test
  public void testCancelDuringWait() throws Exception {
    String opName = "testCancelDuringWait";
    Operation firstOperationResult = Operation.newBuilder().setName(opName).setDone(false).build();

    CountDownLatch waitStartedLatch = new CountDownLatch(1);
    CountDownLatch foreverLatch = new CountDownLatch(1);

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture.Waiter waiter = new LatchCountDownWaiter(waitStartedLatch, foreverLatch);
    OperationFuture<Color> opFuture =
        OperationFuture.create(
            operationsClient,
            startOperationFuture,
            executor,
            Color.class,
            Duration.millis(0),
            waiter);

    Truth.assertThat(opFuture.isDone()).isFalse();
    CancellationHelpers.cancelInThreadAfterLatchCountDown(opFuture, waitStartedLatch);

    startOperationFuture.set(firstOperationResult);

    CancellationException gotException = null;
    try {
      opFuture.get();
    } catch (CancellationException e) {
      gotException = e;
    }
    Truth.assertThat(gotException).isNotNull();
    Truth.assertThat(opFuture.isDone()).isTrue();
    Truth.assertThat(opFuture.isCancelled()).isTrue();

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);

    List<GeneratedMessageV3> requestsIssued = mockOperations.getRequests();
    Truth.assertThat(requestsIssued.get(requestsIssued.size() - 1))
        .isInstanceOf(CancelOperationRequest.class);
  }

  @Test
  public void testExternalCancellation() throws Exception {
    String opName = "testExternalCancellation";
    Operation firstOperationResult = Operation.newBuilder().setName(opName).setDone(false).build();

    mockOperations.addResponse(firstOperationResult.toBuilder().build());
    mockOperations.addResponse(firstOperationResult.toBuilder().build());

    com.google.rpc.Status errorStatus =
        com.google.rpc.Status.newBuilder().setCode(Status.CANCELLED.getCode().value()).build();
    mockOperations.addResponse(
        Operation.newBuilder().setName(opName).setDone(true).setError(errorStatus).build());

    SettableFuture<Operation> startOperationFuture = SettableFuture.<Operation>create();
    OperationFuture<Color> opFuture =
        OperationFuture.create(
            operationsClient, startOperationFuture, executor, Color.class, Duration.millis(0));

    startOperationFuture.set(firstOperationResult);

    CancellationException gotException = null;
    try {
      opFuture.get();
    } catch (CancellationException e) {
      gotException = e;
    }
    Truth.assertThat(gotException).isNotNull();
    Truth.assertThat(opFuture.isDone()).isTrue();
    Truth.assertThat(opFuture.isCancelled()).isTrue();

    opFuture.awaitAsyncCompletion(3, TimeUnit.SECONDS);
  }
}
