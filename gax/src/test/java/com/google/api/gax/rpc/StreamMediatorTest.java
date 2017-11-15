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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import javax.annotation.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StreamMediatorTest {

  @Test
  public void testPassthrough() {
    StreamMediator.Delegate<String, String> delegate = new DasherizingDelegate(1);
    SpoolingResponseObserver<String> downstream = new SpoolingResponseObserver<>();
    SpoolingController upstreamController = new SpoolingController();
    StreamMediator<String, String> observer = new StreamMediator<>(delegate,
        downstream);

    observer.onStart(upstreamController);

    downstream.controller.request(1);
    observer.onResponse("a");
    Assert.assertEquals(downstream.responses, Lists.newArrayList("a"));

    downstream.controller.request(1);
    observer.onResponse("b");
    Assert.assertEquals(downstream.responses, Lists.newArrayList("a", "b"));

    Assert
        .assertFalse("downstream should not be complete until upstream is", downstream.isComplete);
    observer.onComplete();
    Assert
        .assertTrue("downstream should be complete as soon as upstream is", downstream.isComplete);
  }

  @Test
  public void testOneToMany() {
    SpoolingController upstreamController = new SpoolingController();

    StreamMediator.Delegate<String, String> delegate = new DasherizingDelegate(1);
    SpoolingResponseObserver<String> downstream = new SpoolingResponseObserver<>();
    StreamMediator<String, String> observer = new StreamMediator<>(delegate,
        downstream);

    observer.onStart(upstreamController);
    Assert.assertFalse("Auto flow control should be off", upstreamController.autoFlowControl);

    downstream.controller.request(1);
    observer.onResponse("a-b-c-d");
    observer.onComplete();
    Assert.assertEquals(downstream.responses, Lists.newArrayList("a"));

    downstream.controller.request(2);
    Assert.assertEquals(downstream.responses, Lists.newArrayList("a", "b", "c"));

    Assert.assertFalse("downstream should not be complete while buffer still have elements",
        downstream.isComplete);
    downstream.controller.request(1);
    Assert.assertEquals(downstream.responses, Lists.newArrayList("a", "b", "c", "d"));

    Assert
        .assertTrue("downstream should be closed once the buffer is empty", downstream.isComplete);
  }

  @Test
  public void testManyToOne() {
    SpoolingController upstreamController = new SpoolingController();

    StreamMediator.Delegate<String, String> delegate = new DasherizingDelegate(2);
    SpoolingResponseObserver<String> downstream = new SpoolingResponseObserver<>();
    StreamMediator<String, String> observer = new StreamMediator<>(delegate,
        downstream);

    observer.onStart(upstreamController);

    downstream.controller.request(1);
    observer.onResponse("a");
    Assert.assertEquals(downstream.responses, Collections.<String>emptyList());

    Assert.assertEquals(upstreamController.requestCount, 2);
    observer.onResponse("b");

    Assert.assertEquals(downstream.responses, Lists.newArrayList("a-b"));

    downstream.controller.request(1);
    observer.onResponse("c-d");
    Assert.assertEquals(downstream.responses, Lists.newArrayList("a-b", "c-d"));
    Assert.assertEquals(upstreamController.requestCount, 3);
    Assert
        .assertFalse("Downstream should not be complete until upstream is", downstream.isComplete);

    observer.onComplete();
    Assert.assertTrue("Downstream should close as soon upstream closes when nothing is buffered",
        downstream.isComplete);
  }

  static class DasherizingDelegate implements StreamMediator.Delegate<String, String> {

    private Queue<String> buffer = Queues.newArrayDeque();
    private final int partsPerResponse;

    public DasherizingDelegate(int partsPerResponse) {
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
      StringBuilder output = new StringBuilder();
      Joiner joiner = Joiner.on("-");

      String[] parts = new String[partsPerResponse];

      for (int i = 0; i < partsPerResponse; i++) {
        parts[i] = buffer.poll();
      }
      return Joiner.on("-").join(parts);
    }
  }

  static class SpoolingResponseObserver<T> implements ResponseObserver<T> {

    StreamController controller;
    List<T> responses = new ArrayList<>();
    boolean isComplete;
    Throwable error;

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;
      controller.disableAutoInboundFlowControl();
    }

    @Override
    public void onResponse(T response) {
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

  static class SpoolingController extends StreamController {

    boolean cancelled;
    String cancelMessage;
    Throwable cancelCause;

    int requestCount;
    boolean autoFlowControl = true;

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
      cancelled = true;
      cancelMessage = message;
      cancelCause = cause;
    }

    @Override
    public void disableAutoInboundFlowControl() {
      autoFlowControl = false;
    }

    @Override
    public void request(int count) {
      requestCount += count;
    }
  }
}
