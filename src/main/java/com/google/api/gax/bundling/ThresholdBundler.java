/*
 * Copyright 2015, Google Inc.
 * All rights reserved.
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

import com.google.api.client.util.Lists;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.joda.time.Duration;

/**
 * Queues up elements until either a duration of time has passed or any threshold in a given set of
 * thresholds is breached, and then delivers the elements in a bundle to the consumer.
 */
public class ThresholdBundler<E> {

  private ImmutableList<BundlingThreshold<E>> thresholds;
  private ImmutableList<ExternalThreshold<E>> externalThresholds;
  private final Duration maxDelay;

  private final Lock lock = new ReentrantLock();
  private final Condition bundleCondition = lock.newCondition();
  private boolean bundleReady = false;
  private BundleHandle currentBundleHandle;

  private Stopwatch bundleStopwatch;
  private final List<E> data = new ArrayList<>();

  private ThresholdBundler(ImmutableList<BundlingThreshold<E>> thresholds,
      ImmutableList<ExternalThreshold<E>> externalThresholds,
      Duration maxDelay) {
    this.thresholds = copyResetThresholds(Preconditions.checkNotNull(thresholds));
    this.externalThresholds = copyResetExternalThresholds(
        Preconditions.checkNotNull(externalThresholds));
    this.maxDelay = maxDelay;
    this.currentBundleHandle = new BundleHandle(externalThresholds);
  }

  /**
   * Builder for a ThresholdBundler.
   */
  public static class Builder<E> {
    private List<BundlingThreshold<E>> thresholds;
    private List<ExternalThreshold<E>> externalThresholds;
    private Duration maxDelay;

    private Builder() {
      thresholds = Lists.newArrayList();
      externalThresholds = Lists.newArrayList();
    }

    /**
     * Set the max delay for a bundle. This is counted from the first item
     * added to a bundle.
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

    /**
     * Set the external thresholds for the ThresholdBundler.
     */
    public Builder<E> setExternalThresholds(List<ExternalThreshold<E>> externalThresholds) {
      this.externalThresholds = externalThresholds;
      return this;
    }

    /**
     * Add an external threshold to the ThresholdBundler.
     */
    public Builder<E> addExternalThreshold(ExternalThreshold<E> externalThreshold) {
      this.externalThresholds.add(externalThreshold);
      return this;
    }

    /**
     * Build the ThresholdBundler.
     */
    public ThresholdBundler<E> build() {
      return new ThresholdBundler<E>(
          ImmutableList.copyOf(thresholds),
          ImmutableList.copyOf(externalThresholds),
          maxDelay);
    }
  }

  /**
   * Get a new builder for a ThresholdBundler.
   */
  public static <T> Builder<T> newBuilder() {
    return new Builder<T>();
  }

  /**
   * Adds an element to the bundler. If the element causes the collection to go past any of the
   * thresholds, the bundle will be made available to consumers.
   */
  public ThresholdBundleHandle add(E e) {
    final Lock lock = this.lock;
    lock.lock();
    try {
      boolean signal = false;
      // TODO verify invariant: bundleStopwatch == null iff size() == 0
      if (data.size() == 0) {
        bundleStopwatch = Stopwatch.createStarted();
        // we want to trigger the signal so that we switch the await from an unbounded
        // await to a time-bounded await.
        signal = true;
        for (ExternalThreshold<E> threshold : externalThresholds) {
          threshold.startBundle();
        }
      }
      data.add(e);
      if (!bundleReady) {
        for (BundlingThreshold<E> threshold : thresholds) {
          threshold.accumulate(e);
          if (threshold.isThresholdReached()) {
            bundleReady = true;
            signal = true;
            break;
          }
        }
      }
      if (signal) {
        bundleCondition.signalAll();
      }
      return currentBundleHandle;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Makes the currently contained elements available for consumption, even if no thresholds
   * were triggered.
   */
  public void flush() {
    final Lock lock = this.lock;
    lock.lock();
    try {
      bundleReady = true;
      bundleCondition.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Remove all currently contained elements, regardless of whether they have triggered any
   * thresholds. All elements are placed into 'bundle'.
   *
   * @return the number of items added to 'bundle'.
   */
  public int drainTo(Collection<? super E> bundle) {
    final Lock lock = this.lock;
    lock.lock();
    try {
      int dataSize = data.size();

      bundle.addAll(data);
      data.clear();
      currentBundleHandle = new BundleHandle(externalThresholds);

      thresholds = copyResetThresholds(thresholds);
      externalThresholds = copyResetExternalThresholds(externalThresholds);

      bundleStopwatch = null;
      bundleReady = false;

      return dataSize;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Waits until a bundle is available, and returns it once it is.
   */
  public List<E> takeBundle() throws InterruptedException {
    final Lock lock = this.lock;
    lock.lockInterruptibly();
    try {
      while (shouldWait()) {
        if (data.size() == 0 || maxDelay == null) {
          // if an element gets added, this will be signaled, then we will re-check the while-loop
          // condition to see if the delay or other thresholds have been exceeded,
          // and if none of these are true, then we will arrive at the time-bounded
          // await in the else clause.
          bundleCondition.await();
        } else {
          bundleCondition.await(getDelayLeft().getMillis(), TimeUnit.MILLISECONDS);
        }
      }
      List<E> bundle = new ArrayList<>();
      drainTo(bundle);
      return bundle;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns the number of elements queued up in the bundler.
   */
  public int size() {
    final Lock lock = this.lock;
    lock.lock();
    try {
      return data.size();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns the elements queued up in the bundler.
   */
  public Object[] toArray() {
    final Lock lock = this.lock;
    lock.lock();
    try {
      return data.toArray();
    } finally {
      lock.unlock();
    }
  }

  private boolean shouldWait() {
    if (data.size() == 0) {
      return true;
    }
    if (bundleReady) {
      return false;
    }
    if (maxDelay == null) {
      return true;
    }
    return getDelayLeft().getMillis() > 0;
  }

  // pre-condition: data.size() > 0 ( === bundleStopwatch != null)
  private Duration getDelayLeft() {
    return Duration.millis(maxDelay.getMillis() - bundleStopwatch.elapsed(TimeUnit.MILLISECONDS));
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

  private static <E> ImmutableList<ExternalThreshold<E>> copyResetExternalThresholds(
      ImmutableList<ExternalThreshold<E>> thresholds) {
    ImmutableList.Builder<ExternalThreshold<E>> resetThresholds =
        ImmutableList.<ExternalThreshold<E>>builder();
    for (ExternalThreshold<E> threshold : thresholds) {
      resetThresholds.add(threshold.copyReset());
    }
    return resetThresholds.build();
  }

  /**
   * This class represents a handle to a bundle that is being built up inside
   * a ThresholdBundler. It can be used to perform certain operations on
   * a ThresholdBundler, but only if the bundle referenced is still the active
   * one.
   */
  private class BundleHandle implements ThresholdBundleHandle {
    private final ImmutableList<ExternalThreshold<E>> externalThresholds;

    private BundleHandle(ImmutableList<ExternalThreshold<E>> externalThresholds) {
      this.externalThresholds = externalThresholds;
    }

    @Override
    public void externalThresholdEvent(Object event) {
      final Lock lock = ThresholdBundler.this.lock;
      lock.lock();

      try {
        for (ExternalThreshold<E> threshold : externalThresholds) {
          threshold.handleEvent(this, event);
        }
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void flush() {
      final Lock lock = ThresholdBundler.this.lock;
      lock.lock();

      try {
        if (ThresholdBundler.this.currentBundleHandle != this) {
          return;
        }
        ThresholdBundler.this.flush();
      } finally {
        lock.unlock();
      }
    }
  }
}
