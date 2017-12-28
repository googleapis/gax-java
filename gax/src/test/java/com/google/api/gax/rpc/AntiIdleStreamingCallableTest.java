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
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.rpc.AntiIdleStreamingCallable.IdleConnectionException;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class AntiIdleStreamingCallableTest {

  private UpstreamCallable<String, String> upstream;

  private FakeApiClock clock;
  private Duration waitTime = Duration.ofSeconds(10);
  private Duration idleTime = Duration.ofMinutes(5);
  private Duration checkInterval = Duration.ofSeconds(5);

  private AntiIdleStreamingCallable<String, String> callable;

  @Before
  public void setUp() throws Exception {
    clock = new FakeApiClock(0);
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);

    upstream = new UpstreamCallable<>();

    callable =
        new AntiIdleStreamingCallable<>(
            upstream, executor, clock, waitTime, idleTime, checkInterval);
  }

  @Test
  public void testRequestPassthrough() throws Exception {
    AccumulatingObserver<String> downstreamObserver = new AccumulatingObserver<>(false);
    callable.call("req", downstreamObserver);
    downstreamObserver.controller.get(1, TimeUnit.MILLISECONDS).request(1);

    Call<String, String> call = upstream.calls.poll(1, TimeUnit.MILLISECONDS);
    Truth.assertThat(call.upstreamController.getNextRequestCount()).isEqualTo(1);
  }

  @Test
  public void testWaitTimeout() throws Exception {
    AccumulatingObserver<String> downstreamObserver = new AccumulatingObserver<>(false);
    callable.call("req", downstreamObserver);
    downstreamObserver.controller.get(1, TimeUnit.MILLISECONDS).request(1);

    Call<String, String> call = upstream.calls.poll(1, TimeUnit.MILLISECONDS);

    clock.incrementNanoTime(waitTime.toNanos() - 1);
    callable.checkAll();
    Truth.assertThat(call.upstreamController.cancelled).isFalse();

    clock.incrementNanoTime(1);
    callable.checkAll();
    Truth.assertThat(call.upstreamController.cancelled).isTrue();
    call.observer.onError(
        new RuntimeException("Some upstream exception representing cancellation"));

    Throwable actualError = null;
    try {
      Truth.assertThat(downstreamObserver.done.get());
    } catch (ExecutionException t) {
      actualError = t.getCause();
    }
    Truth.assertThat(actualError).isInstanceOf(IdleConnectionException.class);
  }

  @Test
  public void testIdleTimeout() throws InterruptedException {
    AccumulatingObserver<String> downstreamObserver = new AccumulatingObserver<>(false);
    callable.call("req", downstreamObserver);

    Call<String, String> call = upstream.calls.poll(1, TimeUnit.MILLISECONDS);

    clock.incrementNanoTime(idleTime.toNanos() - 1);
    callable.checkAll();
    Truth.assertThat(call.upstreamController.cancelled).isFalse();

    clock.incrementNanoTime(1);
    callable.checkAll();
    Truth.assertThat(call.upstreamController.cancelled).isTrue();
    call.observer.onError(
        new RuntimeException("Some upstream exception representing cancellation"));

    Throwable actualError = null;
    try {
      Truth.assertThat(downstreamObserver.done.get());
    } catch (ExecutionException t) {
      actualError = t.getCause();
    }
    Truth.assertThat(actualError).isInstanceOf(IdleConnectionException.class);
  }

  @Test
  public void testMultiple() throws InterruptedException, ExecutionException {
    // Start stream1
    AccumulatingObserver<String> downstreamObserver1 = new AccumulatingObserver<>(false);
    callable.call("req", downstreamObserver1);
    Call<String, String> call1 = upstream.calls.poll(1, TimeUnit.MILLISECONDS);
    downstreamObserver1.controller.get().request(1);

    // Start stream2
    AccumulatingObserver<String> downstreamObserver2 = new AccumulatingObserver<>(false);
    callable.call("req2", downstreamObserver2);
    Call<String, String> call2 = upstream.calls.poll(1, TimeUnit.MILLISECONDS);
    downstreamObserver2.controller.get().request(1);

    // Give stream1 a response at the last possible moment
    clock.incrementNanoTime(waitTime.toNanos());
    call1.observer.onResponse("resp1");

    // run the callable
    callable.checkAll();

    // Call1 should be ok
    Truth.assertThat(call1.upstreamController.cancelled).isFalse();

    // Call2 should be timed out
    Truth.assertThat(call2.upstreamController.cancelled).isTrue();
  }

  static class Call<ReqT, RespT> {
    final ReqT request;
    final ResponseObserver<RespT> observer;
    private final AccumulatingController upstreamController;

    Call(
        ReqT request, ResponseObserver<RespT> observer, AccumulatingController upstreamController) {
      this.request = request;
      this.observer = observer;
      this.upstreamController = upstreamController;
    }
  }

  static class UpstreamCallable<ReqT, RespT> extends ServerStreamingCallable<ReqT, RespT> {
    BlockingQueue<Call<ReqT, RespT>> calls = Queues.newLinkedBlockingDeque();

    @Override
    public void call(ReqT request, ResponseObserver<RespT> observer, ApiCallContext context) {
      Call<ReqT, RespT> call = new Call<>(request, observer, new AccumulatingController());
      calls.add(call);
      call.observer.onStart(call.upstreamController);
    }
  }

  static class AccumulatingController implements StreamController {
    private BlockingQueue<Integer> requests = Queues.newLinkedBlockingDeque();
    private boolean cancelled;

    @Override
    public void disableAutoInboundFlowControl() {}

    @Override
    public void request(int count) {
      requests.add(count);
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    int getNextRequestCount() throws InterruptedException {
      Integer count = requests.poll(1, TimeUnit.SECONDS);
      if (count == null) {
        count = 0;
      }
      return count;
    }
  }

  static class AccumulatingObserver<T> implements ResponseObserver<T> {
    boolean autoFlowControl;
    SettableApiFuture<StreamController> controller = SettableApiFuture.create();
    Queue<T> responses = Queues.newLinkedBlockingDeque();
    SettableApiFuture<Void> done = SettableApiFuture.create();

    AccumulatingObserver(boolean autoFlowControl) {
      this.autoFlowControl = autoFlowControl;
    }

    @Override
    public void onStart(StreamController controller) {
      if (!autoFlowControl) {
        controller.disableAutoInboundFlowControl();
      }
      this.controller.set(controller);
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
