/*
 * Copyright 2017, Google LLC All rights reserved.
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

/**
 * Semaphore64 is similar to {@link java.util.concurrent.Semaphore} but allows up to {@code 2^63-1}
 * permits.
 *
 * <p>Users who do not need such large number of permits are strongly encouraged to use Java's
 * {@code Semaphore} instead. It is almost certainly faster and less error prone.
 */
final class Semaphore64 {

  private long currentPermits;

  Semaphore64(long permits) {
    notNegative(permits);
    this.currentPermits = permits;
  }

  synchronized void release(long permits) {
    notNegative(permits);

    currentPermits += permits;
    notifyAll();
  }

  synchronized void acquireUninterruptibly(long permits) {
    notNegative(permits);

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
  }

  synchronized boolean tryAcquire(long permits) {
    notNegative(permits);

    if (currentPermits >= permits) {
      currentPermits -= permits;
      return true;
    }
    return false;
  }

  private static void notNegative(long l) {
    Preconditions.checkArgument(l >= 0, "negative permits not allowed: %s", l);
  }
}
