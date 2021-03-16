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

/** A {@link Semaphore64} that blocks until permits become available. */
class BlockingSemaphore implements Semaphore64 {
  private long currentPermits;
  private long limit;

  private static void checkNotNegative(long l) {
    Preconditions.checkArgument(l >= 0, "negative permits not allowed: %s", l);
  }

  BlockingSemaphore(long permits) {
    checkNotNegative(permits);
    this.currentPermits = permits;
    this.limit = permits;
  }

  @Override
  public synchronized void release(long permits) {
    checkNotNegative(permits);

    currentPermits += permits;
    // If more permits are returned than what was originally set, we need to add these extra
    // permits to the limit too
    if (currentPermits > limit) {
      limit = currentPermits;
    }
    notifyAll();
  }

  @Override
  public synchronized boolean acquire(long permits) {
    checkNotNegative(permits);

    boolean interrupted = false;
    while (currentPermits < permits) {
      try {
        wait();
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    currentPermits -= permits;

    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    return true;
  }

  @Override
  public synchronized boolean acquirePartial(long permits) {
    checkNotNegative(permits);

    boolean interrupted = false;
    // Give out permits as long as currentPermits is greater or equal to max of (limit, permits).
    // currentPermits could be negative after the permits are given out, which marks how many
    // permits are owed.
    while (currentPermits < Math.min(limit, permits)) {
      try {
        wait();
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    currentPermits -= permits;

    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    return true;
  }

  @Override
  public synchronized void reducePermits(long reduction) {
    checkNotNegative(reduction);
    checkNotNegative(limit - reduction);
    currentPermits -= reduction;
    limit -= reduction;
  }

  @Override
  public synchronized long getLimit() {
    return limit;
  }
}
