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
package com.google.api.gax.grpc;

import static com.google.api.gax.grpc.testing.FakeServiceGrpc.METHOD_CLIENT_STREAMING_RECOGNIZE;
import static com.google.api.gax.grpc.testing.FakeServiceGrpc.METHOD_SERVER_STREAMING_RECOGNIZE;
import static com.google.api.gax.grpc.testing.FakeServiceGrpc.METHOD_STREAMING_RECOGNIZE;
import static com.google.api.gax.grpc.testing.FakeServiceGrpc.METHOD_STREAMING_RECOGNIZE_ERROR;

import com.google.api.gax.grpc.testing.FakeServiceImpl;
import com.google.api.gax.grpc.testing.InProcessServer;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.rpc.EntryPointBidiStreamingCallable;
import com.google.api.gax.rpc.EntryPointClientStreamingCallable;
import com.google.api.gax.rpc.EntryPointServerStreamingCallable;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.testing.FakeApiCallContext;
import com.google.common.collect.Iterators;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
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
public class GrpcDirectStreamingCallableTest {
  private InProcessServer<FakeServiceImpl> inprocessServer;
  private ManagedChannel channel;
  private FakeServiceImpl serviceImpl;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws InstantiationException, IllegalAccessException, IOException {
    String serverName = "fakeservice";
    serviceImpl = new FakeServiceImpl();
    inprocessServer = new InProcessServer<>(serviceImpl, serverName);
    inprocessServer.start();
    channel =
        InProcessChannelBuilder.forName(serverName).directExecutor().usePlaintext(true).build();
  }

  @After
  public void tearDown() {
    channel.shutdown();
    inprocessServer.stop();
  }

  @Test
  public void testBidiStreaming() throws Exception {
    BidiStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createBidiStreamingCallable(
            GrpcCallSettings.of(METHOD_STREAMING_RECOGNIZE), null, null);
    GrpcCallContext callContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    BidiStreamingCallable<Color, Money> streamingCallable =
        new EntryPointBidiStreamingCallable<>(callable, callContext);

    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver moneyObserver = new MoneyObserver(latch);

    Color request = Color.newBuilder().setRed(0.5f).build();
    ApiStreamObserver<Color> requestObserver = streamingCallable.bidiStreamingCall(moneyObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    latch.await(20, TimeUnit.SECONDS);
    Truth.assertThat(moneyObserver.error).isNull();
    Money expected = Money.newBuilder().setCurrencyCode("USD").setUnits(127).build();
    Truth.assertThat(moneyObserver.response).isEqualTo(expected);
    Truth.assertThat(moneyObserver.completed).isTrue();
  }

  @Test
  public void testBidiStreamingServerError() throws Exception {
    BidiStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createBidiStreamingCallable(
            GrpcCallSettings.of(METHOD_STREAMING_RECOGNIZE_ERROR), null, null);
    GrpcCallContext callContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    BidiStreamingCallable<Color, Money> streamingCallable =
        new EntryPointBidiStreamingCallable<>(callable, callContext);

    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver moneyObserver = new MoneyObserver(latch);

    Color request = Color.newBuilder().setRed(0.5f).build();
    ApiStreamObserver<Color> requestObserver = streamingCallable.bidiStreamingCall(moneyObserver);
    requestObserver.onNext(request);

    latch.await(20, TimeUnit.SECONDS);
    Truth.assertThat(moneyObserver.error).isNotNull();
    Truth.assertThat(moneyObserver.error).isInstanceOf(StatusRuntimeException.class);
    Truth.assertThat(((StatusRuntimeException) moneyObserver.error).getStatus())
        .isEqualTo(Status.INVALID_ARGUMENT);
    Truth.assertThat(moneyObserver.response).isNull();
  }

  @Test
  public void testBidiStreamingClientError() throws Exception {
    BidiStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createBidiStreamingCallable(
            GrpcCallSettings.of(METHOD_STREAMING_RECOGNIZE_ERROR), null, null);
    GrpcCallContext callContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    BidiStreamingCallable<Color, Money> streamingCallable =
        new EntryPointBidiStreamingCallable<>(callable, callContext);

    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver moneyObserver = new MoneyObserver(latch);

    Color request = Color.newBuilder().setRed(0.5f).build();
    ApiStreamObserver<Color> requestObserver = streamingCallable.bidiStreamingCall(moneyObserver);
    Throwable clientError = new StatusRuntimeException(Status.CANCELLED);
    requestObserver.onError(clientError);

    latch.await(20, TimeUnit.SECONDS);
    Truth.assertThat(moneyObserver.error).isNotNull();
    Truth.assertThat(moneyObserver.error).isInstanceOf(StatusRuntimeException.class);
    Truth.assertThat(((StatusRuntimeException) moneyObserver.error).getStatus().getCode())
        .isEqualTo(Status.CANCELLED.getCode());
    Truth.assertThat(moneyObserver.response).isNull();
    StatusException serverReceivedError = (StatusException) serviceImpl.getLastRecievedError();
    Truth.assertThat(serverReceivedError.getStatus()).isEqualTo(Status.CANCELLED);
  }

  @Test
  public void testServerStreaming() throws Exception {
    ServerStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.of(METHOD_SERVER_STREAMING_RECOGNIZE), null, null);
    GrpcCallContext callContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    ServerStreamingCallable<Color, Money> streamingCallable =
        new EntryPointServerStreamingCallable<>(callable, callContext);

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
    ServerStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.of(METHOD_SERVER_STREAMING_RECOGNIZE), null, null);
    GrpcCallContext callContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    ServerStreamingCallable<Color, Money> streamingCallable =
        new EntryPointServerStreamingCallable<>(callable, callContext);

    Color request = Color.newBuilder().setRed(0.5f).build();
    Iterator<Money> response = streamingCallable.blockingServerStreamingCall(request);
    List<Money> responseData = new ArrayList<>();
    Iterators.addAll(responseData, response);

    Money expected = Money.newBuilder().setCurrencyCode("USD").setUnits(127).build();
    Truth.assertThat(responseData).containsExactly(expected);
  }

  @Test
  public void testClientStreaming() throws Exception {
    ClientStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createClientStreamingCallable(
            GrpcCallSettings.of(METHOD_CLIENT_STREAMING_RECOGNIZE), null, null);
    GrpcCallContext callContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    ClientStreamingCallable<Color, Money> streamingCallable =
        new EntryPointClientStreamingCallable<>(callable, callContext);

    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver moneyObserver = new MoneyObserver(latch);

    Color request = Color.newBuilder().setRed(0.5f).build();
    ApiStreamObserver<Color> requestObserver = streamingCallable.clientStreamingCall(moneyObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    latch.await(20, TimeUnit.SECONDS);
    Truth.assertThat(moneyObserver.error).isNull();
    Money expected = Money.newBuilder().setCurrencyCode("USD").setUnits(127).build();
    Truth.assertThat(moneyObserver.response).isEqualTo(expected);
    Truth.assertThat(moneyObserver.completed).isTrue();
  }

  @Test
  public void testBadContext() {
    thrown.expect(IllegalArgumentException.class);
    ServerStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.of(METHOD_SERVER_STREAMING_RECOGNIZE), null, null);
    ServerStreamingCallable<Color, Money> streamingCallable =
        new EntryPointServerStreamingCallable<>(callable, FakeApiCallContext.of());
    Color request = Color.newBuilder().setRed(0.5f).build();
    streamingCallable.blockingServerStreamingCall(request);
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
  };
}
