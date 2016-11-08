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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsApi;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import io.grpc.Status;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.joda.time.Duration;

/**
 * A ListenableFuture which polls a service through OperationsApi for the
 * completion of an operation.
 */
public final class OperationFuture<ResponseT extends Message>
    implements ListenableFuture<ResponseT> {
  private static final Duration POLLING_INTERVAL = Duration.standardSeconds(1);

  private final ListenableFuture<Operation> startOperationFuture;
  private final SettableFuture<ResponseT> finalResultFuture;
  private final Future<ResponseT> dataGetterFuture;
  private final CountDownLatch asyncCompletionLatch;

  /**
   * Creates an OperationFuture with the minimum required inputs, and defaults
   * the rest.
   */
  public static <ResponseT extends Message> OperationFuture<ResponseT> create(
      OperationsApi operationsApi,
      ListenableFuture<Operation> startOperationFuture,
      ScheduledExecutorService executor,
      Class<ResponseT> responseClass) {
    return create(operationsApi, startOperationFuture, executor, responseClass, POLLING_INTERVAL);
  }

  /**
   * Creates an OperationFuture with a custom polling interval.
   */
  public static <ResponseT extends Message> OperationFuture<ResponseT> create(
      OperationsApi operationsApi,
      ListenableFuture<Operation> startOperationFuture,
      ScheduledExecutorService executor,
      Class<ResponseT> responseClass,
      Duration pollingInterval) {
    return create(
        operationsApi,
        startOperationFuture,
        executor,
        responseClass,
        pollingInterval,
        new Waiter());
  }

  // package-private for testing
  @VisibleForTesting
  static <ResponseT extends Message> OperationFuture<ResponseT> create(
      OperationsApi operationsApi,
      ListenableFuture<Operation> startOperationFuture,
      ScheduledExecutorService executor,
      Class<ResponseT> responseClass,
      Duration pollingInterval,
      Waiter waiter) {
    SettableFuture<ResponseT> finalResultFuture = SettableFuture.create();
    CountDownLatch asyncCompletionLatch = new CountDownLatch(1);
    Future<ResponseT> dataGetterFuture =
        executor.submit(
            new DataGetterRunnable<ResponseT>(
                startOperationFuture,
                finalResultFuture,
                operationsApi,
                responseClass,
                pollingInterval,
                waiter,
                asyncCompletionLatch));
    OperationFuture<ResponseT> operationFuture =
        new OperationFuture<>(
            startOperationFuture, finalResultFuture, dataGetterFuture, asyncCompletionLatch);
    return operationFuture;
  }

  private OperationFuture(
      ListenableFuture<Operation> startOperationFuture,
      SettableFuture<ResponseT> finalResultFuture,
      Future<ResponseT> dataGetterFuture,
      CountDownLatch asyncCompletionLatch) {
    this.startOperationFuture = startOperationFuture;
    this.finalResultFuture = finalResultFuture;
    this.dataGetterFuture = dataGetterFuture;
    this.asyncCompletionLatch = asyncCompletionLatch;
  }

  private static class DataGetterRunnable<ResponseT extends Message>
      implements Callable<ResponseT> {
    private final ListenableFuture<Operation> startOperationFuture;
    private final SettableFuture<ResponseT> finalResultFuture;
    private final OperationsApi operationsApi;
    private final Class<ResponseT> responseClass;
    private final Duration pollingInterval;
    private final Waiter waiter;
    private final CountDownLatch asyncCompletionLatch;

    public DataGetterRunnable(
        ListenableFuture<Operation> startOperationFuture,
        SettableFuture<ResponseT> finalResultFuture,
        OperationsApi operationsApi,
        Class<ResponseT> responseClass,
        Duration pollingInterval,
        Waiter waiter,
        CountDownLatch asyncCompletionLatch) {
      this.startOperationFuture = startOperationFuture;
      this.finalResultFuture = finalResultFuture;
      this.operationsApi = operationsApi;
      this.responseClass = responseClass;
      this.pollingInterval = pollingInterval;
      this.waiter = waiter;
      this.asyncCompletionLatch = asyncCompletionLatch;
    }

    @Override
    public ResponseT call() {
      try {
        callImpl();
      } finally {
        asyncCompletionLatch.countDown();
      }
      return null;
    }

    public void callImpl() {
      Operation firstOperation = null;
      try {
        firstOperation = startOperationFuture.get();
        if (firstOperation.getDone()) {
          setResultFromOperation(finalResultFuture, firstOperation, responseClass);
          return;
        }
        do {
          // TODO: switch implementation from polling to scheduled execution
          waiter.wait(pollingInterval);
          Operation latestOperation = operationsApi.getOperation(firstOperation.getName());
          if (latestOperation.getDone()) {
            if (isCancelled(latestOperation)) {
              finalResultFuture.cancel(true);
            } else {
              setResultFromOperation(finalResultFuture, latestOperation, responseClass);
            }
            return;
          }
        } while (true);
      } catch (InterruptedException e) {
        try {
          if (firstOperation != null) {
            operationsApi.cancelOperation(firstOperation.getName());
          }
          if (!startOperationFuture.isDone()) {
            startOperationFuture.cancel(true);
          }
          finalResultFuture.cancel(true);

          Thread.currentThread().interrupt();
          return;
        } catch (Exception e2) {
          finalResultFuture.cancel(true);
          Thread.currentThread().interrupt();
          return;
        }
      } catch (Throwable e) {
        finalResultFuture.setException(e);
        return;
      }
    }
  }

  /**
   * If last Operation's value of `done` is true, returns false;
   * otherwise, issues Operations.CancelOperation and returns true.
   */
  @Override
  public final boolean cancel(boolean mayInterruptIfRunning) {
    dataGetterFuture.cancel(mayInterruptIfRunning);
    // if the operation was cancelled before the executor could even start
    // DataGetterRunnable, then finalResultFuture also needs to be canceled here;
    // It won't break anything if it has already been canceled.
    return finalResultFuture.cancel(mayInterruptIfRunning);
  }

  /**
   * Waits on the polling loop on Operations.GetOperation, and once Operation.done
   * is true, then returns Operation.response if successful or throws
   * ExecutionException containing an ApiException with the status code set to
   * Operation.error if not successful.
   */
  @Override
  public final ResponseT get() throws InterruptedException, ExecutionException {
    return finalResultFuture.get();
  }

  /**
   * Waits on the polling loop on Operations.GetOperation, and once Operation.done
   * is true, then returns Operation.response if successful or throws
   * ExecutionException containing an ApiException with the status code set to
   * Operation.error if not successful.
   */
  @Override
  public final ResponseT get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return finalResultFuture.get(timeout, unit);
  }

  /**
   * Returns true if the operation has been cancelled.
   */
  @Override
  public final boolean isCancelled() {
    return finalResultFuture.isCancelled();
  }

  /**
   * Issues Operations.GetOperation and returns value of Operation.done.
   * @see java.util.concurrent.Future#isDone()
   */
  @Override
  public final boolean isDone() {
    return finalResultFuture.isDone();
  }

  /**
   * Enters a polling loop on Operations.GetOperation, and once Operation.done
   * is true, notifies the listener.
   * Normally, Futures.addCallback would be used instead of calling this directly.
   */
  @Override
  public final void addListener(Runnable listener, Executor executor) {
    finalResultFuture.addListener(listener, executor);
  }

  /**
   * Returns the value of Operation.name from the initial Operation object returned
   * from the initial call to start the Operation.
   * Blocks if the initial call to start the Operation hasn't returned yet.
   */
  public final String getOperationName() throws InterruptedException, ExecutionException {
    return startOperationFuture.get().getName();
  }

  /**
   * Returns the value of Operation.metadata from the initial Operation object
   * returned from the initial call to start the Operation.
   * Blocks if the initial call to start the Operation hasn't returned yet.
   */
  public final Any getMetadata() throws InterruptedException, ExecutionException {
    return startOperationFuture.get().getMetadata();
  }

  /**
   * Returns the data from the last call to OperationsApi.GetOperation (or if only
   * the initial API call has been made, the data from that call).
   * Blocks if the initial call to start the Operation hasn't returned yet.
   */
  public final Operation getLastOperationData() throws InterruptedException, ExecutionException {
    return startOperationFuture.get();
  }

  /**
   * Awaits any ongoing asynchronous work.
   */
  public final void awaitAsyncCompletion() throws InterruptedException {
    asyncCompletionLatch.await();
  }

  /**
   * Awaits any ongoing asynchronous work.
   */
  public final void awaitAsyncCompletion(long timeout, TimeUnit unit) throws InterruptedException {
    asyncCompletionLatch.await(timeout, unit);
  }

  private static <ResponseT extends Message> void setResultFromOperation(
      SettableFuture<ResponseT> resultFuture, Operation operation, Class<ResponseT> responseClass) {
    Status status = Status.fromCodeValue(operation.getError().getCode());
    if (!status.equals(Status.OK)) {
      String message =
          "Operation with name \"" + operation.getName() + "\" failed with status = " + status;
      resultFuture.setException(new ApiException(message, null, status.getCode(), false));
    } else {
      Any responseAny = operation.getResponse();
      if (responseAny.is(responseClass)) {
        ResponseT response;
        try {
          response = responseAny.unpack(responseClass);
          resultFuture.set(response);
        } catch (InvalidProtocolBufferException e) {
          String message =
              "Operation with name \""
                  + operation.getName()
                  + "\" succeeded, but encountered a problem unpacking it.";
          resultFuture.setException(new ApiException(message, e, status.getCode(), false));
        }
      } else {
        String message =
            "Operation with name \""
                + operation.getName()
                + "\" succeeded, but it is not the right type; "
                + "expected \""
                + responseClass.getName()
                + "\" but found \""
                + responseAny.getTypeUrl()
                + "\"";
        resultFuture.setException(new ClassCastException(message));
      }
    }
  }

  private static boolean isCancelled(Operation operation) {
    if (operation.getError() != null) {
      Status status = Status.fromCodeValue(operation.getError().getCode());
      if (status.getCode().equals(Status.Code.CANCELLED)) {
        return true;
      }
    }
    return false;
  }

  static class Waiter {
    public void wait(Duration duration) throws InterruptedException {
      Thread.sleep(duration.getMillis());
    }
  }
}
