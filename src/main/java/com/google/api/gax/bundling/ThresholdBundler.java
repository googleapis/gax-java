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
package com.google.api.gax.bundling;

import com.google.api.gax.core.ApiFuture;
import com.google.api.gax.core.ApiFutures;
import com.google.api.gax.core.FlowController.FlowControlException;
import com.google.api.gax.core.Function;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.joda.time.Duration;

/**
 * Queues up elements until either a duration of time has passed or any threshold in a given set of
 * thresholds is breached, and then delivers the elements in a bundle to the consumer.
 */
public final class ThresholdBundler<E> {

  private class ReleaseResourcesFunction<T> implements Function<T, Void> {
    private final E bundle;

    private ReleaseResourcesFunction(E bundle) {
      this.bundle = bundle;
    }

    @Override
    public Void apply(T input) {
      flowController.release(bundle);
      return null;
    }
  }

  private final Runnable pushCurrentBundleRunnable =
      new Runnable() {
        @Override
        public void run() {
          pushCurrentBundle();
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

  private ThresholdBundler(Builder<E> builder) {
    this.thresholds = new ArrayList<>(builder.thresholds);
    this.executor = Preconditions.checkNotNull(builder.executor);
    this.maxDelay = Preconditions.checkNotNull(builder.maxDelay);
    this.receiver = Preconditions.checkNotNull(builder.receiver);
    this.flowController = Preconditions.checkNotNull(builder.flowController);
    this.bundleMerger = Preconditions.checkNotNull(builder.bundleMerger);

    resetThresholds();
  }

  /** Builder for a ThresholdBundler. */
  public static class Builder<E> {
    private Collection<BundlingThreshold<E>> thresholds;
    private ScheduledExecutorService executor;
    private Duration maxDelay;
    private ThresholdBundleReceiver<E> receiver;
    private BundlingFlowController<E> flowController;
    private BundleMerger<E> bundleMerger;

    private Builder() {}

    /** Set the executor for the ThresholdBundler. */
    public Builder<E> setExecutor(ScheduledExecutorService executor) {
      this.executor = executor;
      return this;
    }

    /** Set the max delay for a bundle. This is counted from the first item added to a bundle. */
    public Builder<E> setMaxDelay(Duration maxDelay) {
      this.maxDelay = maxDelay;
      return this;
    }

    /** Set the thresholds for the ThresholdBundler. */
    public Builder<E> setThresholds(Collection<BundlingThreshold<E>> thresholds) {
      this.thresholds = thresholds;
      return this;
    }

    /** Set the threshold bundle receiver for the ThresholdBundler. */
    public Builder<E> setReceiver(ThresholdBundleReceiver<E> receiver) {
      this.receiver = receiver;
      return this;
    }

    /** Set the flow controller for the ThresholdBundler. */
    public Builder<E> setFlowController(BundlingFlowController<E> flowController) {
      this.flowController = flowController;
      return this;
    }

    /** Set the bundle merger for the ThresholdBundler. */
    public Builder<E> setBundleMerger(BundleMerger<E> bundleMerger) {
      this.bundleMerger = bundleMerger;
      return this;
    }

    /** Build the ThresholdBundler. */
    public ThresholdBundler<E> build() {
      return new ThresholdBundler<>(this);
    }
  }

  /** Get a new builder for a ThresholdBundler. */
  public static <E> Builder<E> newBuilder() {
    return new Builder<>();
  }

  /**
   * Adds an element to the bundler. If the element causes the collection to go past any of the
   * thresholds, the bundle will be sent to the {@code ThresholdBundleReceiver}.
   *
   * @throws FlowControlException
   */
  public void add(E e) throws FlowControlException {
    // We need to reserve resources from flowController outside the lock, so that they can be
    // released by pushCurrentBundle().
    flowController.reserve(e);
    lock.lock();
    try {
      receiver.validateBundle(e);
      boolean anyThresholdReached = isAnyThresholdReached(e);

      if (currentOpenBundle == null) {
        currentOpenBundle = e;
        // Schedule a job only when no thresholds have been exceeded, otherwise it will be
        // immediately cancelled
        if (!anyThresholdReached) {
          currentAlarmFuture =
              executor.schedule(
                  pushCurrentBundleRunnable, maxDelay.getMillis(), TimeUnit.MILLISECONDS);
        }
      } else {
        bundleMerger.merge(currentOpenBundle, e);
      }

      if (anyThresholdReached) {
        pushCurrentBundle();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * * Package-private for use in testing.
   */
  @VisibleForTesting
  boolean isEmpty() {
    lock.lock();
    try {
      return currentOpenBundle == null;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Push the current bundle to the bundle receiver. Returns an ApiFuture that completes once the
   * bundle has been processed by the bundle receiver and the flow controller resources have been
   * released.
   *
   * Note that this future can complete for the current bundle before previous bundles have
   * completed, so it cannot be depended upon for flushing.
   */
  @VisibleForTesting
  public ApiFuture<Void> pushCurrentBundle() {
    final E bundle = removeBundle();
    if (bundle == null) {
      return ApiFutures.immediateFuture(null);
    } else {
      return ApiFutures.transform(
          receiver.processBundle(bundle), new ReleaseResourcesFunction<>(bundle));
    }
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
