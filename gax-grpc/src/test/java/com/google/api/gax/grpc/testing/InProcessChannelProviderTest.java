/*
 * Copyright 2016 Google LLC
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
package com.google.api.gax.grpc.testing;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.grpc.testing.FakeServiceGrpc.FakeServiceImplBase;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InProcessChannelProviderTest {
  private static final String CHANNEL_NAME = "example.com:123";
  private static final Money DEFAULT_RESPONSE =
      Money.newBuilder().setCurrencyCode("USD").setNanos(123).build();

  private InProcessServer<?> server;
  private GrpcTransportChannel transportChannel;

  @Before
  public void setUp() throws Exception {
    FakeServiceGrpc.FakeServiceImplBase serviceImpl =
        new FakeServiceImplBase() {
          @Override
          public void recognize(Color request, StreamObserver<Money> responseObserver) {
            responseObserver.onNext(DEFAULT_RESPONSE);
            responseObserver.onCompleted();
          }
        };

    server = new InProcessServer<>(serviceImpl, CHANNEL_NAME);
    server.start();

    ExecutorProvider executorProvider =
        FixedExecutorProvider.create(Executors.newScheduledThreadPool(1));

    InstantiatingGrpcChannelProvider provider =
        InProcessChannelProvider.newBuilder()
            .setEndpoint(CHANNEL_NAME)
            .setExecutorProvider(executorProvider)
            .setHeaderProvider(FixedHeaderProvider.create())
            .build();
    transportChannel = (GrpcTransportChannel) provider.getTransportChannel();
  }

  @After
  public void tearDown() throws Exception {
    transportChannel.close();
    transportChannel.awaitTermination(1, TimeUnit.MINUTES);

    server.stop();
    server.blockUntilShutdown();
  }

  @Test
  public void test() throws Exception {
    Channel channel = transportChannel.getChannel();
    ClientCall<Color, Money> call =
        channel.newCall(FakeServiceGrpc.METHOD_RECOGNIZE, CallOptions.DEFAULT);

    final SettableApiFuture<Money> result = SettableApiFuture.create();

    call.start(
        new Listener<Money>() {
          @Override
          public void onMessage(Money message) {
            result.set(message);
          }

          @Override
          public void onClose(Status status, Metadata trailers) {
            if (!status.isOk()) {
              result.setException(status.asRuntimeException());
            }
          }
        },
        new Metadata());

    call.sendMessage(Color.getDefaultInstance());
    call.halfClose();
    call.request(1);

    Truth.assertThat(result.get(1, TimeUnit.MINUTES)).isEqualTo(DEFAULT_RESPONSE);
  }
}
