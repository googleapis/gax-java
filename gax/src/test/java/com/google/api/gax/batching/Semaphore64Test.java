/*
 * Copyright 2017 Google LLC
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

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Semaphore64Test {
  @Test(expected = IllegalArgumentException.class)
  public void testNegative() {
    Semaphore64 semaphore = new BlockingSemaphore(1);
    semaphore.acquire(-1);
  }

  @Test
  public void testReturning() {
    Semaphore64 semaphore = new NonBlockingSemaphore(1);
    assertThat(semaphore.acquire(1)).isTrue();
    assertThat(semaphore.acquire(1)).isFalse();
    semaphore.release(1);
    assertThat(semaphore.acquire(1)).isTrue();
  }

  @Test
  public void testBlocking() throws InterruptedException {
    final Semaphore64 semaphore = new BlockingSemaphore(1);
    semaphore.acquire(1);

    Runnable acquireOneRunnable =
        new Runnable() {
          @Override
          public void run() {
            semaphore.acquire(1);
          }
        };

    List<Thread> acquirers = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Thread t = new Thread(acquireOneRunnable);
      acquirers.add(t);
      t.start();
    }

    Thread.sleep(500);

    for (Thread t : acquirers) {
      assertThat(t.isAlive()).isTrue();
    }

    semaphore.release(3);
    semaphore.release(3);

    for (Thread t : acquirers) {
      t.join(500);
      assertThat(t.isAlive()).isFalse();
    }
  }
}
