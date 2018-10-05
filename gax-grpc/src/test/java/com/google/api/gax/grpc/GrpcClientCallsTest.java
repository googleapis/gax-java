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
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.threeten.bp.Duration;

public class GrpcClientCallsTest {
  @Test
  public void testAffinity() {
    MethodDescriptor<Color, Money> descriptor = FakeServiceGrpc.METHOD_RECOGNIZE;

    @SuppressWarnings("unchecked")
    ClientCall<Color, Money> clientCall0 = Mockito.mock(ClientCall.class);

    @SuppressWarnings("unchecked")
    ClientCall<Color, Money> clientCall1 = Mockito.mock(ClientCall.class);

    ManagedChannel channel0 = Mockito.mock(ManagedChannel.class);
    ManagedChannel channel1 = Mockito.mock(ManagedChannel.class);

    Mockito.when(channel0.newCall(Mockito.eq(descriptor), Mockito.<CallOptions>any()))
        .thenReturn(clientCall0);
    Mockito.when(channel1.newCall(Mockito.eq(descriptor), Mockito.<CallOptions>any()))
        .thenReturn(clientCall1);

    Channel pool = new ChannelPool(Arrays.asList(channel0, channel1));
    GrpcCallContext context = GrpcCallContext.createDefault().withChannel(pool);

    ClientCall<Color, Money> gotCallA =
        GrpcClientCalls.newCall(descriptor, context.withChannelAffinity(0));
    ClientCall<Color, Money> gotCallB =
        GrpcClientCalls.newCall(descriptor, context.withChannelAffinity(0));
    ClientCall<Color, Money> gotCallC =
        GrpcClientCalls.newCall(descriptor, context.withChannelAffinity(1));

    assertThat(gotCallA).isSameAs(gotCallB);
    assertThat(gotCallA).isNotSameAs(gotCallC);
  }

  @Test
  public void testExtraHeaders() {
    Metadata emptyHeaders = new Metadata();
    final Map<String, List<String>> extraHeaders = new HashMap<>();
    extraHeaders.put(
        "header-key-1", ImmutableList.<String>of("header-value-11", "header-value-12"));
    extraHeaders.put("header-key-2", ImmutableList.<String>of("header-value-21"));

    MethodDescriptor<Color, Money> descriptor = FakeServiceGrpc.METHOD_RECOGNIZE;

    @SuppressWarnings("unchecked")
    ClientCall<Color, Money> mockClientCall = Mockito.mock(ClientCall.class);

    @SuppressWarnings("unchecked")
    ClientCall.Listener<Money> mockListener = Mockito.mock(ClientCall.Listener.class);

    @SuppressWarnings("unchecked")
    Channel mockChannel = Mockito.mock(ManagedChannel.class);

    Mockito.doAnswer(
            new Answer<Void>() {
              public Void answer(InvocationOnMock invocation) {
                Metadata clientCallHeaders = (Metadata) invocation.getArguments()[1];
                Metadata.Key<String> key1 =
                    Metadata.Key.of("header-key-1", Metadata.ASCII_STRING_MARSHALLER);
                Metadata.Key<String> key2 =
                    Metadata.Key.of("header-key-2", Metadata.ASCII_STRING_MARSHALLER);
                assertThat(clientCallHeaders.getAll(key1))
                    .containsExactly("header-value-11", "header-value-12");
                assertThat(clientCallHeaders.getAll(key2)).containsExactly("header-value-21");
                return null;
              }
            })
        .when(mockClientCall)
        .start(Mockito.<ClientCall.Listener<Money>>any(), Mockito.<Metadata>any());

    Mockito.when(mockChannel.newCall(Mockito.eq(descriptor), Mockito.<CallOptions>any()))
        .thenReturn(mockClientCall);

    GrpcCallContext context =
        GrpcCallContext.createDefault().withChannel(mockChannel).withExtraHeaders(extraHeaders);
    GrpcClientCalls.newCall(descriptor, context).start(mockListener, emptyHeaders);
  }

  @Test
  public void testTimeoutToDeadlineConversion() {
    MethodDescriptor<Color, Money> descriptor = FakeServiceGrpc.METHOD_RECOGNIZE;

    @SuppressWarnings("unchecked")
    ClientCall<Color, Money> mockClientCall = Mockito.mock(ClientCall.class);

    @SuppressWarnings("unchecked")
    ClientCall.Listener<Money> mockListener = Mockito.mock(ClientCall.Listener.class);

    @SuppressWarnings("unchecked")
    Channel mockChannel = Mockito.mock(ManagedChannel.class);

    ArgumentCaptor<CallOptions> capturedCallOptions = ArgumentCaptor.forClass(CallOptions.class);

    Mockito.when(mockChannel.newCall(Mockito.eq(descriptor), capturedCallOptions.capture()))
        .thenReturn(mockClientCall);

    Duration timeout = Duration.ofSeconds(10);
    Deadline minExpectedDeadline = Deadline.after(timeout.getSeconds(), TimeUnit.SECONDS);

    GrpcCallContext context =
        GrpcCallContext.createDefault().withChannel(mockChannel).withTimeout(timeout);

    GrpcClientCalls.newCall(descriptor, context).start(mockListener, new Metadata());

    Deadline maxExpectedDeadline = Deadline.after(timeout.getSeconds(), TimeUnit.SECONDS);

    Truth.assertThat(capturedCallOptions.getValue().getDeadline()).isAtLeast(minExpectedDeadline);
    Truth.assertThat(capturedCallOptions.getValue().getDeadline()).isAtMost(maxExpectedDeadline);
  }

  @Test
  public void testTimeoutAfterDeadline() {
    MethodDescriptor<Color, Money> descriptor = FakeServiceGrpc.METHOD_RECOGNIZE;

    @SuppressWarnings("unchecked")
    ClientCall<Color, Money> mockClientCall = Mockito.mock(ClientCall.class);

    @SuppressWarnings("unchecked")
    ClientCall.Listener<Money> mockListener = Mockito.mock(ClientCall.Listener.class);

    @SuppressWarnings("unchecked")
    Channel mockChannel = Mockito.mock(ManagedChannel.class);

    ArgumentCaptor<CallOptions> capturedCallOptions = ArgumentCaptor.forClass(CallOptions.class);

    Mockito.when(mockChannel.newCall(Mockito.eq(descriptor), capturedCallOptions.capture()))
        .thenReturn(mockClientCall);

    // Configure a timeout that occurs after the grpc deadline
    Deadline priorDeadline = Deadline.after(5, TimeUnit.SECONDS);
    Duration timeout = Duration.ofSeconds(10);

    GrpcCallContext context =
        GrpcCallContext.createDefault()
            .withChannel(mockChannel)
            .withCallOptions(CallOptions.DEFAULT.withDeadline(priorDeadline))
            .withTimeout(timeout);

    GrpcClientCalls.newCall(descriptor, context).start(mockListener, new Metadata());

    // Verify that the timeout is ignored
    Truth.assertThat(capturedCallOptions.getValue().getDeadline()).isEqualTo(priorDeadline);
  }

  @Test
  public void testTimeoutBeforeDeadline() {
    MethodDescriptor<Color, Money> descriptor = FakeServiceGrpc.METHOD_RECOGNIZE;

    @SuppressWarnings("unchecked")
    ClientCall<Color, Money> mockClientCall = Mockito.mock(ClientCall.class);

    @SuppressWarnings("unchecked")
    ClientCall.Listener<Money> mockListener = Mockito.mock(ClientCall.Listener.class);

    @SuppressWarnings("unchecked")
    Channel mockChannel = Mockito.mock(ManagedChannel.class);

    ArgumentCaptor<CallOptions> capturedCallOptions = ArgumentCaptor.forClass(CallOptions.class);

    Mockito.when(mockChannel.newCall(Mockito.eq(descriptor), capturedCallOptions.capture()))
        .thenReturn(mockClientCall);

    // Configure a timeout that occurs before the grpc deadline
    Duration timeout = Duration.ofSeconds(5);
    Deadline subsequentDeadline = Deadline.after(10, TimeUnit.SECONDS);
    Deadline minExpectedDeadline = Deadline.after(timeout.getSeconds(), TimeUnit.SECONDS);

    GrpcCallContext context =
        GrpcCallContext.createDefault()
            .withChannel(mockChannel)
            .withCallOptions(CallOptions.DEFAULT.withDeadline(subsequentDeadline))
            .withTimeout(timeout);

    GrpcClientCalls.newCall(descriptor, context).start(mockListener, new Metadata());

    // Verify that the timeout is ignored
    Deadline maxExpectedDeadline = Deadline.after(timeout.getSeconds(), TimeUnit.SECONDS);

    Truth.assertThat(capturedCallOptions.getValue().getDeadline()).isAtLeast(minExpectedDeadline);
    Truth.assertThat(capturedCallOptions.getValue().getDeadline()).isAtMost(maxExpectedDeadline);
  }
}
