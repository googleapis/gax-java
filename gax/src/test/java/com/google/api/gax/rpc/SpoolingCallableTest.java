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

import com.google.api.core.ApiFuture;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpoolingCallableTest {
  private FakeStreamController controller;
  private FakeServerStreamingCallable streamingCallable;
  private SpoolingCallable<String, String> callable;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    controller = new FakeStreamController();
    streamingCallable = new FakeServerStreamingCallable();
    callable = new SpoolingCallable<>(streamingCallable);
  }

  @Test
  public void testHappyPath() throws InterruptedException, ExecutionException {
    ApiFuture<List<String>> result = callable.futureCall("request");

    Assert.assertEquals(
        "The request should be proxied to the streaming callable",
        "request",
        streamingCallable.request);

    streamingCallable.responseObserver.onStart(controller);
    streamingCallable.responseObserver.onResponse("response1");
    streamingCallable.responseObserver.onResponse("response2");
    streamingCallable.responseObserver.onComplete();

    Assert.assertEquals(
        "The response should be proxied back to the unary callable",
        Lists.newArrayList("response1", "response2"),
        result.get());
  }

  @Test
  public void testEarlyTermination() throws Exception {
    ApiFuture<List<String>> result = callable.futureCall("request");

    streamingCallable.responseObserver.onStart(controller);
    streamingCallable.responseObserver.onResponse("response1");
    result.cancel(true);
    streamingCallable.responseObserver.onResponse("response2");
    streamingCallable.responseObserver.onError(new CancellationException());

    expectedException.expect(CancellationException.class);
    result.get();
  }

  @Test
  public void testNoResults() throws Exception {
    ApiFuture<List<String>> result = callable.futureCall("request");
    streamingCallable.responseObserver.onStart(controller);
    streamingCallable.responseObserver.onComplete();

    Assert.assertEquals(Lists.newArrayList(), result.get());
  }

  private static class FakeServerStreamingCallable extends ServerStreamingCallable<String, String> {
    String request;
    ResponseObserver<String> responseObserver;
    ApiCallContext context;

    @Override
    public void call(
        String request, ResponseObserver<String> responseObserver, ApiCallContext context) {
      this.request = request;
      this.responseObserver = responseObserver;
      this.context = context;
    }
  }

  private static class FakeStreamController extends StreamController {
    boolean cancelled = false;
    boolean autoFlowControl = true;
    BlockingQueue<Integer> requests = Queues.newLinkedBlockingDeque();

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
      cancelled = true;
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
