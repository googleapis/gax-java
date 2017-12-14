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

import com.google.api.gax.rpc.testing.FakeStreamingApi.ServerStreamingStashCallable;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockResponseObserver;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCallable;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockStreamController;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
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

  @Before
  public void setUp() throws Exception {
    executor = Executors.newCachedThreadPool();
  }

  @After
  public void tearDown() throws Exception {
    executor.shutdownNow();
  }

  @Test
  public void testUnsolicitedResponseError() throws Exception {
    // Have the outer observer request manual flow control
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(false);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(1));
    MockServerStreamingCallable<String, String> innerCallable = new MockServerStreamingCallable<>();

    innerCallable.call("request", middleware);
    MockStreamController<String> innerController = innerCallable.popLastCall();

    // Nothing was requested by the outer observer (thats also in manual flow control)
    Preconditions.checkState(innerController.popLastPull() == 0);

    Throwable error = null;
    try {
      // send an unsolicited response
      innerController.getObserver().onResponse("a");
    } catch (Throwable t) {
      error = t;
    }

    Truth.assertThat(error).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testOneToOne() throws InterruptedException {
    // Have the outer observer request manual flow control
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(false);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(1));
    ServerStreamingStashCallable<String, String> innerCallable =
        new ServerStreamingStashCallable<>(ImmutableList.of("a"));

    innerCallable.call("request", middleware);

    // simple path: downstream requests 1 response, the request is proxied to upstream & upstream delivers.
    outerObserver.getController().request(1);

    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("a");
    Truth.assertThat(outerObserver.isDone()).isTrue();
  }

  @Test
  public void testOneToOneAuto() throws InterruptedException {
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(true);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(1));
    ServerStreamingStashCallable<String, String> innerCallable =
        new ServerStreamingStashCallable<>(ImmutableList.of("a", "b"));
    innerCallable.call("request", middleware);

    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("a");
    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("b");
    Truth.assertThat(outerObserver.isDone()).isTrue();
  }

  @Test
  public void testManyToOne() throws InterruptedException {
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(false);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(1));
    ServerStreamingStashCallable<String, String> innerCallable =
        new ServerStreamingStashCallable<>(ImmutableList.of("a-b"));
    innerCallable.call("request", middleware);

    Preconditions.checkState(outerObserver.popNextResponse() == null);

    // First downstream request makes the upstream over produce
    outerObserver.getController().request(1);
    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("a");
    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo(null);
    Truth.assertThat(outerObserver.isDone()).isFalse();

    // Next downstream request should fetch from buffer
    outerObserver.getController().request(1);
    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("b");

    // Make sure completion is delivered
    Truth.assertThat(outerObserver.isDone()).isTrue();
  }

  @Test
  public void testManyToOneAuto() throws InterruptedException {
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(true);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(1));
    ServerStreamingStashCallable<String, String> innerCallable =
        new ServerStreamingStashCallable<>(ImmutableList.of("a-b"));
    innerCallable.call("request", middleware);

    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("a");
    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("b");
    Truth.assertThat(outerObserver.isDone()).isTrue();
  }

  @Test
  public void testManyToOneCancelEarly() throws InterruptedException {
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(false);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(1));
    MockServerStreamingCallable<String, String> innerCallable = new MockServerStreamingCallable<>();
    innerCallable.call("request", middleware);

    MockStreamController<String> innerController = innerCallable.popLastCall();

    outerObserver.getController().request(1);
    innerController.getObserver().onResponse("a-b");
    innerController.getObserver().onComplete();

    outerObserver.popNextResponse();
    outerObserver.getController().cancel();

    Truth.assertThat(innerController.isCancelled()).isTrue();
    innerController.getObserver().onError(new RuntimeException("Some other upstream error"));

    Truth.assertThat(outerObserver.getFinalError()).isInstanceOf(CancellationException.class);
  }

  @Test
  public void testOneToMany() throws InterruptedException {
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(false);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(2));
    ServerStreamingStashCallable<String, String> innerCallable =
        new ServerStreamingStashCallable<>(ImmutableList.of("a", "b"));
    innerCallable.call("request", middleware);

    Preconditions.checkState(outerObserver.popNextResponse() == null);
    outerObserver.getController().request(1);

    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("a-b");
    Truth.assertThat(outerObserver.isDone()).isTrue();
    Truth.assertThat(outerObserver.getFinalError()).isNull();
  }

  @Test
  public void testOneToManyAuto() throws InterruptedException {
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(true);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(2));
    ServerStreamingStashCallable<String, String> innerCallable =
        new ServerStreamingStashCallable<>(ImmutableList.of("a", "b"));
    innerCallable.call("request", middleware);

    Truth.assertThat(outerObserver.popNextResponse()).isEqualTo("a-b");
    Truth.assertThat(outerObserver.isDone()).isTrue();
    Truth.assertThat(outerObserver.getFinalError()).isNull();
  }

  @Test
  public void testOneToManyIncomplete() {
    MockResponseObserver<String> outerObserver = new MockResponseObserver<>(true);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(2));
    ServerStreamingStashCallable<String, String> innerCallable =
        new ServerStreamingStashCallable<>(ImmutableList.of("a"));
    innerCallable.call("request", middleware);

    // Make sure completion is delivered
    Truth.assertThat(outerObserver.getFinalError()).isInstanceOf(IncompleteStreamException.class);
  }

  @Test
  public void testConcurrentCancel() throws InterruptedException {
    final MockResponseObserver<String> outerObserver = new MockResponseObserver<>(true);
    ReframingResponseObserver<String, String> middleware =
        new ReframingResponseObserver<>(outerObserver, new DasherizingReframer(2));
    MockServerStreamingCallable<String, String> innerCallable = new MockServerStreamingCallable<>();

    innerCallable.call("request", middleware);
    final MockStreamController<String> innerController = innerCallable.popLastCall();

    final CountDownLatch latch = new CountDownLatch(2);

    executor.submit(
        new Runnable() {
          @Override
          public void run() {
            while (!outerObserver.isDone()) {
              outerObserver.popNextResponse();
            }
            latch.countDown();
          }
        });

    executor.submit(
        new Runnable() {
          @Override
          public void run() {
            while (!innerController.isCancelled()) {
              if (innerController.popLastPull() > 0) {
                innerController.getObserver().onResponse("a");
              }
            }
            innerController
                .getObserver()
                .onError(new RuntimeException("Some other upstream error"));
            latch.countDown();
          }
        });

    outerObserver.getController().cancel();

    Truth.assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
  }

  /**
   * A simple implementation of a {@link Reframer}. The input string is split by dash, and the
   * output is concatenated by dashes. The test can verify M:N behavior by adjusting the
   * partsPerResponse parameter and the number of dashes in the input.
   */
  static class DasherizingReframer implements Reframer<String, String> {
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
}
