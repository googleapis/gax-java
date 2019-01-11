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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.grpc.testing.FakeServiceGrpc;
import com.google.api.gax.grpc.testing.FakeServiceImpl;
import com.google.api.gax.grpc.testing.InProcessServer;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.api.gax.rpc.ServerStreamingCallSettings;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.tracing.SpanName;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.inprocess.InProcessChannelBuilder;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class GrpcCallableFactoryTest {
  private InProcessServer<FakeServiceImpl> inprocessServer;
  private ManagedChannel channel;
  private ClientContext clientContext;

  @Before
  public void setUp() throws Exception {
    String serverName = "fakeservice";
    FakeServiceImpl serviceImpl = new FakeServiceImpl();
    inprocessServer = new InProcessServer<>(serviceImpl, serverName);
    inprocessServer.start();

    channel = InProcessChannelBuilder.forName(serverName).directExecutor().usePlaintext().build();
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
  public void createServerStreamingCallableRetryableExceptions() throws Exception {
    GrpcCallSettings<Color, Money> grpcCallSettings =
        GrpcCallSettings.create(FakeServiceGrpc.METHOD_STREAMING_RECOGNIZE_ERROR);

    // Base case: without config, invalid argument errors are not retryable.
    ServerStreamingCallSettings<Color, Money> nonRetryableSettings =
        ServerStreamingCallSettings.<Color, Money>newBuilder()
            .setRetrySettings(
                RetrySettings.newBuilder()
                    .setTotalTimeout(Duration.ofSeconds(1))
                    .setMaxAttempts(1)
                    .build())
            .build();

    ServerStreamingCallable<Color, Money> nonRetryableCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            grpcCallSettings, nonRetryableSettings, clientContext);

    Throwable actualError = null;
    try {
      nonRetryableCallable
          .first()
          .call(Color.getDefaultInstance(), clientContext.getDefaultCallContext());
    } catch (Throwable e) {
      actualError = e;
    }
    assertThat(actualError).isInstanceOf(InvalidArgumentException.class);
    assertThat(((InvalidArgumentException) actualError).isRetryable()).isFalse();

    // Actual test: with config, invalid argument errors are retryable.
    ServerStreamingCallSettings<Color, Money> retryableSettings =
        ServerStreamingCallSettings.<Color, Money>newBuilder()
            .setRetryableCodes(Code.INVALID_ARGUMENT)
            .setRetrySettings(
                RetrySettings.newBuilder()
                    .setTotalTimeout(Duration.ofSeconds(1))
                    .setMaxAttempts(1)
                    .build())
            .build();

    ServerStreamingCallable<Color, Money> retryableCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            grpcCallSettings, retryableSettings, clientContext);

    Throwable actualError2 = null;
    try {
      retryableCallable
          .first()
          .call(Color.getDefaultInstance(), clientContext.getDefaultCallContext());
    } catch (Throwable e) {
      actualError2 = e;
    }
    assertThat(actualError2).isInstanceOf(InvalidArgumentException.class);
    assertThat(((InvalidArgumentException) actualError2).isRetryable()).isTrue();
  }

  @Test
  public void testGetSpanName() {
    @SuppressWarnings("unchecked")
    MethodDescriptor descriptor =
        MethodDescriptor.newBuilder()
            .setType(MethodType.SERVER_STREAMING)
            .setFullMethodName("google.bigtable.v2.Bigtable/ReadRows")
            .setRequestMarshaller(Mockito.mock(Marshaller.class))
            .setResponseMarshaller(Mockito.mock(Marshaller.class))
            .build();

    SpanName actualSpanName = GrpcCallableFactory.getSpanName(descriptor);
    assertThat(actualSpanName).isEqualTo(SpanName.of("Bigtable", "ReadRows"));
  }

  @Test
  public void testGetSpanNameUnqualified() {
    @SuppressWarnings("unchecked")
    MethodDescriptor descriptor =
        MethodDescriptor.newBuilder()
            .setType(MethodType.SERVER_STREAMING)
            .setFullMethodName("UnqualifiedService/ReadRows")
            .setRequestMarshaller(Mockito.mock(Marshaller.class))
            .setResponseMarshaller(Mockito.mock(Marshaller.class))
            .build();

    SpanName actualSpanName = GrpcCallableFactory.getSpanName(descriptor);
    assertThat(actualSpanName).isEqualTo(SpanName.of("UnqualifiedService", "ReadRows"));
  }

  @Test
  public void testGetSpanNameInvalid() {
    List<String> invalidNames = ImmutableList.of("BareMethod", "/MethodWithoutService");

    for (String invalidName : invalidNames) {
      @SuppressWarnings("unchecked")
      MethodDescriptor descriptor =
          MethodDescriptor.newBuilder()
              .setType(MethodType.SERVER_STREAMING)
              .setFullMethodName(invalidName)
              .setRequestMarshaller(Mockito.mock(Marshaller.class))
              .setResponseMarshaller(Mockito.mock(Marshaller.class))
              .build();

      IllegalArgumentException actualError = null;
      try {
        SpanName spanName = GrpcCallableFactory.getSpanName(descriptor);
        Truth.assertWithMessage("Invalid method descriptor should not have a valid span name")
            .fail("%s should not generate the spanName: %s", invalidName, spanName);
      } catch (IllegalArgumentException e) {
        actualError = e;
      }

      assertThat(actualError).isNotNull();
    }
  }
}
