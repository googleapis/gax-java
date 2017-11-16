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
package com.google.api.gax.rpc;

import com.google.api.core.SettableApiFuture;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ServerStreamTest {
  private TestStreamController controller;
  private ServerStream<Integer> stream;
  private ExecutorService executor;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    controller = new TestStreamController();
    stream = new ServerStream<>();

    stream.observer().onStart(controller);
    executor = Executors.newSingleThreadExecutor();
  }

  @Test
  public void testEmptyStream() {
    stream.observer().onStart(controller);
    stream.observer().onComplete();

    Assert.assertTrue("Stream should be empty", Lists.newArrayList(stream).isEmpty());
  }

  @Test
  public void testMultipleItemStream() throws InterruptedException {
    executor.submit(
        new Callable<Void>() {
          @Override
          public Void call() throws InterruptedException {
            for (int i = 0; i < 5; i++) {

              int requestCount = controller.requests.take();
              Assert.assertEquals("Stream should request one item at a time", 1, requestCount);
              stream.observer().onResponse(i);
            }
            stream.observer().onComplete();
            return null;
          }
        });

    Assert.assertEquals(Lists.newArrayList(stream), Lists.newArrayList(0, 1, 2, 3, 4));
  }

  @Test
  public void testEarlyTermination() throws Exception {
    executor.submit(
        new Callable<Void>() {
          @Override
          public Void call() throws InterruptedException, ExecutionException, TimeoutException {
            for (int i = 0; i < 2; i++) {
              controller.requests.take();
              stream.observer().onResponse(i);
            }
            Throwable cancelException = controller.cancelFuture.get(1, TimeUnit.SECONDS);
            stream.observer().onError(cancelException);
            return null;
          }
        });

    int numRead = 0;
    for (Integer integer : stream) {
      numRead++;
      if (integer == 1) {
        stream.cancel();
      }
    }

    Assert.assertEquals("Stream should've closed without error 2 items", 2, numRead);
  }

  @Test
  public void testErrorPropagation() {
    ClassCastException e = new ClassCastException("fake error");

    stream.observer().onError(e);
    expectedException.expectMessage(e.getMessage());
    expectedException.expect(ClassCastException.class);

    for (Integer integer : stream) {
      Assert.fail("Error should've propagated before iteration can begin");
    }
  }

  @Test
  public void testNoErrorsBetweenHasNextAndNext() throws InterruptedException {
    Iterator<Integer> it = stream.iterator();

    controller.requests.take();
    stream.observer().onResponse(1);

    Assert.assertTrue(it.hasNext());
    RuntimeException fakeError = new RuntimeException("fake");
    stream.observer().onError(fakeError);
    Assert.assertEquals((int) it.next(), 1);

    // Now the error should be thrown
    try {
      it.next();
      Assert.fail("Error was never thrown");
    } catch (RuntimeException e) {
      Assert.assertEquals(e, fakeError);
    }
  }

  @Test
  public void testReady() throws InterruptedException {
    Iterator<Integer> it = stream.iterator();
    Assert.assertFalse("Initially the stream is not ready", stream.isReady());

    controller.requests.take();
    stream.observer().onResponse(1);

    Assert.assertTrue("After receiving a response, it should be ready", stream.isReady());
    it.next();
    Assert.assertFalse("After consuming the buffer, it's no longer ready", stream.isReady());
  }

  private static class TestStreamController extends StreamController {
    SettableApiFuture<Throwable> cancelFuture = SettableApiFuture.create();
    BlockingQueue<Integer> requests = Queues.newLinkedBlockingDeque();
    boolean autoFlowControl = true;

    @Override
    public void cancel(Throwable cause) {
      cancelFuture.set(cause);
    }

    @Override
    public void disableAutoInboundFlowControl() {
      autoFlowControl = false;
    }

    @Override
    public void request(int count) {
      requests.add(count);
    }
  }
}
