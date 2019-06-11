/*
 * Copyright 2019 Google LLC
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
package com.google.api.gax.batching.v2;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Queues up the elements until {@link #flush()} is called; once batching is over, returned future
 * resolves.
 *
 * <p>This class is not thread-safe, and expects to be used from a single thread.
 *
 * @param <ElementT> The type of each individual element to be batched.
 * @param <ElementResultT> The type of the result for each individual element.
 * @param <RequestT> The type of the request that will contain the accumulated elements.
 * @param <ResponseT> The type of the response that will unpack into individual element results.
 */
@BetaApi("The surface for batching is not stable yet and may change in the future.")
@InternalApi
public class BatcherImpl<ElementT, ElementResultT, RequestT, ResponseT>
    implements Batcher<ElementT, ElementResultT> {

  private final BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT>
      batchingDescriptor;
  private final UnaryCallable<RequestT, ResponseT> unaryCallable;
  private final RequestT prototype;

  private Batch<ElementT, ElementResultT, RequestT, ResponseT> currentOpenBatch;
  private final AtomicInteger numOfOutstandingBatches = new AtomicInteger(0);
  private final Object flushLock = new Object();
  private volatile boolean isClosed = false;

  public BatcherImpl(
      BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> batchingDescriptor,
      UnaryCallable<RequestT, ResponseT> unaryCallable,
      RequestT prototype) {
    this.batchingDescriptor =
        Preconditions.checkNotNull(batchingDescriptor, "batching descriptor cannot be null");
    this.unaryCallable = Preconditions.checkNotNull(unaryCallable, "callable cannot be null");
    this.prototype = Preconditions.checkNotNull(prototype, "request prototype cannot be null");
  }

  /** {@inheritDoc} */
  @Override
  public ApiFuture<ElementResultT> add(ElementT element) {
    Preconditions.checkState(!isClosed, "Cannot add elements on a closed batcher");

    if (currentOpenBatch == null) {
      currentOpenBatch = new Batch<>(prototype, batchingDescriptor);
    }

    SettableApiFuture<ElementResultT> result = SettableApiFuture.create();
    currentOpenBatch.add(element, result);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void flush() throws InterruptedException {
    sendBatch();
    awaitAllOutstandingBatches();
  }

  /** Sends accumulated elements asynchronously for batching. */
  private void sendBatch() {
    if (currentOpenBatch == null) {
      return;
    }
    final Batch<ElementT, ElementResultT, RequestT, ResponseT> accumulatedBatch = currentOpenBatch;
    currentOpenBatch = null;

    final ApiFuture<ResponseT> batchResponse =
        unaryCallable.futureCall(accumulatedBatch.builder.build());

    numOfOutstandingBatches.incrementAndGet();
    ApiFutures.addCallback(
        batchResponse,
        new ApiFutureCallback<ResponseT>() {
          @Override
          public void onSuccess(ResponseT response) {
            try {
              accumulatedBatch.onBatchSuccess(response);
            } finally {
              onBatchCompletion();
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            try {
              accumulatedBatch.onBatchFailure(throwable);
            } finally {
              onBatchCompletion();
            }
          }
        },
        directExecutor());
  }

  private void onBatchCompletion() {
    if (numOfOutstandingBatches.decrementAndGet() == 0) {
      synchronized (flushLock) {
        flushLock.notifyAll();
      }
    }
  }

  private void awaitAllOutstandingBatches() throws InterruptedException {
    while (numOfOutstandingBatches.get() > 0) {
      synchronized (flushLock) {
        flushLock.wait();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws InterruptedException {
    isClosed = true;
    flush();
  }

  /**
   * This class represent one logical Batch. It accumulates all the elements and their corresponding
   * future element results for one batch.
   */
  private static class Batch<ElementT, ElementResultT, RequestT, ResponseT> {
    private final RequestBuilder<ElementT, RequestT> builder;
    private final List<SettableApiFuture<ElementResultT>> results;
    private final BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> descriptor;

    private Batch(
        RequestT prototype,
        BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> descriptor) {
      this.descriptor = descriptor;
      this.builder = descriptor.newRequestBuilder(prototype);
      this.results = new ArrayList<>();
    }

    void add(ElementT element, SettableApiFuture<ElementResultT> result) {
      builder.add(element);
      results.add(result);
    }

    void onBatchSuccess(ResponseT response) {
      try {
        descriptor.splitResponse(response, results);
      } catch (Exception ex) {
        onBatchFailure(ex);
      }
    }

    void onBatchFailure(Throwable throwable) {
      try {
        descriptor.splitException(throwable, results);
      } catch (Exception ex) {
        for (SettableApiFuture<ElementResultT> result : results) {
          result.setException(ex);
        }
      }
    }
  }
}
