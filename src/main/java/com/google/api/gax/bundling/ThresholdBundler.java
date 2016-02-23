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

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Queues up elements until either a duration of time has passed or any threshold in a given set of
 * thresholds is breached, and then delivers the elements in a bundle to the consumer.
 */
public class ThresholdBundler<E> {

  private final ImmutableList<BundlingThreshold<E>> thresholds;
  private final Duration maxDelay;

  private final Lock lock = new ReentrantLock();
  private final Condition bundleCondition = lock.newCondition();
  private boolean bundleReady = false;

  private Stopwatch bundleStopwatch;
  private final List<E> data = new ArrayList<>();

  public ThresholdBundler(ImmutableList<BundlingThreshold<E>> thresholds) {
    this(null, thresholds);
  }

  public ThresholdBundler(Duration maxDelay) {
    this(maxDelay, ImmutableList.<BundlingThreshold<E>>of());
  }

  public ThresholdBundler(Duration maxDelay, ImmutableList<BundlingThreshold<E>> thresholds) {
    this.thresholds = Preconditions.checkNotNull(thresholds);
    this.maxDelay = maxDelay;
  }

  /**
   * Adds an element to the bundler. If the element causes the collection to go past any of the
   * thresholds, the bundle will be made available to consumers.
   */
  public void add(E e) {
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

      for (BundlingThreshold<E> threshold : thresholds) {
        threshold.reset();
      }

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
}
