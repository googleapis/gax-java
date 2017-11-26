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
import com.google.api.gax.grpc.testing.InProcessServer;
import com.google.api.gax.grpc.testing.InstrumentedFakeServiceImpl;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStream;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StreamController;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GrpcDirectServerStreamingCallableTest {
  private InProcessServer<InstrumentedFakeServiceImpl> inprocessServer;
  private ManagedChannel channel;
  private InstrumentedFakeServiceImpl serviceImpl;
  private ClientContext clientContext;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() throws InstantiationException, IllegalAccessException, IOException {
    // Setup the fake environment
    String serverName = "fakeservice";
    serviceImpl = new InstrumentedFakeServiceImpl();
    inprocessServer = new InProcessServer<>(serviceImpl, serverName);
    inprocessServer.start();
    channel =
        InProcessChannelBuilder.forName(serverName).directExecutor().usePlaintext(true).build();
    clientContext =
        ClientContext.newBuilder()
            .setTransportChannel(GrpcTransportChannel.create(channel))
            .setDefaultCallContext(GrpcCallContext.of(channel, CallOptions.DEFAULT))
            .build();
  }

  @After
  public void tearDown() {
    channel.shutdown();
    inprocessServer.stop();
  }

  @Test
  public void testServerStreaming() throws Exception {
    ServerStreamingCallable<Color, Money> streamingCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE), null, clientContext);

    CountDownLatch latch = new CountDownLatch(1);
    MoneyResponseObserver moneyObserver = new MoneyResponseObserver(true, latch);

    Color request = Color.newBuilder().setRed(0.5f).build();
    streamingCallable.call(request, moneyObserver);

    serviceImpl.awaitNextRequest();
    Money response = Money.newBuilder().setCurrencyCode("USD").setUnits(127).build();
    serviceImpl.getResponseStream().onNext(response);

    latch.await(20, TimeUnit.SECONDS);
    Truth.assertThat(moneyObserver.error).isNull();
    Truth.assertThat(moneyObserver.response).isEqualTo(response);
  }

  @Test
  public void testManualFlowControl() throws Exception {
    ServerStreamingCallable<Color, Money> streamingCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE), null, clientContext);

    CountDownLatch latch = new CountDownLatch(1);
    MoneyResponseObserver moneyObserver = new MoneyResponseObserver(false, latch);

    streamingCallable.call(Color.getDefaultInstance(), moneyObserver);
    serviceImpl.awaitNextRequest();
    serviceImpl.getResponseStream().onNext(Money.getDefaultInstance());

    Assert.assertFalse(
        "Received response before requesting it", latch.await(100, TimeUnit.MILLISECONDS));

    moneyObserver.controller.request(1);

    Assert.assertTrue(
        "Timed out waiting for requested response", latch.await(100, TimeUnit.MILLISECONDS));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testAutoFlowControl() throws Exception {
    ServerStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE), null, clientContext);

    CountDownLatch latch = new CountDownLatch(3);
    MoneyResponseObserver moneyObserver = new MoneyResponseObserver(true, latch);

    // Start the call and get the listener
    callable.call(Color.getDefaultInstance(), moneyObserver);
    serviceImpl.awaitNextRequest();
    serviceImpl.getResponseStream().onNext(Money.getDefaultInstance());
    serviceImpl.getResponseStream().onNext(Money.getDefaultInstance());
    serviceImpl.getResponseStream().onCompleted();

    // Request for the initial response.
    latch.await(1, TimeUnit.SECONDS);
  }

  @Test
  public void testCancelClientCall() throws Throwable {
    // Stub the creation of the ClientCall
    ServerStreamingCallable<Color, Money> callable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE), null, clientContext);

    CountDownLatch latch = new CountDownLatch(2);
    MoneyResponseObserver moneyObserver = new MoneyResponseObserver(true, latch);

    callable.call(Color.getDefaultInstance(), moneyObserver);
    serviceImpl.awaitNextRequest();
    serviceImpl.getResponseStream().onNext(Money.getDefaultInstance());

    moneyObserver.controller.cancel();

    thrown.expect(StatusRuntimeException.class);
    thrown.expectMessage("CANCELLED");

    try {
      moneyObserver.doneFuture.get();
    } catch (ExecutionException e) {
      throw e.getCause();
    }
  }

  @Test
  public void testOnMessageErrorNotifiesOnError() throws Throwable {
    ServerStreamingCallable<Color, Money> streamingCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE), null, clientContext);

    CountDownLatch latch = new CountDownLatch(1);
    MoneyResponseObserver observer = new MoneyResponseObserver(true, latch);

    streamingCallable.call(Color.getDefaultInstance(), observer);
    serviceImpl.awaitNextRequest();
    serviceImpl
        .getResponseStream()
        .onError(new StatusRuntimeException(Status.INTERNAL.withDescription("fake")));

    thrown.expectMessage(CoreMatchers.containsString("fake"));
    observer.doneFuture.get();
  }

  @Test
  public void testObserverErrorCancelsCall() throws Throwable {
    ServerStreamingCallable<Color, Money> streamingCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE), null, clientContext);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicInteger responseCount = new AtomicInteger();
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final RuntimeException expectedCause = new RuntimeException("unexpected error");

    ResponseObserver<Money> observer =
        new ResponseObserver<Money>() {
          @Override
          public void onStart(StreamController controller) {}

          @Override
          public void onResponse(Money response) {
            responseCount.incrementAndGet();
            throw expectedCause;
          }

          @Override
          public void onError(Throwable t) {
            error.set(t);
            latch.countDown();
          }

          @Override
          public void onComplete() {
            Assert.fail("A broken ResponseObserver should not complete normally");
          }
        };

    streamingCallable.call(Color.getDefaultInstance(), observer);
    serviceImpl.awaitNextRequest();
    serviceImpl.getResponseStream().onNext(Money.getDefaultInstance());

    latch.await(10, TimeUnit.SECONDS);

    Assert.assertNotNull("Observer should be notified with the error", error.get());
    Assert.assertTrue(
        "The error should be a StatusRuntimeException",
        error.get() instanceof StatusRuntimeException);
    Assert.assertEquals(
        Code.CANCELLED, ((StatusRuntimeException) error.get()).getStatus().getCode());
    Assert.assertEquals(
        expectedCause, ((StatusRuntimeException) error.get()).getStatus().getCause());

    Assert.assertTrue(
        "The server rpc should be cancelled",
        ((ServerCallStreamObserver) serviceImpl.getResponseStream()).isCancelled());
  }

  @Test
  public void testBlockingServerStreaming() throws Exception {
    ServerStreamingCallable<Color, Money> streamingCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            GrpcCallSettings.create(METHOD_SERVER_STREAMING_RECOGNIZE), null, clientContext);

    ServerStream<Money> response = streamingCallable.call(Color.getDefaultInstance());
    serviceImpl.awaitNextRequest();

    // send the responses
    List<Money> expected =
        Lists.newArrayList(
            Money.newBuilder().setCurrencyCode("USD").setUnits(1).build(),
            Money.newBuilder().setCurrencyCode("USD").setUnits(2).build());
    for (Money result : expected) {
      serviceImpl.getResponseStream().onNext(result);
    }
    serviceImpl.getResponseStream().onCompleted();

    Truth.assertThat(response).containsExactlyElementsIn(expected);
  }

  private static class MoneyResponseObserver implements ResponseObserver<Money> {
    volatile Money response;
    volatile Throwable error;
    volatile boolean completed;
    volatile StreamController controller;
    private boolean autoFlowControl;
    CountDownLatch latch;
    SettableApiFuture<StreamController> startedFuture = SettableApiFuture.create();
    BlockingDeque<Money> responses = Queues.newLinkedBlockingDeque();
    SettableApiFuture<Void> doneFuture = SettableApiFuture.create();

    MoneyResponseObserver(boolean autoFlowControl, CountDownLatch latch) {
      this.autoFlowControl = autoFlowControl;
      this.latch = latch;
    }

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;
      if (!autoFlowControl) {
        controller.disableAutoInboundFlowControl();
      }
      startedFuture.set(controller);
    }

    @Override
    public void onResponse(Money value) {
      response = value;
      responses.addLast(value);
      latch.countDown();
    }

    @Override
    public void onError(Throwable t) {
      error = t;
      latch.countDown();
      doneFuture.setException(t);
    }

    @Override
    public void onComplete() {
      completed = true;
      doneFuture.set(null);
    }
  }
}
