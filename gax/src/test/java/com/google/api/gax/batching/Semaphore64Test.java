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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedList;
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
    assertTrue(semaphore.acquire(1));
    assertFalse(semaphore.acquire(1));
    semaphore.release(1);
    assertTrue(semaphore.acquire(1));
  }

  @Test(timeout = 500)
  public void testBlocking() throws InterruptedException {
    final Semaphore64 semaphore = new BlockingSemaphore(1);
    semaphore.acquire(1);

    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                semaphore.acquire(1);
              }
            });
    t.start();

    Thread.sleep(50);
    assertTrue(t.isAlive());

    semaphore.release(1);
    t.join();
  }

  @Test
  public void testReducePermitLimitNonBlocking() {
    final Semaphore64 semaphore = new NonBlockingSemaphore(5);
    semaphore.reducePermitLimit(3);
    assertFalse(semaphore.acquire(3));
    assertTrue(semaphore.acquire(2));
    assertEquals(2, semaphore.getPermitLimit());
  }

  @Test(timeout = 500)
  public void testReducePermitLimitBlocking() throws InterruptedException {
    final Semaphore64 semaphore = new BlockingSemaphore(2);

    semaphore.reducePermitLimit(1);
    semaphore.acquire(1);

    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                semaphore.acquire(1);
              }
            });
    t.start();

    Thread.sleep(50);
    assertTrue(t.isAlive());

    semaphore.release(1);
    t.join();

    assertEquals(1, semaphore.getPermitLimit());
  }

  @Test
  public void testAcquirePartialNonBlocking() {
    Semaphore64 semaphore = new NonBlockingSemaphore(5);
    assertTrue(semaphore.acquirePartial(6));
    assertFalse(semaphore.acquire(1));
    semaphore.release(6);
    assertTrue(semaphore.acquire(1));
    assertFalse(semaphore.acquirePartial(6));
    // limit should still be 5
    assertEquals(5, semaphore.getPermitLimit());
  }

  @Test(timeout = 500)
  public void testAcquirePartialBlocking() throws Exception {
    final Semaphore64 semaphore = new BlockingSemaphore(5);
    semaphore.acquirePartial(6);
    Thread t1 =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                semaphore.acquire(1);
              }
            });
    t1.start();
    // wait for thread to start
    Thread.sleep(100);
    assertTrue(t1.isAlive());
    semaphore.release(6);
    t1.join();

    // now there should be 4 permits available, acquiring 6 should block
    Thread t2 =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                semaphore.acquirePartial(6);
              }
            });
    t2.start();
    // wait fo thread to start
    Thread.sleep(100);
    assertTrue(t2.isAlive());
    // limit should still be 5 and get limit should not block
    assertEquals(5, semaphore.getPermitLimit());
  }

  @Test
  public void testIncreasePermitLimitNonBlocking() {
    Semaphore64 semaphore = new NonBlockingSemaphore(1);
    assertFalse(semaphore.acquire(2));
    semaphore.increasePermitLimit(1);
    assertTrue(semaphore.acquire(2));
    semaphore.release(2);
    assertFalse(semaphore.acquire(3));
    assertEquals(2, semaphore.getPermitLimit());
  }

  @Test(timeout = 500)
  public void testIncreasePermitLimitBlocking() throws Exception {
    final Semaphore64 semaphore = new BlockingSemaphore(1);
    semaphore.acquire(1);
    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                semaphore.acquire(1);
              }
            });
    t.start();

    Thread.sleep(50);
    assertTrue(t.isAlive());

    semaphore.increasePermitLimit(1);
    t.join();
    semaphore.release(2);

    semaphore.acquire(2);
    assertEquals(2, semaphore.getPermitLimit());
  }

  @Test(timeout = 500)
  public void testReleaseWontOverflowNonBlocking() throws Exception {
    final Semaphore64 semaphore = new NonBlockingSemaphore(10);
    List<Thread> threads = new LinkedList<>();
    for (int i = 0; i < 20; i++) {
      final int id = i;
      Thread t =
          new Thread(
              new Runnable() {
                @Override
                public void run() {
                  semaphore.acquire(5);
                  semaphore.release(6);
                }
              });
      threads.add(t);
    }
    for (Thread t : threads) {
      t.start();
    }
    for (Thread t : threads) {
      t.join();
    }
    // Limit should still be 10 and only 10 permits should be available
    assertEquals(10, semaphore.getPermitLimit());
    assertFalse(semaphore.acquire(11));
    assertTrue(semaphore.acquire(10));
  }

  @Test(timeout = 500)
  public void testReleaseWontOverflowBlocking() throws Exception {
    final Semaphore64 semaphore = new BlockingSemaphore(10);
    semaphore.acquire(5);
    semaphore.release(6);
    // Limit should still be 10 and only 10 permits should be available
    assertEquals(10, semaphore.getPermitLimit());
    semaphore.acquire(10);
    semaphore.release(10);
    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                semaphore.acquire(11);
              }
            });
    t.start();
    Thread.sleep(100);
    assertTrue(t.isAlive());
  }

  @Test
  public void testPermitLimitUnderflowNonBlocking() {
    Semaphore64 semaphore = new NonBlockingSemaphore(10);
    try {
      semaphore.reducePermitLimit(10);
      fail("Did not throw illegal state exception");
    } catch (IllegalStateException e) {
    }
    assertEquals(10, semaphore.getPermitLimit());
    assertFalse(semaphore.acquire(11));
    assertTrue(semaphore.acquire(10));
  }

  @Test(timeout = 500)
  public void testPermitLimitUnderflowBlocking() throws Exception {
    final Semaphore64 semaphore = new BlockingSemaphore(10);
    try {
      semaphore.reducePermitLimit(10);
      fail("Did not throw illegal state exception");
    } catch (IllegalStateException e) {
    }
    assertEquals(10, semaphore.getPermitLimit());
    semaphore.acquire(10);
    semaphore.release(10);
    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                semaphore.acquire(11);
              }
            });
    t.start();
    Thread.sleep(100);
    assertTrue(t.isAlive());
  }
}
