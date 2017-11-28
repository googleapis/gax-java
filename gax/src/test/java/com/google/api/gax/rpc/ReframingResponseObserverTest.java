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

import com.google.api.core.SettableApiFuture;
import com.google.common.base.Joiner;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReframingResponseObserverTest {
  private ExecutorService executor;

  private AccumulatingObserver<String> downstreamObserver;
  private ReframingResponseObserver<String, String> mediator;
  private StashController upstreamCall;

  @Before
  public void setUp() throws Exception {
    executor = Executors.newCachedThreadPool();
  }

  private void setUpPipeline(boolean autoFlowControl, int partsPerResponse) {
    downstreamObserver = new AccumulatingObserver<>(autoFlowControl);

    mediator =
        new ReframingResponseObserver<>(
            downstreamObserver, new DasherizingReframer(partsPerResponse));

    ServerStreamingStashCallable<String, String> callable = new ServerStreamingStashCallable<>();
    callable.call("request", mediator);
    this.upstreamCall = callable.getControllerForLastCall();
  }

  @After
  public void tearDown() throws Exception {
    executor.shutdownNow();
  }

  @Test
  public void testOneToOne() throws InterruptedException {
    setUpPipeline(false, 1);

    // simple path: downstream requests 1 response, the request is proxied to upstream & upstream delivers.
    downstreamObserver.controller.request(1);
    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("a");
    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("a");

    mediator.onComplete();
    Truth.assertThat(downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testOneToOneAuto() throws InterruptedException {
    setUpPipeline(true, 1);

    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("a");
    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("a");

    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("b");
    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("b");

    mediator.onComplete();
    Truth.assertThat(downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testManyToOne() throws InterruptedException {
    setUpPipeline(false, 1);

    // First downstream request makes the upstream over produce
    downstreamObserver.controller.request(1);
    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("a-b");
    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("a");
    Truth.assertThat(downstreamObserver.getNextResponse()).isNull();

    // Next downstream request should fetch from buffer
    downstreamObserver.controller.request(1);
    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(0);
    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("b");

    // Make sure completion is delivered
    mediator.onComplete();
    Truth.assertThat(downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testManyToOneAuto() throws InterruptedException {
    setUpPipeline(true, 1);

    // First downstream request makes the upstream over produce
    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("a-b");
    mediator.onComplete();

    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("a");
    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("b");
    Truth.assertThat(downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testManyToOneCompleteIsQueued() throws InterruptedException {
    setUpPipeline(false, 1);

    downstreamObserver.controller.request(1);
    mediator.onResponse("a-b");
    mediator.onComplete();

    downstreamObserver.getNextResponse();
    Truth.assertThat(downstreamObserver.isDone()).isFalse();

    downstreamObserver.controller.request(1);
    downstreamObserver.getNextResponse();

    Truth.assertThat(downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testManyToOneCancelEarly() throws InterruptedException {
    setUpPipeline(false, 1);

    downstreamObserver.controller.request(1);
    mediator.onResponse("a-b");
    mediator.onComplete();

    downstreamObserver.getNextResponse();
    downstreamObserver.controller.cancel();

    Truth.assertThat(upstreamCall.cancelled).isTrue();
    upstreamCall.downstreamObserver.onError(new RuntimeException("Some other upstream error"));

    Truth.assertThat(downstreamObserver.getFinalError()).isInstanceOf(CancellationException.class);
  }

  @Test
  public void testOneToMany() throws InterruptedException {
    setUpPipeline(false, 2);

    // First request gets a partial response upstream
    downstreamObserver.controller.request(1);
    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("a");
    Truth.assertThat(downstreamObserver.getNextResponse()).isNull();

    // Mediator will automatically send another request upstream to complete the response
    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("b");
    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("a-b");

    // Make sure completion is delivered
    mediator.onComplete();
    Truth.assertThat(downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testOneToManyAuto() throws InterruptedException {
    setUpPipeline(true, 2);

    // First request gets a partial response upstream
    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("a");
    Truth.assertThat(downstreamObserver.getNextResponse()).isNull();

    // Mediator will automatically send another request upstream to complete the response
    Truth.assertThat(upstreamCall.getLastRequestCount()).isEqualTo(1);
    mediator.onResponse("b");
    Truth.assertThat(downstreamObserver.getNextResponse()).isEqualTo("a-b");

    // Make sure completion is delivered
    mediator.onComplete();
    Truth.assertThat(downstreamObserver.isDone()).isTrue();
  }

  @Test
  public void testOneToManyIncomplete() {
    setUpPipeline(false, 2);

    // First request gets a partial response upstream
    downstreamObserver.controller.request(1);
    mediator.onResponse("a");
    mediator.onComplete();

    // Make sure completion is delivered
    Truth.assertThat(downstreamObserver.getFinalError())
        .isInstanceOf(ReframingResponseObserver.IncompleteStreamException.class);
  }

  @Test
  public void testConcurrentCancel() throws InterruptedException {
    setUpPipeline(true, 1);
    final CountDownLatch latch = new CountDownLatch(2);

    executor.submit(
        new Runnable() {
          @Override
          public void run() {
            while (!downstreamObserver.isDone()) {
              downstreamObserver.getNextResponse();
            }
            latch.countDown();
          }
        });

    executor.submit(
        new Runnable() {
          @Override
          public void run() {
            while (!upstreamCall.cancelled) {
              if (upstreamCall.getLastRequestCount() > 0) {
                mediator.onResponse("a");
              }
            }
            upstreamCall.getObserver().onError(new RuntimeException("Some other upstream error"));
            latch.countDown();
          }
        });

    downstreamObserver.controller.cancel();

    Truth.assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
  }

  /**
   * A simple implementation of a {@link ReframingResponseObserver.Reframer}. The input string is
   * split by dash, and the output is concatenated by dashes. The test can verify M:N behavior by
   * adjusting the partsPerResponse parameter and the number of dashes in the input.
   */
  static class DasherizingReframer implements ReframingResponseObserver.Reframer<String, String> {
    final Queue<String> buffer = Queues.newArrayDeque();
    final int partsPerResponse;

    DasherizingReframer(int partsPerResponse) {
      this.partsPerResponse = partsPerResponse;
    }

    @Override
    public void push(String response) {
      buffer.addAll(Arrays.asList(response.split("-")));
    }

    @Override
    public boolean hasFullFrame() {
      return buffer.size() >= partsPerResponse;
    }

    @Override
    public boolean hasPartialFrame() {
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

  static class ServerStreamingStashCallable<RequestT, ResponseT>
      extends ServerStreamingCallable<RequestT, ResponseT> {
    final BlockingQueue<StashController> controllers = Queues.newLinkedBlockingDeque();

    @Override
    public void call(
        RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context) {

      StashController<ResponseT> controller = new StashController<>(responseObserver);
      controllers.add(controller);
      responseObserver.onStart(controller);
    }

    StashController getControllerForLastCall() {
      try {
        return controllers.poll(1, TimeUnit.SECONDS);
      } catch (Throwable e) {
        return null;
      }
    }
  }

  static class StashController<ResponseT> implements StreamController {
    final ResponseObserver<ResponseT> downstreamObserver;

    final BlockingQueue<Integer> pulls = Queues.newLinkedBlockingQueue();
    volatile boolean cancelled;

    StashController(ResponseObserver<ResponseT> downstreamObserver) {
      this.downstreamObserver = downstreamObserver;
    }

    @Override
    public void disableAutoInboundFlowControl() {}

    @Override
    public void request(int count) {
      pulls.add(count);
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    ResponseObserver<ResponseT> getObserver() {
      return downstreamObserver;
    }

    int getLastRequestCount() {
      Integer results;

      try {
        results = pulls.poll(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }

      if (results == null) {
        return 0;
      } else {
        return results;
      }
    }
  }

  static class AccumulatingObserver<T> implements ResponseObserver<T> {
    final boolean autoFlowControl;
    StreamController controller;
    final BlockingQueue<T> responses = Queues.newLinkedBlockingDeque();
    final SettableApiFuture<Void> done = SettableApiFuture.create();

    AccumulatingObserver(boolean autoFlowControl) {
      this.autoFlowControl = autoFlowControl;
    }

    private T getNextResponse() {
      try {
        return responses.poll(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    private Throwable getFinalError() {
      try {
        done.get(1, TimeUnit.SECONDS);
        return null;
      } catch (ExecutionException e) {
        return e.getCause();
      } catch (Throwable t) {
        return t;
      }
    }

    private boolean isDone() {
      return done.isDone();
    }

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;
      if (!autoFlowControl) {
        controller.disableAutoInboundFlowControl();
      }
    }

    @Override
    public void onResponse(T response) {
      responses.add(response);
    }

    @Override
    public void onError(Throwable t) {
      done.setException(t);
    }

    @Override
    public void onComplete() {
      done.set(null);
    }
  }
}
