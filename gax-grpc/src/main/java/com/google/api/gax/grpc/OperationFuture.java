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

import com.google.api.core.ApiFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.BetaApi;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.longrunning.Operation;
import com.google.protobuf.Message;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An ApiFuture which tracks polling of a service, typically started by {@link
 * OperationCallable#futureCall(Object, CallContext)}. The polling is done periodically, based on
 * the {@link com.google.api.gax.retrying.TimedRetryAlgorithm} specified in {@link
 * OperationCallSettings} provided during creation of the corresponding {@link OperationCallable}.
 *
 * <p>This class is thread-safe.
 */
@BetaApi
public final class OperationFuture<ResponseT extends Message, MetadataT extends Message>
    implements ApiFuture<ResponseT> {
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
  public OperationFuture(
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

  /**
   * Returns the value of {@code Operation.name} from the initial Operation object returned from the
   * initial call to start the {@code Operation}. Blocks if the initial call to start the {@code
   * Operation} hasn't returned yet.
   */
  public String getName() throws ExecutionException, InterruptedException {
    return initialFuture.get().getName();
  }

  /**
   * Returns the {@code Operation} future of the initial request which started this {@code
   * OperationFuture}.
   */
  public ApiFuture<Operation> getInitialFuture() {
    return initialFuture;
  }

  /**
   * Peeks at the metadata of the operation tracked by this {@link OperationFuture}. If the initial
   * future hasn't completed yet this method returns {@code null}, otherwise it returns the latest
   * metadata returned from the server (i.e. either initial call metadata or the metadata received
   * from the latest completed poll iteration).
   *
   * <p>If not {@code null}, the returned result is guaranteed to be an already completed future, so
   * {@link ApiFuture#isDone()} will always be {@code true} and {@link ApiFuture#get()} will always
   * be non-blocking.
   *
   * <p>Note, some APIs may return {@code null} in metadata response message. In such cases this
   * method may return a non-null future whose {@code get()} method will return {@code null}. This
   * behavior is API specific an should be considered a valid case, which indicates that the recent
   * poll request has completed, but no specific metadata was provided by the server (i.e. most
   * probably providing metadata for an intermediate result is not supported by the server).
   *
   * <p>This method should be used to check operation progress without blocking current thread.
   * Since this method returns metadata from the latest completed poll, it is potentially slightly
   * stale compared to the most recent data. To get the most recent data and/or get notified when
   * the current scheduled poll request completes use the {@link #getMetadata()} method instead.
   *
   * <p>If this operation future is completed, this method always returns the metadata from the last
   * poll request (which completed the operation future).
   *
   * <p>If this operation future failed, this method may (depending on the failure type) return a
   * non-failing future, representing the metadata from the last poll request (which failed the
   * operation future).
   *
   * <p>If this operation future was cancelled, this method returns a canceled metatata future as
   * well.
   *
   * <p>In general this method behaves similarly to {@link RetryingFuture#peekAttemptResult()}.
   */
  // Note, the following two methods are not duplicates of each other even though code checking
  // tools may indicate so. They assign multiple different class fields.
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

  /**
   * Gets the metadata of the operation tracked by this {@link OperationFuture}. This method returns
   * the current poll metadata result (or the initial call metadata if it hasn't completed yet). The
   * returned future completes once the current scheduled poll request (or the initial request if it
   * hasn't completed yet) is executed and response is received from the server. The time when the
   * polling request is executed is determined by the underlying polling algorithm.
   *
   * <p>Adding direct executor (same thread) callbacks to the future returned by this method is
   * strongly not recommended, since the future is resolved under retrying future's internal lock
   * and may affect the operation polling process. Adding separate thread callbacks is ok.
   *
   * <p>Note, some APIs may return {@code null} in metadata response message. In such cases this
   * method may return a non-null future whose {@code get()} method will return {@code null}. This
   * behavior is API specific an should be considered a valid case, which indicates that the recent
   * poll request has completed, but no specific metadata was provided by the server. (i.e. most
   * probably providing metadata for an intermediate result is not supported by the server).
   *
   * <p>In most cases this method returns a future which is not completed yet, so calling {@link
   * ApiFuture#get()} is a potentially blocking operation. To get metadata without blocking the
   * current thread use the {@link #peekMetadata()} method instead.
   *
   * <p>If this operation future is completed, this method always returns the metadata from the last
   * poll request (which completed the operation future).
   *
   * <p>If this operation future failed, this method may (depending on the failure type) return a
   * non-failing future, representing the metadata from the last poll request (which failed the
   * operation future).
   *
   * <p>If this operation future was cancelled, this method returns a canceled metatata future as
   * well.
   *
   * <p>In general this method behaves similarly to {@link RetryingFuture#getAttemptResult()}.
   */
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
