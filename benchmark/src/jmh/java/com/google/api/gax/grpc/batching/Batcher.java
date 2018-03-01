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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.threeten.bp.Duration;

/**
 * Queues up elements until either a duration of time has passed or any threshold in a given set of
 * thresholds is breached, and then delivers the elements in a batch to the consumer.
 */
public class Batcher<RequestT, ResponseT> {

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

  private final Runnable pushCurrentBatchRunnable =
      new Runnable() {
        @Override
        public void run() {
          pushCurrentBatch();
        }
      };

  private final ArrayList<BatchingThreshold<RequestT>> thresholds;
  private final ScheduledExecutorService executor;
  private final Duration maxDelay;
  private final ThresholdBatchReceiver<Batch<RequestT, ResponseT>> receiver;
  private final BatchingFlowController<RequestT> flowController;

  private final ReentrantLock lock = new ReentrantLock();

  private RequestBuilder<RequestT> requestBuilder;
  final BatchingDescriptor<RequestT, ResponseT> descriptor;
  final UnaryCallable<RequestT, ResponseT> innerCallable;
  List<BatchedRequestIssuer<ResponseT>> requestIssuers = Lists.newArrayList();
  long requestSize = 0;

  private Future<?> currentAlarmFuture;


  private Batcher(Builder<RequestT, ResponseT> builder) {
    this.thresholds = Lists.newArrayList(builder.thresholds);
    this.executor = builder.executor;
    this.maxDelay = builder.maxDelay;
    this.receiver = builder.receiver;
    this.flowController = builder.flowController;
    this.requestBuilder = builder.descriptor.getRequestBuilder();
    this.descriptor = builder.descriptor;
    this.innerCallable = builder.innerCallable;

    resetThresholds();
  }

  /** Builder for a Batcher. */
  public static class Builder<RequestT, ResponseT> {
    private Collection<BatchingThreshold<RequestT>> thresholds;
    private ScheduledExecutorService executor;
    private Duration maxDelay;
    private ThresholdBatchReceiver<Batch<RequestT, ResponseT>> receiver;
    private BatchingFlowController<RequestT> flowController;

    private BatchingDescriptor<RequestT, ResponseT> descriptor;
    private UnaryCallable<RequestT, ResponseT> innerCallable;

    private Builder() {}

    /** Set the executor for the ThresholdBatcher. */
    public Builder<RequestT, ResponseT> setExecutor(ScheduledExecutorService executor) {
      this.executor = executor;
      return this;
    }

    /** Set the max delay for a batch. This is counted from the first item added to a batch. */
    public Builder<RequestT, ResponseT> setMaxDelay(Duration maxDelay) {
      this.maxDelay = maxDelay;
      return this;
    }

    /** Set the thresholds for the ThresholdBatcher. */
    public Builder<RequestT, ResponseT> setThresholds(Collection<BatchingThreshold<RequestT>> thresholds) {
      this.thresholds = Lists.newArrayList(thresholds);
      return this;
    }

    /** Set the threshold batch receiver for the ThresholdBatcher. */
    public Builder<RequestT, ResponseT> setReceiver(ThresholdBatchReceiver<Batch<RequestT, ResponseT>> receiver) {
      this.receiver = receiver;
      return this;
    }

    /** Set the flow controller for the ThresholdBatcher. */
    public Builder<RequestT, ResponseT> setFlowController(BatchingFlowController<RequestT> flowController) {
      this.flowController = flowController;
      return this;
    }

    public Builder<RequestT, ResponseT> setDescriptor(BatchingDescriptor<RequestT, ResponseT> descriptor) {
      this.descriptor = descriptor;
      return this;
    }

    public Builder<RequestT, ResponseT> setInnerCallable(UnaryCallable<RequestT, ResponseT> innerCallable) {
      this.innerCallable = innerCallable;
      return this;
    }

    /** Build the ThresholdBatcher. */
    public Batcher<RequestT, ResponseT> build() {
      return new Batcher<>(this);
    }
  }

    /** Get a new builder for a Batcher. */
  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<>();
  }

  /**
   * Adds an element to the batcher. If the element causes the collection to go past any of the
   * thresholds, the batch will be sent to the {@code ThresholdBatchReceiver}.
   */
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

      if (currentAlarmFuture == null) {
        // Schedule a job only when no thresholds have been exceeded, otherwise it will be
        // immediately cancelled
        if (!anyThresholdReached) {
          currentAlarmFuture =
              executor.schedule(
                  pushCurrentBatchRunnable, maxDelay.toMillis(), TimeUnit.MILLISECONDS);
        }
      }

      if (anyThresholdReached) {
        pushCurrentBatch();
      }

      return future;
    } finally {
      lock.unlock();
    }
  }

  /** * Package-private for use in testing. */
  @VisibleForTesting
  boolean isEmpty() {
    lock.lock();
    try {
      return requestSize == 0;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Push the current batch to the batch receiver. Returns an ApiFuture that completes once the
   * batch has been processed by the batch receiver and the flow controller resources have been
   * released.
   *
   * <p>Note that this future can complete for the current batch before previous batches have
   * completed, so it cannot be depended upon for flushing.
   */
  @VisibleForTesting
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
    lock.lock();
    try {
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
      resetThresholds();
      return batch;
    } finally {
      lock.unlock();
    }
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
}
