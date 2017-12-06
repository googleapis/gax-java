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

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.grpc.testing.FakeServiceImpl;
import com.google.api.gax.grpc.testing.InProcessServer;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StreamController;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.common.collect.Iterators;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
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
  private static final Color DEFAULT_REQUEST = Color.newBuilder().setRed(0.5f).build();
  private static final Color ASYNC_REQUEST = DEFAULT_REQUEST.toBuilder().setGreen(1000).build();
  private static final Color ERROR_REQUEST = Color.newBuilder().setRed(-1).build();
  private static final Money DEFAULT_RESPONSE =
      Money.newBuilder().setCurrencyCode("USD").setUnits(127).build();

  private InProcessServer<FakeServiceImpl> inprocessServer;
  private ManagedChannel channel;
  private ClientContext clientContext;
  private ServerStreamingCallable<Color, Money> streamingCallable;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws InstantiationException, IllegalAccessException, IOException {
    String serverName = "fakeservice";
    FakeServiceImpl serviceImpl = new FakeServiceImpl();
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
    streamingCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE),
            null,
            clientContext
                .toBuilder()
                .setDefaultCallContext(FakeCallContext.createDefault())
                .build());

    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver observer = new MoneyObserver(true, latch);

    thrown.expect(IllegalArgumentException.class);
    streamingCallable.call(DEFAULT_REQUEST, observer);
  }

  @Test
  public void testServerStreamingStart() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver moneyObserver = new MoneyObserver(true, latch);

    streamingCallable.call(DEFAULT_REQUEST, moneyObserver);

    Truth.assertThat(moneyObserver.controller).isNotNull();
  }

  @Test
  public void testServerStreaming() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    MoneyObserver moneyObserver = new MoneyObserver(true, latch);

    streamingCallable.call(DEFAULT_REQUEST, moneyObserver);

    latch.await(20, TimeUnit.SECONDS);
    Truth.assertThat(moneyObserver.error).isNull();
    Truth.assertThat(moneyObserver.response).isEqualTo(DEFAULT_RESPONSE);
  }

  @Test
  public void testManualFlowControl() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    MoneyObserver moneyObserver = new MoneyObserver(false, latch);

    streamingCallable.call(DEFAULT_REQUEST, moneyObserver);

    latch.await(500, TimeUnit.MILLISECONDS);
    Truth.assertWithMessage("Received response before requesting it")
        .that(moneyObserver.response)
        .isNull();

    moneyObserver.controller.request(1);
    latch.await(500, TimeUnit.MILLISECONDS);

    Truth.assertThat(moneyObserver.response).isEqualTo(DEFAULT_RESPONSE);
    Truth.assertThat(moneyObserver.completed).isTrue();
  }

  @Test
  public void testCancelClientCall() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver moneyObserver = new MoneyObserver(false, latch);

    streamingCallable.call(ASYNC_REQUEST, moneyObserver);

    moneyObserver.controller.cancel();
    moneyObserver.controller.request(1);
    latch.await(500, TimeUnit.MILLISECONDS);

    Truth.assertThat(moneyObserver.error).isInstanceOf(CancellationException.class);
    Truth.assertThat(moneyObserver.error).hasMessage("User cancelled stream");
  }

  @Test
  public void testOnResponseError() throws Throwable {
    CountDownLatch latch = new CountDownLatch(1);
    MoneyObserver moneyObserver = new MoneyObserver(true, latch);

    streamingCallable.call(ERROR_REQUEST, moneyObserver);
    latch.await(500, TimeUnit.MILLISECONDS);

    Truth.assertThat(moneyObserver.error).isInstanceOf(StatusRuntimeException.class);
    Truth.assertThat(moneyObserver.error).hasMessage("INVALID_ARGUMENT: red must be positive");
  }

  @Test
  public void testObserverErrorCancelsCall() throws Throwable {
    final RuntimeException expectedCause = new RuntimeException("some error");
    final SettableApiFuture<Throwable> actualErrorF = SettableApiFuture.create();

    ResponseObserver<Money> moneyObserver =
        new ResponseObserver<Money>() {
          @Override
          public void onStart(StreamController controller) {}

          @Override
          public void onResponse(Money response) {
            throw expectedCause;
          }

          @Override
          public void onError(Throwable t) {
            actualErrorF.set(t);
          }

          @Override
          public void onComplete() {
            actualErrorF.set(null);
          }
        };

    streamingCallable.call(DEFAULT_REQUEST, moneyObserver);
    Throwable actualError = actualErrorF.get(500, TimeUnit.MILLISECONDS);

    Truth.assertThat(actualError).isInstanceOf(StatusRuntimeException.class);
    Truth.assertThat(((StatusRuntimeException) actualError).getStatus().getCode())
        .isEqualTo(Status.CANCELLED.getCode());
    Truth.assertThat(actualError.getCause()).isSameAs(expectedCause);
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

  private static class MoneyObserver implements ResponseObserver<Money> {
    private final boolean autoFlowControl;
    private final CountDownLatch latch;

    volatile StreamController controller;
    volatile Money response;
    volatile Throwable error;
    volatile boolean completed;

    MoneyObserver(boolean autoFlowControl, CountDownLatch latch) {
      this.autoFlowControl = autoFlowControl;
      this.latch = latch;
    }

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;
      if (!autoFlowControl) {
        controller.disableAutoInboundFlowControl();
      }
    }

    @Override
    public void onResponse(Money value) {
      response = value;
      latch.countDown();
    }

    @Override
    public void onError(Throwable t) {
      error = t;
      latch.countDown();
    }

    @Override
    public void onComplete() {
      completed = true;
      latch.countDown();
    }
  }
}
