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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.AbstractApiFuture;
import com.google.api.core.ApiFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.BetaApi;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.OperationCallSettings;
import com.google.api.gax.rpc.OperationCallable;
import com.google.api.gax.rpc.OperationFuture;
import com.google.longrunning.Operation;
import com.google.protobuf.Message;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An ApiFuture which tracks polling of a service, typically started by {@link
 * OperationCallable#futureCall(Object, ApiCallContext)}. The polling is done periodically, based on
 * the {@link com.google.api.gax.retrying.TimedRetryAlgorithm} specified in {@link
 * OperationCallSettings} provided during creation of the corresponding {@link OperationCallable}.
 *
 * <p>This class is thread-safe.
 */
@BetaApi
public final class GrpcOperationFuture<ResponseT extends Message, MetadataT extends Message>
    extends AbstractApiFuture<ResponseT>
    implements OperationFuture<ResponseT, MetadataT, Operation> {
  private final Object lock = new Object();

  private final RetryingFuture<Operation> pollingFuture;
  private final ApiFuture<Operation> initialFuture;
  private final ApiFuture<ResponseT> resultFuture;
  private final ApiFunction<Operation, MetadataT> metadataTransformer;

  private volatile ApiFuture<Operation> peekedAttemptResult;
  private volatile ApiFuture<MetadataT> peekedPollResult;
  private volatile ApiFuture<Operation> gottenAttemptResult;
  private volatile ApiFuture<MetadataT> gottenPollResult;

  /**
   * Creates a new operation future instance.
   *
   * @param pollingFuture retrying future which tracks polling of the server operation (in most
   *     cases with exponential upper bounded intervals)
   * @param initialFuture the initial future which started the operation on the server side
   * @param responseTransformer transformer which unpacks a {@link ResponseT} object from an {@link
   *     Operation} object
   * @param metadataTransformer transformer which unpacks a {@link MetadataT} object from an {@link
   *     Operation} object
   */
  public GrpcOperationFuture(
      RetryingFuture<Operation> pollingFuture,
      ApiFuture<Operation> initialFuture,
      ApiFunction<Operation, ResponseT> responseTransformer,
      ApiFunction<Operation, MetadataT> metadataTransformer) {
    this.pollingFuture = checkNotNull(pollingFuture);
    this.initialFuture = checkNotNull(initialFuture);
    this.resultFuture = ApiFutures.transform(pollingFuture, responseTransformer);
    this.metadataTransformer = checkNotNull(metadataTransformer);
  }

  @Override
  public void addListener(Runnable listener, Executor executor) {
    pollingFuture.addListener(listener, executor);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return pollingFuture.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return pollingFuture.isCancelled();
  }

  @Override
  public boolean isDone() {
    return pollingFuture.isDone();
  }

  @Override
  public ResponseT get() throws InterruptedException, ExecutionException {
    pollingFuture.get();
    return resultFuture.get();
  }

  @Override
  public ResponseT get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    pollingFuture.get(timeout, unit);
    return resultFuture.get();
  }

  @Override
  public String getName() throws ExecutionException, InterruptedException {
    return initialFuture.get().getName();
  }

  @Override
  public ApiFuture<Operation> getInitialFuture() {
    return initialFuture;
  }

  // Note, the following two methods are not duplicates of each other even though code checking
  // tools may indicate so. They assign multiple different class fields.
  @Override
  public ApiFuture<MetadataT> peekMetadata() {
    ApiFuture<Operation> future = pollingFuture.peekAttemptResult();
    synchronized (lock) {
      if (peekedAttemptResult == future) {
        return peekedPollResult;
      }
      peekedAttemptResult = future;
      peekedPollResult = ApiFutures.transform(peekedAttemptResult, metadataTransformer);
      return peekedPollResult;
    }
  }

  @Override
  public ApiFuture<MetadataT> getMetadata() {
    ApiFuture<Operation> future = pollingFuture.getAttemptResult();
    synchronized (lock) {
      if (gottenAttemptResult == future) {
        return gottenPollResult;
      }
      gottenAttemptResult = future;
      gottenPollResult = ApiFutures.transform(gottenAttemptResult, metadataTransformer);
      return gottenPollResult;
    }
  }
}
