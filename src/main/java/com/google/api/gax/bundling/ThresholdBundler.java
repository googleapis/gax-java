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

import com.google.api.gax.core.FlowController.FlowControlException;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.joda.time.Duration;

/**
 * Queues up elements until either a duration of time has passed or any threshold in a given set of
 * thresholds is breached, and then delivers the elements in a bundle to the consumer.
 */
public final class ThresholdBundler<E> {

  private ImmutableList<BundlingThreshold<E>> thresholdPrototypes;
  private final Duration maxDelay;
  private final BundlingFlowController<E> flowController;
  private final BundleMerger<E> bundleMerger;

  private final Lock lock = new ReentrantLock();
  private final Condition bundleCondition = lock.newCondition();
  private BundleState currentBundleState;
  private Queue<E> closedBundles = new ArrayDeque<>();

  private ThresholdBundler(
      ImmutableList<BundlingThreshold<E>> thresholds,
      Duration maxDelay,
      BundlingFlowController<E> flowController,
      BundleMerger<E> bundleMerger) {
    this.thresholdPrototypes = copyResetThresholds(Preconditions.checkNotNull(thresholds));
    this.maxDelay = maxDelay;
    this.flowController = Preconditions.checkNotNull(flowController);
    this.bundleMerger = Preconditions.checkNotNull(bundleMerger);
    this.currentBundleState = null;
  }

  /** Builder for a ThresholdBundler. */
  public static final class Builder<E> {
    private List<BundlingThreshold<E>> thresholds;
    private Duration maxDelay;
    private BundlingFlowController<E> flowController;
    private BundleMerger<E> bundleMerger;

    private Builder() {
      thresholds = Lists.newArrayList();
    }

    /**
     * Set the max delay for a bundle. This is counted from the first item added to a bundle.
     */
    public Builder<E> setMaxDelay(Duration maxDelay) {
      this.maxDelay = maxDelay;
      return this;
    }

    /**
     * Set the thresholds for the ThresholdBundler.
     */
    public Builder<E> setThresholds(List<BundlingThreshold<E>> thresholds) {
      this.thresholds = thresholds;
      return this;
    }

    /**
     * Add a threshold to the ThresholdBundler.
     */
    public Builder<E> addThreshold(BundlingThreshold<E> threshold) {
      this.thresholds.add(threshold);
      return this;
    }

    /** Set the flow controller for the ThresholdBundler. */
    public Builder<E> setFlowController(BundlingFlowController<E> flowController) {
      this.flowController = flowController;
      return this;
    }

    public Builder<E> setBundleMerger(BundleMerger<E> bundleMerger) {
      this.bundleMerger = bundleMerger;
      return this;
    }

    /** Build the ThresholdBundler. */
    public ThresholdBundler<E> build() {
      return new ThresholdBundler<E>(
          ImmutableList.copyOf(thresholds), maxDelay, flowController, bundleMerger);
    }
  }

  /** Get a new builder for a ThresholdBundler. */
  public static <T> Builder<T> newBuilder() {
    return new Builder<T>();
  }

  /**
   * Adds an element to the bundler. If the element causes the collection to go past any of the
   * thresholds, the bundle will be made available to consumers.
   *
   * @throws FlowControlException
   */
  public void add(E e) throws FlowControlException {
    final Lock lock = this.lock;
    // We need to reserve resources from flowController outside the lock, so that they can be
    // released by removeBundle().
    flowController.reserve(e);
    lock.lock();
    try {
      boolean signalBundleIsReady = false;
      if (currentBundleState == null) {
        currentBundleState = new BundleState(thresholdPrototypes, maxDelay);
        currentBundleState.start();
        signalBundleIsReady = true;
      }

      currentBundleState.add(e);
      if (currentBundleState.isAnyThresholdReached()) {
        signalBundleIsReady = true;
        closedBundles.add(currentBundleState.getBundle());
        currentBundleState = null;
      }

      if (signalBundleIsReady) {
        bundleCondition.signalAll();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Makes the currently contained elements available for consumption, even if no thresholds were
   * triggered.
   */
  public void flush() {
    final Lock lock = this.lock;
    lock.lock();
    try {
      if (currentBundleState != null) {
        closedBundles.add(currentBundleState.getBundle());
        currentBundleState = null;
      }
      bundleCondition.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Remove and return the current bundle, regardless of whether it has triggered any thresholds.
   */
  public E removeBundle() {
    final Lock lock = this.lock;
    lock.lock();
    try {
      E outBundle = null;
      if (closedBundles.size() > 0) {
        outBundle = closedBundles.remove();
      } else if (currentBundleState != null) {
        outBundle = currentBundleState.getBundle();
        currentBundleState = null;
      }

      if (outBundle != null) {
        flowController.release(outBundle);
        return outBundle;
      } else {
        return null;
      }
    } finally {
      lock.unlock();
    }
  }

  /** Waits until a bundle is available, and returns it once it is. */
  public E takeBundle() throws InterruptedException {
    final Lock lock = this.lock;
    lock.lockInterruptibly();
    try {
      while (shouldWait()) {
        if (currentBundleState == null || maxDelay == null) {
          // if an element gets added, this will be signaled, then we will re-check the while-loop
          // condition to see if the delay or other thresholds have been exceeded,
          // and if none of these are true, then we will arrive at the time-bounded
          // await in the else clause.
          bundleCondition.await();
        } else {
          bundleCondition.await(
              currentBundleState.getDelayLeft().getMillis(), TimeUnit.MILLISECONDS);
        }
      }

      return removeBundle();
    } finally {
      lock.unlock();
    }
  }

  public boolean isEmpty() {
    final Lock lock = this.lock;
    lock.lock();
    try {
      if (closedBundles.size() > 0) {
        return false;
      }
      if (currentBundleState != null) {
        return false;
      }
      return true;
    } finally {
      lock.unlock();
    }
  }

  private boolean shouldWait() {
    if (closedBundles.size() > 0) {
      return false;
    }
    if (currentBundleState == null) {
      return true;
    }
    if (maxDelay == null) {
      return true;
    }
    return currentBundleState.getDelayLeft().getMillis() > 0;
  }

  private static <E> ImmutableList<BundlingThreshold<E>> copyResetThresholds(
      ImmutableList<BundlingThreshold<E>> thresholds) {
    ImmutableList.Builder<BundlingThreshold<E>> resetThresholds =
        ImmutableList.<BundlingThreshold<E>>builder();
    for (BundlingThreshold<E> threshold : thresholds) {
      resetThresholds.add(threshold.copyWithZeroedValue());
    }
    return resetThresholds.build();
  }

  private class BundleState {
    private final ImmutableList<BundlingThreshold<E>> thresholds;

    @SuppressWarnings("hiding")
    private final Duration maxDelay;

    private E bundle;
    private Stopwatch stopwatch;

    private BundleState(ImmutableList<BundlingThreshold<E>> thresholds, Duration maxDelay) {
      this.thresholds = copyResetThresholds(thresholds);
      this.maxDelay = maxDelay;
    }

    private void start() {
      stopwatch = Stopwatch.createStarted();
    }

    private void add(E newBundle) {
      if (bundle == null) {
        bundle = newBundle;
      } else {
        bundleMerger.merge(bundle, newBundle);
      }
      for (BundlingThreshold<E> threshold : thresholds) {
        threshold.accumulate(newBundle);
      }
    }

    private E getBundle() {
      return bundle;
    }

    private Duration getDelayLeft() {
      return Duration.millis(maxDelay.getMillis() - stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private boolean isAnyThresholdReached() {
      for (BundlingThreshold<E> threshold : thresholds) {
        if (threshold.isThresholdReached()) {
          return true;
        }
      }
      return false;
    }
  }
}
