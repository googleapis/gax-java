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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.joda.time.Duration;

/** Like ThresholdBundler, but pushes instead of pulls. */
final class PushingBundler<E> {

  private final Duration maxDelay;
  private final ScheduledExecutorService executor;

  // Invariant:
  // - lock gates all accesses to members below
  // - currentOpenBundle and currentAlarmFuture are either both null or both non-null
  // TODO(pongad): Make ThresholdBundleReceiver thread-safe. Then move it out of the lock.
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

  void flush() {
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

  void add(E e) {
    lock.lock();
    try {
      receiver.validateItem(e);
      if (currentOpenBundle == null) {
        currentOpenBundle = new LinkedList<E>();
        currentAlarmFuture =
            executor.schedule(
                new Runnable() {
                  @Override
                  public void run() {
                    flush();
                  }
                },
                maxDelay.getMillis(),
                TimeUnit.MILLISECONDS);
      }

      currentOpenBundle.add(e);
      for (BundlingThreshold threshold : thresholds) {
        threshold.accumulate(e);
        if (threshold.isThresholdReached()) {
          flush();
          return;
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void resetThresholds() {
    for (int i = 0; i < thresholds.size(); i++) {
      thresholds.set(i, thresholds.get(i).copyWithZeroedValue());
    }
  }
}
