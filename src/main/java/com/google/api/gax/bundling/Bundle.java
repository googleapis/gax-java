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

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.TimeUnit;
import org.joda.time.Duration;

public abstract class Bundle<E> {
  private final ImmutableList<BundlingThreshold<E>> thresholds;

  @SuppressWarnings("hiding")
  private final Duration maxDelay;

  private Stopwatch stopwatch;

  private Bundle(ImmutableList<BundlingThreshold<E>> thresholds, Duration maxDelay) {
    this.thresholds = copyResetThresholds(thresholds);
    this.maxDelay = maxDelay;
  }

  public void start() {
    stopwatch = Stopwatch.createStarted();
  }

  public void add(E e) {
    accumulateItem(e);
    for (BundlingThreshold<E> threshold : thresholds) {
      threshold.accumulate(e);
    }
  }

  public E close() {
    return constructBundledItem();
  }

  protected abstract void accumulateItem(E e);
  
  protected abstract E constructBundledItem();
  
  public Duration getDelayLeft() {
    return Duration.millis(maxDelay.getMillis() - stopwatch.elapsed(TimeUnit.MILLISECONDS));
  }

  public boolean isAnyThresholdReached() {
    for (BundlingThreshold<E> threshold : thresholds) {
      if (threshold.isThresholdReached()) {
        return true;
      }
    }
    return false;
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
}
