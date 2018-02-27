/*
 * Copyright 2018, Google LLC All rights reserved.
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
package com.google.api.gax.grpc.batching;

import com.google.api.core.ApiFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.batching.BatchingFlowController;
import com.google.api.gax.batching.BatchingThreshold;
import com.google.api.gax.batching.FlowController.FlowControlException;
import com.google.api.gax.batching.RequestBuilder;
import com.google.api.gax.batching.ThresholdBatchReceiver;
import com.google.api.gax.rpc.Batch;
import com.google.api.gax.rpc.BatchedFuture;
import com.google.api.gax.rpc.BatchedRequestIssuer;
import com.google.api.gax.rpc.BatchingDescriptor;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.threeten.bp.Duration;

public class Batcher<RequestT, ResponseT> {
  final BatchingDescriptor<RequestT, ResponseT> descriptor;
  final UnaryCallable<RequestT, ResponseT> innerCallable;

  private final ArrayList<BatchingThreshold<RequestT>> thresholds;
  private final ScheduledExecutorService executor;
  private final Duration maxDelay;
  private final ThresholdBatchReceiver<Batch<RequestT, ResponseT>> receiver;
  private final BatchingFlowController<RequestT> flowController;

  private final ReentrantLock lock = new ReentrantLock();
  private RequestBuilder<RequestT> requestBuilder;
  private Future<?> currentAlarmFuture;

  List<BatchedRequestIssuer<ResponseT>> requestIssuers = Lists.newArrayList();
  long requestSize = 0;

  private final Runnable pushCurrentBatchRunnable =
      new Runnable() {
        @Override
        public void run() {
          pushCurrentBatch();
        }
      };

  public Batcher(
      BatchingDescriptor<RequestT, ResponseT> descriptor,
      UnaryCallable<RequestT, ResponseT> innerCallable,
      ArrayList<BatchingThreshold<RequestT>> thresholds,
      ScheduledExecutorService executor,
      Duration maxDelay,
      ThresholdBatchReceiver<Batch<RequestT, ResponseT>> receiver,
      BatchingFlowController<RequestT> flowController) {
    this.descriptor = descriptor;
    this.innerCallable = innerCallable;
    this.thresholds = thresholds;
    this.executor = executor;
    this.maxDelay = maxDelay;
    this.receiver = receiver;
    this.flowController = flowController;
    this.requestBuilder = descriptor.getRequestBuilder();
  }

  public ApiFuture<ResponseT> add(RequestT r) throws FlowControlException {
    // We need to reserve resources from flowController outside the lock, so that they can be
    // released by pushCurrentBatch().
    flowController.reserve(r);
    lock.lock();
    try {
      boolean anyThresholdReached = isAnyThresholdReached(r);

      requestBuilder.appendRequest(r);
      BatchedFuture<ResponseT> future = new BatchedFuture<>();
      requestIssuers.add(new BatchedRequestIssuer<>(future, descriptor.countElements(r)));
      requestSize += descriptor.countBytes(r);

      if (!anyThresholdReached && currentAlarmFuture == null) {
        currentAlarmFuture =
            executor.schedule(pushCurrentBatchRunnable, maxDelay.toMillis(), TimeUnit.MILLISECONDS);
      } else if (anyThresholdReached) {
        pushCurrentBatch();
      }

      return future;
    } finally {
      lock.unlock();
    }
  }

  public ApiFuture<Void> pushCurrentBatch() {
    final Batch<RequestT, ResponseT> batch = removeBatch();
    if (batch == null) {
      return ApiFutures.immediateFuture(null);
    } else {
      return ApiFutures.transform(
          receiver.processBatch(batch), new ReleaseResourcesFunction<>(batch.getRequest()));
    }
  }

  private Batch<RequestT, ResponseT> removeBatch() {
    RequestT request = requestBuilder.build();
    if (currentAlarmFuture != null) {
      currentAlarmFuture.cancel(false);
      currentAlarmFuture = null;
    }

    requestBuilder = descriptor.getRequestBuilder();
    resetThresholds();

    Batch<RequestT, ResponseT> batch =
        new Batch<>(request, requestIssuers, innerCallable, requestSize);
    requestSize = 0;
    requestIssuers = Lists.newArrayList();
    return batch;
  }

  private boolean isAnyThresholdReached(RequestT e) {
    for (BatchingThreshold<RequestT> threshold : thresholds) {
      threshold.accumulate(e);
      if (threshold.isThresholdReached()) {
        return true;
      }
    }
    return false;
  }

  private void resetThresholds() {
    for (int i = 0; i < thresholds.size(); i++) {
      thresholds.set(i, thresholds.get(i).copyWithZeroedValue());
    }
  }

  private class ReleaseResourcesFunction<T> implements ApiFunction<T, Void> {
    private final RequestT request;

    private ReleaseResourcesFunction(RequestT request) {
      this.request = request;
    }

    @Override
    public Void apply(T input) {
      flowController.release(request);
      return null;
    }
  }
}
