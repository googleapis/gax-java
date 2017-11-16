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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.collect.Queues;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StreamMediatorTest {

  @Test
  public void testOneToOne() {
    FakePipeline pipeline = new FakePipeline(false, 1);

    // simple path: downstream requests 1 response, the request is proxied to upstream & upstream delivers.
    pipeline.downstreamObserver.controller.request(1);
    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("a");
    Assert.assertArrayEquals(pipeline.downstreamObserver.responses.toArray(), new String[]{"a"});

    pipeline.mediator.onComplete();
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isComplete);
  }

  @Test
  public void testOneToOneAuto() {
    FakePipeline pipeline = new FakePipeline(true, 1);

    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("a");
    assertEquals("a", pipeline.downstreamObserver.responses.poll());

    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("b");
    assertEquals("b", pipeline.downstreamObserver.responses.poll());

    pipeline.mediator.onComplete();
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isComplete);
  }

  @Test
  public void testManyToOne() {
    FakePipeline pipeline = new FakePipeline(false, 1);

    // First downstream request makes the upstream over produce
    pipeline.downstreamObserver.controller.request(1);
    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("a-b");
    assertEquals(pipeline.downstreamObserver.responses.poll(), "a");
    assertTrue("Over produced responses should not be delivered unsolicited",
        pipeline.downstreamObserver.responses.isEmpty());

    // Next downstream request should fetch from buffer
    pipeline.downstreamObserver.controller.request(1);
    assertTrue(pipeline.upstreamController.requests.isEmpty());
    assertEquals("b", pipeline.downstreamObserver.responses.poll());

    // Make sure completion is delivered
    pipeline.mediator.onComplete();
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isComplete);
  }

  @Test
  public void testManyToOneAuto() {
    FakePipeline pipeline = new FakePipeline(true, 1);

    // First downstream request makes the upstream over produce
    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("a-b");
    pipeline.mediator.onComplete();

    assertEquals("a", pipeline.downstreamObserver.responses.poll());
    assertEquals("b", pipeline.downstreamObserver.responses.poll());
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isComplete);
  }

  @Test
  public void testManyToOneCompleteIsQueued() {
    FakePipeline pipeline = new FakePipeline(false, 1);

    pipeline.downstreamObserver.controller.request(1);
    pipeline.mediator.onResponse("a-b");
    pipeline.mediator.onComplete();

    pipeline.downstreamObserver.responses.poll();
    assertFalse(pipeline.downstreamObserver.isComplete);

    pipeline.downstreamObserver.controller.request(1);
    pipeline.downstreamObserver.responses.poll();

    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isComplete);
  }

  @Test
  public void testManyToOneCancelEarly() {
    FakePipeline pipeline = new FakePipeline(false, 1);

    pipeline.downstreamObserver.controller.request(1);
    pipeline.mediator.onResponse("a-b");
    pipeline.mediator.onComplete();

    pipeline.downstreamObserver.responses.poll();

    CancellationException cause = new CancellationException("forced cancellation");
    pipeline.downstreamObserver.controller.cancel(cause);
    pipeline.downstreamObserver.responses.poll();

    assertEquals(pipeline.downstreamObserver.error, cause);
    assertFalse("downstream should not have completed normally",
        pipeline.downstreamObserver.isComplete);
  }

  @Test
  public void testOneToMany() {
    FakePipeline pipeline = new FakePipeline(false, 2);

    // First request gets a partial response upstream
    pipeline.downstreamObserver.controller.request(1);
    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("a");
    assertTrue("Downstream should not have been notified",
        pipeline.downstreamObserver.responses.isEmpty());

    // Mediator will automatically send another request upstream to complete the response
    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("b");
    assertEquals("a-b", pipeline.downstreamObserver.responses.poll());

    // Make sure completion is delivered
    pipeline.mediator.onComplete();
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isComplete);
  }

  @Test
  public void testOneToManyAuto() {
    FakePipeline pipeline = new FakePipeline(true, 2);

    // First request gets a partial response upstream
    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("a");
    assertTrue("Downstream should not have been notified",
        pipeline.downstreamObserver.responses.isEmpty());

    // Mediator will automatically send another request upstream to complete the response
    assertEquals(1, (int) pipeline.upstreamController.requests.poll());
    pipeline.mediator.onResponse("b");
    assertEquals("a-b", pipeline.downstreamObserver.responses.poll());

    // Make sure completion is delivered
    pipeline.mediator.onComplete();
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isComplete);
  }

  @Test
  public void testOneToManyIncomplete() {
    FakePipeline pipeline = new FakePipeline(false, 2);

    // First request gets a partial response upstream
    pipeline.downstreamObserver.controller.request(1);
    pipeline.mediator.onResponse("a");
    pipeline.mediator.onComplete();

    // Make sure completion is delivered
    assertTrue("Downstream should have been notified of IncompleteStreamException",
        pipeline.downstreamObserver.error instanceof StreamMediator.IncompleteStreamException);

  }

  @Test
  public void testConcurrentCancel() throws InterruptedException {
    final FakePipeline pipeline = new FakePipeline(true, 1);
    final CountDownLatch latch = new CountDownLatch(2);

    Thread consumer = new Thread(new Runnable() {
      @Override
      public void run() {
        while (pipeline.downstreamObserver.error != null) {
          try {
            pipeline.downstreamObserver.responses.poll(10, TimeUnit.NANOSECONDS);
          } catch (InterruptedException e) {
            break;
          }
        }
        latch.countDown();
      }
    });

    consumer.start();

    Thread producer = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!pipeline.upstreamController.cancelled) {
          try {
            if (null != pipeline.upstreamController.requests.poll(10, TimeUnit.NANOSECONDS)) {
              pipeline.mediator.onResponse("a");
            }
          } catch (InterruptedException e) {
            break;
          }
        }
        latch.countDown();
      }
    });
    producer.start();

    pipeline.downstreamObserver.controller.cancel();

    latch.await();
  }

  static class FakePipeline {

    final FakeStreamController upstreamController;
    final StreamMediator<String, String> mediator;
    final FakeResponseObserver downstreamObserver;


    FakePipeline(boolean autoFlowControl, int partsPerResponse) {
      downstreamObserver = new FakeResponseObserver(autoFlowControl);
      mediator = new StreamMediator<>(new DasherizingDelegate(partsPerResponse),
          downstreamObserver);
      upstreamController = new FakeStreamController();

      mediator.onStart(upstreamController);
    }
  }

  /**
   * A simple implementation of a {@link StreamMediator.Delegate}. The input string is split by
   * dash, and the output is concatenated by dashes. The test can verify M:N behavior by adjusting
   * the partsPerResponse parameter and the number of dashes in the input.
   */
  static class DasherizingDelegate implements StreamMediator.Delegate<String, String> {

    private Queue<String> buffer = Queues.newArrayDeque();
    private final int partsPerResponse;

    DasherizingDelegate(int partsPerResponse) {
      this.partsPerResponse = partsPerResponse;
    }

    @Override
    public void push(String response) {
      buffer.addAll(Arrays.asList(response.split("-")));
    }

    @Override
    public boolean hasFullResponse() {
      return buffer.size() >= partsPerResponse;
    }

    @Override
    public boolean hasPartialResponse() {
      return !buffer.isEmpty();
    }

    @Override
    public String pop() {
      String[] parts = new String[partsPerResponse];

      for (int i = 0; i < partsPerResponse; i++) {
        parts[i] = buffer.poll();
      }
      return Joiner.on("-").join(parts);
    }
  }

  /**
   * A fake implementation of {@link ResponseObserver} to help testing interactions of the {@link
   * StreamMediator}. The implementation simply records method invocations to be inspected by a
   * test.
   */
  static class FakeResponseObserver implements ResponseObserver<String> {

    final boolean autoFlowControl;

    StreamController controller;
    BlockingQueue<String> responses = Queues.newLinkedBlockingDeque();
    volatile boolean isComplete;
    volatile Throwable error;

    FakeResponseObserver(boolean autoFlowControl) {
      this.autoFlowControl = autoFlowControl;
    }

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;

      if (!autoFlowControl) {
        controller.disableAutoInboundFlowControl();
      }
    }

    @Override
    public void onResponse(String response) {
      responses.add(response);
    }

    @Override
    public void onError(Throwable t) {
      error = t;
    }

    @Override
    public void onComplete() {
      isComplete = true;
    }
  }

  /**
   * A fake implementation of {@link StreamController} to help testing interactions of the {@link
   * StreamMediator}. The implementation simply records method invocations to be inspected by a
   * test.
   */
  private static class FakeStreamController extends StreamController {

    volatile boolean cancelled;
    volatile Throwable cancelCause;

    BlockingQueue<Integer> requests = Queues.newLinkedBlockingDeque();
    boolean autoFlowControl = true;

    @Override
    public void cancel(Throwable cause) {
      cancelled = true;
      cancelCause = cause;
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
