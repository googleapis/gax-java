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
package com.google.api.gax.rpc;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.gax.rpc.testing.FakeStreamingApi.ServerStreamingStashCallable;
import com.google.api.gax.rpc.testing.FakeStreamingApi.ServerStreamingStashCallable.Call;
import com.google.api.gax.rpc.testing.FakeStreamingApi.StashResponseObserver;
import com.google.common.base.Joiner;
import com.google.common.collect.Queues;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StreamMediatorTest {

  @Test
  public void testOneToOne() {
    FakePipeline pipeline = new FakePipeline(false, 1);

    // simple path: downstream requests 1 response, the request is proxied to upstream & upstream delivers.
    pipeline.downstreamObserver.getController().request(1);
    assertThat(pipeline.upstreamCall.getLastRequestCount()).isEqualTo(1);
    pipeline.mediator.onResponse("a");
    assertThat(pipeline.downstreamObserver.getNextResponse()).isEqualTo("a");

    pipeline.mediator.onComplete();
    assertThat(pipeline.downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testOneToOneAuto() {
    FakePipeline pipeline = new FakePipeline(true, 1);

    assertThat(pipeline.upstreamCall.getLastRequestCount()).isEqualTo(1);
    pipeline.mediator.onResponse("a");
    assertThat(pipeline.downstreamObserver.getNextResponse()).isEqualTo("a");

    assertThat(pipeline.upstreamCall.getLastRequestCount()).isEqualTo(1);
    pipeline.mediator.onResponse("b");
    assertThat(pipeline.downstreamObserver.getNextResponse()).isEqualTo("b");

    pipeline.mediator.onComplete();
    assertThat(pipeline.downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testManyToOne() {
    FakePipeline pipeline = new FakePipeline(false, 1);

    // First downstream request makes the upstream over produce
    pipeline.downstreamObserver.getController().request(1);
    assertEquals(1, (int) pipeline.upstreamCall.getLastRequestCount());
    pipeline.mediator.onResponse("a-b");
    assertEquals(pipeline.downstreamObserver.getNextResponse(), "a");
    assertThat(pipeline.downstreamObserver.getNextResponse()).isNull();

    // Next downstream request should fetch from buffer
    pipeline.downstreamObserver.getController().request(1);
    assertThat(pipeline.upstreamCall.getLastRequestCount()).isEqualTo(0);
    assertEquals("b", pipeline.downstreamObserver.getNextResponse());

    // Make sure completion is delivered
    pipeline.mediator.onComplete();
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isDone());
  }

  @Test
  public void testManyToOneAuto() {
    FakePipeline pipeline = new FakePipeline(true, 1);

    // First downstream request makes the upstream over produce
    assertEquals(1, (int) pipeline.upstreamCall.getLastRequestCount());
    pipeline.mediator.onResponse("a-b");
    pipeline.mediator.onComplete();

    assertEquals("a", pipeline.downstreamObserver.getNextResponse());
    assertEquals("b", pipeline.downstreamObserver.getNextResponse());
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isDone());
  }

  @Test
  public void testManyToOneCompleteIsQueued() {
    FakePipeline pipeline = new FakePipeline(false, 1);

    pipeline.downstreamObserver.getController().request(1);
    pipeline.mediator.onResponse("a-b");
    pipeline.mediator.onComplete();

    pipeline.downstreamObserver.getNextResponse();
    assertFalse(pipeline.downstreamObserver.isDone());

    pipeline.downstreamObserver.getController().request(1);
    pipeline.downstreamObserver.getNextResponse();

    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isDone());
  }

  @Test
  public void testManyToOneCancelEarly() {
    FakePipeline pipeline = new FakePipeline(false, 1);

    pipeline.downstreamObserver.getController().request(1);
    pipeline.mediator.onResponse("a-b");
    pipeline.mediator.onComplete();

    pipeline.downstreamObserver.getNextResponse();
    CancellationException cause = new CancellationException("forced cancellation");
    pipeline.downstreamObserver.getController().cancel(cause);

    assertThat(pipeline.downstreamObserver.getFinalError()).isSameAs(cause);
  }

  @Test
  public void testOneToMany() {
    FakePipeline pipeline = new FakePipeline(false, 2);

    // First request gets a partial response upstream
    pipeline.downstreamObserver.getController().request(1);
    assertEquals(1, (int) pipeline.upstreamCall.getLastRequestCount());
    pipeline.mediator.onResponse("a");
    assertThat(pipeline.downstreamObserver.getNextResponse()).isNull();

    // Mediator will automatically send another request upstream to complete the response
    assertEquals(1, (int) pipeline.upstreamCall.getLastRequestCount());
    pipeline.mediator.onResponse("b");
    assertEquals("a-b", pipeline.downstreamObserver.getNextResponse());

    // Make sure completion is delivered
    pipeline.mediator.onComplete();
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isDone());
  }

  @Test
  public void testOneToManyAuto() {
    FakePipeline pipeline = new FakePipeline(true, 2);

    // First request gets a partial response upstream
    assertEquals(1, (int) pipeline.upstreamCall.getLastRequestCount());
    pipeline.mediator.onResponse("a");
    assertThat(pipeline.downstreamObserver.getNextResponse()).isNull();

    // Mediator will automatically send another request upstream to complete the response
    assertEquals(1, (int) pipeline.upstreamCall.getLastRequestCount());
    pipeline.mediator.onResponse("b");
    assertEquals("a-b", pipeline.downstreamObserver.getNextResponse());

    // Make sure completion is delivered
    pipeline.mediator.onComplete();
    assertTrue("Downstream should be complete", pipeline.downstreamObserver.isDone());
  }

  @Test
  public void testOneToManyIncomplete() {
    FakePipeline pipeline = new FakePipeline(false, 2);

    // First request gets a partial response upstream
    pipeline.downstreamObserver.getController().request(1);
    pipeline.mediator.onResponse("a");
    pipeline.mediator.onComplete();

    // Make sure completion is delivered
    assertThat(pipeline.downstreamObserver.getFinalError())
        .isInstanceOf(StreamMediator.IncompleteStreamException.class);
  }

  @Test
  public void testConcurrentCancel() throws InterruptedException {
    final FakePipeline pipeline = new FakePipeline(true, 1);
    final CountDownLatch latch = new CountDownLatch(2);

    Thread consumer =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                while (!pipeline.downstreamObserver.isDone()) {
                  pipeline.downstreamObserver.getNextResponse();
                }
                latch.countDown();
              }
            });

    consumer.start();

    Thread producer =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                while (pipeline.upstreamCall.getCancelRequest() == null) {
                  if (pipeline.upstreamCall.getLastRequestCount() > 0) {
                    pipeline.mediator.onResponse("a");
                  }
                }
                pipeline
                    .upstreamCall
                    .getObserver()
                    .onError(pipeline.upstreamCall.getCancelRequest());
                latch.countDown();
              }
            });
    producer.start();

    pipeline.downstreamObserver.getController().cancel();

    latch.await();
  }

  static class FakePipeline {
    private final StreamMediator<String, String> mediator;
    private final StashResponseObserver<String> downstreamObserver;
    private final Call upstreamCall;

    FakePipeline(boolean autoFlowControl, int partsPerResponse) {
      downstreamObserver = new StashResponseObserver<>(autoFlowControl);

      mediator =
          new StreamMediator<>(new DasherizingDelegate(partsPerResponse), downstreamObserver);

      ServerStreamingStashCallable<String, String> callable = new ServerStreamingStashCallable<>();
      callable.call("request", mediator);
      this.upstreamCall = callable.getCall();
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
}
