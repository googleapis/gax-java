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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
          flush();
        }
      };

  private final Duration maxDelay;
  private final ScheduledExecutorService executor;

  // Invariant:
  // - lock gates all accesses to members below
  // - currentOpenBundle and currentAlarmFuture are either both null or both non-null
  // TODO(pongad): Require that ThresholdBundleReceiver be thread-safe. Then move it out of the lock.
  private final ReentrantLock lock = new ReentrantLock();
  private LinkedList<E> currentOpenBundle;
  private ScheduledFuture<?> currentAlarmFuture;
  private final ArrayList<BundlingThreshold<E>> thresholds;
  private final ThresholdBundleReceiver<E> receiver;

  private PushingBundler(
      Collection<BundlingThreshold<E>> thresholdPrototypes,
      ScheduledExecutorService executor,
      Duration maxDelay,
      ThresholdBundleReceiver<E> receiver) {
    this.maxDelay = Preconditions.checkNotNull(maxDelay);
    this.receiver = Preconditions.checkNotNull(receiver);
    this.executor = Preconditions.checkNotNull(executor);

    this.thresholds = new ArrayList<BundlingThreshold<E>>(thresholdPrototypes);
    resetThresholds();
  }

  /**
   * Immediately make contained elements available to the {@code ThresholdBundleReceiver}.
   */
  public void flush() {
    lock.lock();
    try {
      LinkedList<E> bundle = currentOpenBundle;
      currentOpenBundle = null;
      if (currentAlarmFuture != null) {
        currentAlarmFuture.cancel(false);
        currentAlarmFuture = null;
      }
      resetThresholds();

      if (bundle != null && !bundle.isEmpty()) {
        receiver.processBundle(bundle);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Adds an element to the bundler. If the element causes the collection to go past any of the
   * thresholds, the bundle will be made available to the {@code ThresholdBundleReceiver}.
   */
  public void add(E e) {
    lock.lock();
    try {
      receiver.validateItem(e);
      if (currentOpenBundle == null) {
        currentOpenBundle = new LinkedList<E>();
        currentAlarmFuture =
            executor.schedule(flushRunnable, maxDelay.getMillis(), TimeUnit.MILLISECONDS);
      }

      currentOpenBundle.add(e);
      if (doesReachThreshold(e)) {
        flush();
      }
    } finally {
      lock.unlock();
    }
  }

  private boolean doesReachThreshold(E e) {
    for (BundlingThreshold threshold : thresholds) {
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
