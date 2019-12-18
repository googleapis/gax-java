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
package com.google.api.gax.batching;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
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
@InternalApi("For google-cloud-java client use only")
public class BatcherImpl<ElementT, ElementResultT, RequestT, ResponseT>
    implements Batcher<ElementT, ElementResultT> {

  private static final Logger LOG = Logger.getLogger(BatcherImpl.class.getName());
  private final BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT>
      batchingDescriptor;
  private final UnaryCallable<RequestT, ResponseT> unaryCallable;
  private final RequestT prototype;
  private final BatchingSettings batchingSettings;
  private final BatcherReference currentBatcherReference;

  private Batch<ElementT, ElementResultT, RequestT, ResponseT> currentOpenBatch;
  private final AtomicInteger numOfOutstandingBatches = new AtomicInteger(0);
  private final Object flushLock = new Object();
  private final Object elementLock = new Object();
  private final Future<?> scheduledFuture;
  private volatile boolean isClosed = false;
  private final BatcherStats batcherStats = new BatcherStats();

  /**
   * @param batchingDescriptor a {@link BatchingDescriptor} for transforming individual elements
   *     into wrappers request and response.
   * @param unaryCallable a {@link UnaryCallable} object.
   * @param prototype a {@link RequestT} object.
   * @param batchingSettings a {@link BatchingSettings} with configuration of thresholds.
   */
  public BatcherImpl(
      BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> batchingDescriptor,
      UnaryCallable<RequestT, ResponseT> unaryCallable,
      RequestT prototype,
      BatchingSettings batchingSettings,
      ScheduledExecutorService executor) {

    this.batchingDescriptor =
        Preconditions.checkNotNull(batchingDescriptor, "batching descriptor cannot be null");
    this.unaryCallable = Preconditions.checkNotNull(unaryCallable, "callable cannot be null");
    this.prototype = Preconditions.checkNotNull(prototype, "request prototype cannot be null");
    this.batchingSettings =
        Preconditions.checkNotNull(batchingSettings, "batching setting cannot be null");
    Preconditions.checkNotNull(executor, "executor cannot be null");
    currentOpenBatch = new Batch<>(prototype, batchingDescriptor, batchingSettings, batcherStats);

    if (batchingSettings.getDelayThreshold() != null) {
      long delay = batchingSettings.getDelayThreshold().toMillis();
      PushCurrentBatchRunnable<ElementT, ElementResultT, RequestT, ResponseT> runnable =
          new PushCurrentBatchRunnable<>(this);
      scheduledFuture =
          executor.scheduleWithFixedDelay(runnable, delay, delay, TimeUnit.MILLISECONDS);
    } else {
      scheduledFuture = Futures.immediateCancelledFuture();
    }
    currentBatcherReference = new BatcherReference(this);
  }

  /** {@inheritDoc} */
  @Override
  public ApiFuture<ElementResultT> add(ElementT element) {
    Preconditions.checkState(!isClosed, "Cannot add elements on a closed batcher");
    SettableApiFuture<ElementResultT> result = SettableApiFuture.create();

    synchronized (elementLock) {
      currentOpenBatch.add(element, result);
    }

    if (currentOpenBatch.hasAnyThresholdReached()) {
      sendOutstanding();
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void flush() throws InterruptedException {
    sendOutstanding();
    awaitAllOutstandingBatches();
  }

  /** {@inheritDoc} */
  @Override
  public void sendOutstanding() {
    final Batch<ElementT, ElementResultT, RequestT, ResponseT> accumulatedBatch;

    synchronized (elementLock) {
      if (currentOpenBatch.isEmpty()) {
        return;
      }
      accumulatedBatch = currentOpenBatch;
      currentOpenBatch = new Batch<>(prototype, batchingDescriptor, batchingSettings, batcherStats);
    }

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
    if (isClosed) {
      return;
    }
    flush();
    scheduledFuture.cancel(true);
    isClosed = true;
    currentBatcherReference.closed = true;
    currentBatcherReference.clear();
    BatchingException exception = batcherStats.asException();
    if (exception != null) {
      throw exception;
    }
  }

  /**
   * This class represent one logical Batch. It accumulates all the elements and their corresponding
   * future results for one batch.
   */
  private static class Batch<ElementT, ElementResultT, RequestT, ResponseT> {
    private final BatchingRequestBuilder<ElementT, RequestT> builder;
    private final List<SettableApiFuture<ElementResultT>> results;
    private final BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> descriptor;
    private final BatcherStats batcherStats;
    private final long elementThreshold;
    private final long bytesThreshold;

    private long elementCounter = 0;
    private long byteCounter = 0;

    private Batch(
        RequestT prototype,
        BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> descriptor,
        BatchingSettings batchingSettings,
        BatcherStats batcherStats) {
      this.descriptor = descriptor;
      this.builder = descriptor.newRequestBuilder(prototype);
      this.results = new ArrayList<>();
      Long elementCountThreshold = batchingSettings.getElementCountThreshold();
      this.elementThreshold = elementCountThreshold == null ? 0 : elementCountThreshold;
      Long requestByteThreshold = batchingSettings.getRequestByteThreshold();
      this.bytesThreshold = requestByteThreshold == null ? 0 : requestByteThreshold;
      this.batcherStats = batcherStats;
    }

    void add(ElementT element, SettableApiFuture<ElementResultT> result) {
      builder.add(element);
      results.add(result);
      elementCounter++;
      byteCounter += descriptor.countBytes(element);
    }

    void onBatchSuccess(ResponseT response) {
      try {
        descriptor.splitResponse(response, results);
        batcherStats.recordBatchElementsCompletion(results);
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
      batcherStats.recordBatchFailure(throwable);
    }

    boolean isEmpty() {
      return elementCounter == 0;
    }

    boolean hasAnyThresholdReached() {
      return elementCounter >= elementThreshold || byteCounter >= bytesThreshold;
    }
  }

  /**
   * Executes {@link #sendOutstanding()} on a periodic interval.
   *
   * <p>This class holds a weak reference to the Batcher instance and cancels polling if the target
   * Batcher has been garbage collected.
   */
  @VisibleForTesting
  static class PushCurrentBatchRunnable<ElementT, ElementResultT, RequestT, ResponseT>
      implements Runnable {

    private Future<?> scheduledFuture;
    private final WeakReference<BatcherImpl<ElementT, ElementResultT, RequestT, ResponseT>>
        batcherReferent;

    PushCurrentBatchRunnable(BatcherImpl<ElementT, ElementResultT, RequestT, ResponseT> batcher) {
      this.batcherReferent = new WeakReference<>(batcher);
    }

    @Override
    public void run() {
      BatcherImpl<ElementT, ElementResultT, RequestT, ResponseT> batcher = batcherReferent.get();
      if (batcher == null) {
        scheduledFuture.cancel(true);
      } else {
        batcher.sendOutstanding();
      }
    }

    void setScheduledFuture(Future<?> scheduledFuture) {
      this.scheduledFuture = scheduledFuture;
    }

    boolean isCancelled() {
      return scheduledFuture.isCancelled();
    }
  }

  /**
   * On every Batcher allocation this class will check for garbage collected batchers that were
   * never closed and emit warning logs.
   */
  @VisibleForTesting
  static final class BatcherReference extends WeakReference<BatcherImpl> {

    private static final ReferenceQueue<BatcherImpl> refQueue = new ReferenceQueue<>();

    // Retain the References so they don't get GC'd
    private static final ConcurrentMap<BatcherReference, BatcherReference> refs =
        new ConcurrentHashMap<>();

    private static final String ALLOCATION_SITE_PROPERTY_NAME =
        "com.google.api.gax.batching.Batcher.enableAllocationTracking";

    private static final boolean ENABLE_ALLOCATION_TRACKING =
        Boolean.parseBoolean(System.getProperty(ALLOCATION_SITE_PROPERTY_NAME, "true"));
    private static final RuntimeException missingCallSite = missingCallSite();

    private final Reference<RuntimeException> allocationSite;
    private volatile boolean closed;

    BatcherReference(BatcherImpl referent) {
      super(referent, refQueue);
      // allocationSite is softReference to make it garbage collectible, but delay it as long as
      // possible as BatcherReference can only be weakly referred.
      allocationSite =
          new SoftReference<>(
              ENABLE_ALLOCATION_TRACKING
                  ? new RuntimeException("Batcher allocation site")
                  : missingCallSite);
      refs.put(this, this);
      cleanQueue();
    }

    /**
     * This clear() is *not* called automatically by the JVM. As this is a weak ref, the reference
     * will be cleared automatically by the JVM, but will not be removed from {@link #refs}. We do
     * it here to avoid this ending up on the reference queue.
     */
    @Override
    public void clear() {
      clearInternal();
      // We run this here to periodically clean up the queue if any Batcher is being
      // closed properly.
      cleanQueue();
    }

    private void clearInternal() {
      super.clear();
      refs.remove(this);
      allocationSite.clear();
    }

    /**
     * It performs below tasks:
     *
     * <ul>
     *   <li>Check each batcher registered on refQueue while initialization.
     *   <li>Unregister them from refQueue.
     *   <li>If close() is not called on the batcher, then emits log with possible allocationSite.
     *   <li>Keeps track of number of batcher on which close() is not called.
     * </ul>
     */
    @VisibleForTesting
    static int cleanQueue() {
      BatcherReference ref;
      int orphanedBatchers = 0;
      while ((ref = (BatcherReference) refQueue.poll()) != null) {
        RuntimeException maybeAllocationSite = ref.allocationSite.get();
        ref.clearInternal(); // technically the reference is gone already.
        if (!ref.closed) {
          orphanedBatchers++;
          if (LOG.isLoggable(Level.SEVERE)) {
            String message = "Batcher was not closed properly!!! Make sure to call close().";
            LOG.log(Level.SEVERE, message, maybeAllocationSite);
          }
        }
      }
      return orphanedBatchers;
    }

    private static RuntimeException missingCallSite() {
      RuntimeException e =
          new RuntimeException(
              "Batcher allocation site not recorded.  Set -D"
                  + ALLOCATION_SITE_PROPERTY_NAME
                  + "=true to enable it");
      e.setStackTrace(new StackTraceElement[0]);
      return e;
    }
  }
}
