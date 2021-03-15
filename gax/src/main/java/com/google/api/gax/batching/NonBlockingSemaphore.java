/*
 * Copyright 2018 Google LLC
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

import com.google.common.base.Preconditions;
import java.util.concurrent.atomic.AtomicLong;

/** A {@link Semaphore64} that immediately returns with failure if permits are not available. */
class NonBlockingSemaphore implements Semaphore64 {
  private AtomicLong currentPermits;
  private AtomicLong limit;

  private static void checkNotNegative(long l) {
    Preconditions.checkArgument(l >= 0, "negative permits not allowed: %s", l);
  }

  NonBlockingSemaphore(long permits) {
    checkNotNegative(permits);
    this.currentPermits = new AtomicLong(permits);
    this.limit = new AtomicLong(permits);
  }

  @Override
  public void release(long permits) {
    checkNotNegative(permits);

    long diff = permits + currentPermits.get() - limit.get();
    currentPermits.addAndGet(permits);
    // If more permits are returned than what was originally set, we need to add these extra
    // permits to the limit
    if (diff > 0) {
      limit.addAndGet(diff);
    }
  }

  @Override
  public boolean acquire(long permits) {
    checkNotNegative(permits);
    while (true) {
      long old = currentPermits.get();
      if (old < permits) {
        return false;
      }
      if (currentPermits.compareAndSet(old, old - permits)) {
        return true;
      }
    }
  }

  @Override
  public boolean acquirePartial(long permits) {
    checkNotNegative(permits);
    // Give out permits as long as currentPermits is greater or equal to max of (limit, permits).
    // currentPermits could be negative after the permits are given out, which marks how many
    // permits are owed.
    while (true) {
      long old = currentPermits.get();
      if (old < Math.min(limit.get(), permits)) {
        return false;
      }
      if (currentPermits.compareAndSet(old, old - permits)) {
        return true;
      }
    }
  }

  @Override
  public void reducePermits(long reduction) {
    checkNotNegative(reduction);

    while (true) {
      long oldLimit = limit.get();
      checkNotNegative(oldLimit - reduction);
      if (limit.compareAndSet(oldLimit, oldLimit - reduction)) {
        currentPermits.addAndGet(-reduction);
        return;
      }
    }
  }
}
