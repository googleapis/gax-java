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

import com.google.api.core.ApiFuture;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
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
public class FirstElementCallableTest {
  private AccumulatingCallable<String, String> upstream;
  private FirstElementCallable<String, String> callable;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    upstream = new AccumulatingCallable<>();
    callable = new FirstElementCallable<>(upstream);
  }

  @Test
  public void testHappyPath() throws InterruptedException, ExecutionException {
    ApiFuture<String> result = callable.futureCall("request");
    AccumulatingController<String> call = upstream.getCall();

    Truth.assertThat(call.autoFlowControl).isFalse();

    Truth.assertThat(call.getLastRequestCount()).isEqualTo(1);
    call.observer.onResponse("response");
    Truth.assertThat(result.get()).isEqualTo("response");

    Truth.assertThat(call.observer).isNotNull();
  }

  @Test
  public void testEarlyTermination() throws Exception {
    ApiFuture<String> result = callable.futureCall("request");
    AccumulatingController<String> call = upstream.getCall();

    // callable should request a single element on start
    Truth.assertThat(call.autoFlowControl).isFalse();
    Truth.assertThat(call.getLastRequestCount()).isEqualTo(1);

    // Then the user promptly cancels it
    result.cancel(true);

    // The cancellation should propagate to the inner callable
    Truth.assertThat(call.cancelled).isTrue();
    // Then we fake a cancellation going the other way (it will be wrapped in StatusRuntimeException
    // for grpc)
    call.observer.onError(new RuntimeException("Some other upstream cancellation notice"));

    Throwable actualError = null;
    try {
      result.get(1, TimeUnit.SECONDS);
    } catch (Throwable e) {
      actualError = e;
    }

    // However, that exception will be ignored and will be replaced by a generic CancellationException
    Truth.assertThat(actualError).isInstanceOf(CancellationException.class);
  }

  @Test
  public void testNoResults() throws Exception {
    ApiFuture<String> result = callable.futureCall("request");
    AccumulatingController<String> call = upstream.getCall();

    Truth.assertThat(call.autoFlowControl).isFalse();

    call.observer.onComplete();
    Truth.assertThat(result.get()).isNull();
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

    int getLastRequestCount() throws InterruptedException {
      Integer pull = pulls.poll(1, TimeUnit.SECONDS);
      if (pull == null) {
        pull = 0;
      }
      return pull;
    }
  }
}
