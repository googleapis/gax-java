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

import com.google.api.core.ApiFuture;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpoolingCallableTest {
  private AccumulatingCallable<String, String> upstream;
  private SpoolingCallable<String, String> callable;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    upstream = new AccumulatingCallable<>();
    callable = new SpoolingCallable<>(upstream);
  }

  @Test
  public void testHappyPath() throws InterruptedException, ExecutionException {
    ApiFuture<List<String>> result = callable.futureCall("request");
    AccumulatingController<String> call = upstream.getCall();

    assertThat(call.autoFlowControl).isTrue();

    call.observer.onResponse("response1");
    call.observer.onResponse("response2");
    call.observer.onComplete();

    assertThat(result.get()).containsAllOf("response1", "response2").inOrder();
  }

  @Test
  public void testEarlyTermination() throws Exception {
    ApiFuture<List<String>> result = callable.futureCall("request");
    AccumulatingController<String> call = upstream.getCall();

    // The caller cancels the stream while receiving responses
    call.observer.onResponse("response1");
    result.cancel(true);
    call.observer.onResponse("response2");

    // The cancellation should propagate upstream
    Truth.assertThat(call.cancelled).isTrue();
    // Then we fake a cancellation going the other way (it will be wrapped in StatusRuntimeException
    // for grpc)
    call.observer.onError(new RuntimeException("Some other upstream cancellation indicator"));

    // However the inner cancellation exception will be masked by an outer CancellationException
    expectedException.expect(CancellationException.class);
    result.get();
  }

  @Test
  public void testNoResults() throws Exception {
    ApiFuture<List<String>> result = callable.futureCall("request");
    AccumulatingController<String> call = upstream.getCall();

    call.observer.onComplete();

    assertThat(result.get()).isEmpty();
  }

  static class AccumulatingCallable<ReqT, RespT> extends ServerStreamingCallable<ReqT, RespT> {
    BlockingQueue<AccumulatingController<RespT>> calls = Queues.newLinkedBlockingQueue();

    @Override
    public void call(
        ReqT request, ResponseObserver<RespT> responseObserver, ApiCallContext context) {

      AccumulatingController<RespT> controller = new AccumulatingController<>(responseObserver);
      calls.add(controller);
      responseObserver.onStart(controller);
    }

    AccumulatingController<RespT> getCall() throws InterruptedException {
      return calls.poll(1, TimeUnit.SECONDS);
    }
  }

  static class AccumulatingController<RespT> implements StreamController {
    final ResponseObserver<RespT> observer;
    boolean autoFlowControl = true;
    final BlockingQueue<Integer> pulls = Queues.newLinkedBlockingQueue();
    boolean cancelled;

    AccumulatingController(ResponseObserver<RespT> observer) {
      this.observer = observer;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    @Override
    public void disableAutoInboundFlowControl() {
      autoFlowControl = false;
    }

    @Override
    public void request(int count) {
      pulls.add(count);
    }
  }
}
