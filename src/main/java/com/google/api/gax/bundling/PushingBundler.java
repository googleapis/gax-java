/*
 * Copyright 2017, Google Inc. All rights reserved.
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
package com.google.api.gax.bundling;

import com.google.api.gax.core.ApiFuture;
import com.google.api.gax.core.ApiFutures;
import com.google.api.gax.core.FlowController.FlowControlException;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.joda.time.Duration;

/**
 * Queues up elements until either a duration of time has passed or any threshold in a given set of
 * thresholds is breached, and then delivers the elements in a bundle to the consumer.
 */
public final class PushingBundler<E> {

  private final Runnable flushRunnable =
      new Runnable() {
        @Override
        public void run() {
          process(removeBundle());
        }
      };

  private final ArrayList<BundlingThreshold<E>> thresholds;
  private final ScheduledExecutorService executor;
  private final Duration maxDelay;
  private final ThresholdBundleReceiver<E> receiver;
  private final BundlingFlowController<E> flowController;
  private final BundleMerger<E> bundleMerger;

  // Invariant:
  // - lock gates all accesses to members below
  // - currentOpenBundle and currentAlarmFuture are either both null or both non-null
  private final ReentrantLock lock = new ReentrantLock();
  private E currentOpenBundle;
  private Future<?> currentAlarmFuture;

  private PushingBundler(Builder<E> builder) {
    this.thresholds = new ArrayList<>(builder.thresholds);
    this.executor = Preconditions.checkNotNull(builder.executor);
    this.maxDelay = Preconditions.checkNotNull(builder.maxDelay);
    this.receiver = Preconditions.checkNotNull(builder.receiver);
    this.flowController = Preconditions.checkNotNull(builder.flowController);
    this.bundleMerger = Preconditions.checkNotNull(builder.bundleMerger);

    resetThresholds();
  }

  public static class Builder<E> {
    private Collection<BundlingThreshold<E>> thresholds;
    private ScheduledExecutorService executor;
    private Duration maxDelay;
    private ThresholdBundleReceiver<E> receiver;
    private BundlingFlowController<E> flowController;
    private BundleMerger<E> bundleMerger;

    private Builder() {}

    public Builder<E> setThresholds(Collection<BundlingThreshold<E>> thresholds) {
      this.thresholds = thresholds;
      return this;
    }

    public Builder<E> setExecutor(ScheduledExecutorService executor) {
      this.executor = executor;
      return this;
    }

    public Builder<E> setMaxDelay(Duration maxDelay) {
      this.maxDelay = maxDelay;
      return this;
    }

    public Builder<E> setReceiver(ThresholdBundleReceiver<E> receiver) {
      this.receiver = receiver;
      return this;
    }

    public Builder<E> setFlowController(BundlingFlowController<E> flowController) {
      this.flowController = flowController;
      return this;
    }

    public Builder<E> setBundleMerger(BundleMerger<E> bundleMerger) {
      this.bundleMerger = bundleMerger;
      return this;
    }

    public PushingBundler<E> build() {
      return new PushingBundler<>(this);
    }
  }

  public static <E> Builder<E> newBuilder() {
    return new Builder<>();
  }

  /**
   * Immediately send contained elements to the {@code ThresholdBundleReceiver} and wait for them to
   * be processed.
   */
  public void flush() {
    try {
      process(removeBundle()).get();
    } catch (InterruptedException | ExecutionException e) {
    }
  }

  /**
   * Adds an element to the bundler. If the element causes the collection to go past any of the
   * thresholds, the bundle will be sent to the {@code ThresholdBundleReceiver}.
   */
  public void add(E e) throws FlowControlException {
    // We need to reserve resources from flowController outside the lock, so that they can be
    // released by process().
    flowController.reserve(e);
    lock.lock();
    try {
      receiver.validateBundle(e);
      if (currentOpenBundle == null) {
        currentOpenBundle = e;
        currentAlarmFuture =
            executor.schedule(flushRunnable, maxDelay.getMillis(), TimeUnit.MILLISECONDS);
      } else {
        bundleMerger.merge(currentOpenBundle, e);
      }

      if (isAnyThresholdReached(e)) {
        process(removeBundle());
      }
    } finally {
      lock.unlock();
    }
  }

  public boolean isEmpty() {
    lock.lock();
    try {
      return currentOpenBundle == null;
    } finally {
      lock.unlock();
    }
  }

  private ApiFuture<?> process(final E bundle) {
    if (bundle == null) {
      return ApiFutures.immediateFuture(null);
    }
    ApiFuture<?> future = receiver.processBundle(bundle);
    future.addListener(
        new Runnable() {
          @Override
          public void run() {
            flowController.release(bundle);
          }
        },
        executor);
    return future;
  }

  private E removeBundle() {
    lock.lock();
    try {
      E bundle = currentOpenBundle;
      currentOpenBundle = null;
      if (currentAlarmFuture != null) {
        currentAlarmFuture.cancel(false);
        currentAlarmFuture = null;
      }
      resetThresholds();
      return bundle;
    } finally {
      lock.unlock();
    }
  }

  private boolean isAnyThresholdReached(E e) {
    for (BundlingThreshold<E> threshold : thresholds) {
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
