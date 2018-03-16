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
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import java.util.Arrays;
import org.junit.Test;
import org.mockito.Mockito;

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
}
