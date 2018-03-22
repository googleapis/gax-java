/*
 * Copyright 2018 Google LLC
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

import com.google.api.gax.grpc.testing.FakeServiceGrpc;
import com.google.api.gax.grpc.testing.FakeServiceGrpc.FakeServiceImplBase;
import com.google.api.gax.grpc.testing.InProcessServer;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class GrpcResponseMetadataTest {

  private static final String HEADER_KEY = "inprocessheaderkey";
  private static final String HEADER_VALUE = "inprocessheadervalue";
  private static final String TRAILER_KEY = "inprocesstrailerkey";
  private static final String TRAILER_VALUE = "inprocesstrailervalue";

  private InProcessServer<FakeServiceImplBase> inprocessServer;
  private ManagedChannel channel;
  private ClientContext clientContext;

  private Metadata requestHeaders = null;

  @Before
  public void setUp() throws Exception {
    String serverName = "fakeservice";
    FakeServiceImplBase serviceImpl = Mockito.mock(FakeServiceImplBase.class);
    Mockito.doAnswer(
            new Answer<Void>() {
              @Override
              public Void answer(InvocationOnMock invocation) {
                Color color = invocation.getArgumentAt(0, Color.class);
                StreamObserver<Money> observer = invocation.getArgumentAt(1, StreamObserver.class);
                observer.onNext(
                    Money.newBuilder()
                        .setCurrencyCode("USD")
                        .setUnits((long) (color.getRed() * 255))
                        .build());
                observer.onCompleted();
                return null;
              }
            })
        .when(serviceImpl)
        .recognize(Mockito.<Color>any(), Mockito.<StreamObserver<Money>>any());
    requestHeaders = null;
    inprocessServer =
        new InProcessServer<>(
            serviceImpl,
            serverName,
            new ServerInterceptor() {
              @Override
              public <ReqT, RespT> Listener<ReqT> interceptCall(
                  ServerCall<ReqT, RespT> call,
                  Metadata headers,
                  ServerCallHandler<ReqT, RespT> next) {
                requestHeaders = headers;
                return next.startCall(
                    new SimpleForwardingServerCall<ReqT, RespT>(call) {
                      @Override
                      public void sendHeaders(Metadata responseHeaders) {
                        responseHeaders.put(
                            Key.of(HEADER_KEY, Metadata.ASCII_STRING_MARSHALLER), HEADER_VALUE);
                        super.sendHeaders(responseHeaders);
                      }

                      @Override
                      public void close(Status status, Metadata trailers) {
                        trailers.put(
                            Key.of(TRAILER_KEY, Metadata.ASCII_STRING_MARSHALLER), TRAILER_VALUE);
                        super.close(status, trailers);
                      }
                    },
                    headers);
              }
            });
    inprocessServer.start();

    channel =
        InProcessChannelBuilder.forName(serverName)
            .directExecutor()
            .usePlaintext(true)
            .intercept(new GrpcMetadataHandlerInterceptor())
            .build();
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
  public void testResponseMetadataUnaryCall() throws Exception {
    GrpcCallSettings<Color, Money> grpcCallSettings =
        GrpcCallSettings.create(FakeServiceGrpc.METHOD_RECOGNIZE);

    UnaryCallSettings<Color, Money> callSettings =
        UnaryCallSettings.<Color, Money>newUnaryCallSettingsBuilder().build();

    UnaryCallable<Color, Money> callable =
        GrpcCallableFactory.createUnaryCallable(grpcCallSettings, callSettings, clientContext);

    Assert.assertNull(requestHeaders);

    GrpcResponseMetadata responseMetadata = new GrpcResponseMetadata();
    callable.call(Color.getDefaultInstance(), responseMetadata.createContextWithHandlers());

    Assert.assertNotNull(requestHeaders);

    Metadata metadata = responseMetadata.getMetadata();
    Metadata trailingMetadata = responseMetadata.getTrailingMetadata();

    Assert.assertEquals(
        metadata.get(Key.of(HEADER_KEY, Metadata.ASCII_STRING_MARSHALLER)), HEADER_VALUE);
    Assert.assertEquals(
        trailingMetadata.get(Key.of(TRAILER_KEY, Metadata.ASCII_STRING_MARSHALLER)), TRAILER_VALUE);
  }
}
