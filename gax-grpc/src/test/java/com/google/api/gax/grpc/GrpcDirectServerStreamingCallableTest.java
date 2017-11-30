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
package com.google.api.gax.grpc;

import static com.google.api.gax.grpc.testing.FakeServiceGrpc.METHOD_SERVER_STREAMING_RECOGNIZE;

import com.google.api.gax.grpc.testing.FakeServiceImpl;
import com.google.api.gax.grpc.testing.InProcessServer;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.common.collect.Iterators;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GrpcDirectServerStreamingCallableTest {
  private InProcessServer<FakeServiceImpl> inprocessServer;
  private ManagedChannel channel;
  private FakeServiceImpl serviceImpl;
  private ClientContext clientContext;
  private ServerStreamingCallable<Color, Money> streamingCallable;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws InstantiationException, IllegalAccessException, IOException {
    String serverName = "fakeservice";
    serviceImpl = new FakeServiceImpl();
    inprocessServer = new InProcessServer<>(serviceImpl, serverName);
    inprocessServer.start();
    channel =
        InProcessChannelBuilder.forName(serverName).directExecutor().usePlaintext(true).build();
    clientContext =
        ClientContext.newBuilder()
            .setTransportChannel(GrpcTransportChannel.create(channel))
            .setDefaultCallContext(GrpcCallContext.of(channel, CallOptions.DEFAULT))
            .build();
    streamingCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE), null, clientContext);
  }

  @After
  public void tearDown() {
    channel.shutdown();
    inprocessServer.stop();
  }

  @Test
  public void testBadContext() {
    thrown.expect(IllegalArgumentException.class);

    streamingCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE),
            null,
            clientContext
                .toBuilder()
                .setDefaultCallContext(FakeCallContext.createDefault())
                .build());

    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver observer = new MoneyObserver(latch);

    Color request = Color.newBuilder().setRed(0.5f).build();
    streamingCallable.serverStreamingCall(request, observer);
  }

  @Test
  public void testServerStreaming() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver moneyObserver = new MoneyObserver(latch);

    Color request = Color.newBuilder().setRed(0.5f).build();
    streamingCallable.serverStreamingCall(request, moneyObserver);

    latch.await(20, TimeUnit.SECONDS);
    Truth.assertThat(moneyObserver.error).isNull();
    Money expected = Money.newBuilder().setCurrencyCode("USD").setUnits(127).build();
    Truth.assertThat(moneyObserver.response).isEqualTo(expected);
  }

  @Test
  public void testBlockingServerStreaming() throws Exception {
    Color request = Color.newBuilder().setRed(0.5f).build();
    Iterator<Money> response = streamingCallable.blockingServerStreamingCall(request);
    List<Money> responseData = new ArrayList<>();
    Iterators.addAll(responseData, response);

    Money expected = Money.newBuilder().setCurrencyCode("USD").setUnits(127).build();
    Truth.assertThat(responseData).containsExactly(expected);
  }

  private static class MoneyObserver implements ApiStreamObserver<Money> {
    volatile Money response;
    volatile Throwable error;
    volatile boolean completed;
    CountDownLatch latch;

    MoneyObserver(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void onNext(Money value) {
      response = value;
      latch.countDown();
    }

    @Override
    public void onError(Throwable t) {
      error = t;
      latch.countDown();
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }
}
