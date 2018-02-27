/*
 * Copyright 2017 Google LLC
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
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class ChannelPoolTest {
  @Test
  public void testAuthority() {
    ManagedChannel sub1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel sub2 = Mockito.mock(ManagedChannel.class);

    Mockito.when(sub1.authority()).thenReturn("myAuth");

    ChannelPool pool = new ChannelPool(Lists.newArrayList(sub1, sub2));
    Truth.assertThat(pool.authority()).isEqualTo("myAuth");
  }

  @Test
  public void testRoundRobin() {
    ManagedChannel sub1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel sub2 = Mockito.mock(ManagedChannel.class);
    Mockito.when(sub1.authority()).thenReturn("myAuth");

    ArrayList<ManagedChannel> channels = Lists.newArrayList(sub1, sub2);
    ChannelPool pool = new ChannelPool(channels);

    verifyTargetChannel(pool, channels, sub1);
    verifyTargetChannel(pool, channels, sub2);
    verifyTargetChannel(pool, channels, sub1);
  }

  private void verifyTargetChannel(
      ChannelPool pool, List<ManagedChannel> channels, ManagedChannel targetChannel) {
    MethodDescriptor<Color, Money> methodDescriptor = FakeServiceGrpc.METHOD_RECOGNIZE;
    CallOptions callOptions = CallOptions.DEFAULT;
    @SuppressWarnings("unchecked")
    ClientCall<Color, Money> expectedClientCall = Mockito.mock(ClientCall.class);

    for (ManagedChannel channel : channels) {
      Mockito.reset(channel);
    }
    Mockito.doReturn(expectedClientCall).when(targetChannel).newCall(methodDescriptor, callOptions);

    ClientCall<Color, Money> actualCall = pool.newCall(methodDescriptor, callOptions);

    Truth.assertThat(actualCall).isSameAs(expectedClientCall);
    Mockito.verify(targetChannel, Mockito.times(1)).newCall(methodDescriptor, callOptions);

    for (ManagedChannel otherChannel : channels) {
      if (otherChannel != targetChannel) {
        Mockito.verify(otherChannel, Mockito.never()).newCall(methodDescriptor, callOptions);
      }
    }
  }

  @Test
  public void ensureEvenDistribution() throws InterruptedException {
    int numChannels = 10;
    final ManagedChannel[] channels = new ManagedChannel[numChannels];
    final AtomicInteger[] counts = new AtomicInteger[numChannels];

    final MethodDescriptor<Color, Money> methodDescriptor = FakeServiceGrpc.METHOD_RECOGNIZE;
    final CallOptions callOptions = CallOptions.DEFAULT;
    @SuppressWarnings("unchecked")
    final ClientCall<Color, Money> clientCall = Mockito.mock(ClientCall.class);

    for (int i = 0; i < numChannels; i++) {
      final int index = i;

      counts[i] = new AtomicInteger();

      channels[i] = Mockito.mock(ManagedChannel.class);
      Mockito.when(channels[i].newCall(methodDescriptor, callOptions))
          .thenAnswer(
              new Answer<ClientCall<Color, Money>>() {
                @Override
                public ClientCall<Color, Money> answer(InvocationOnMock invocationOnMock)
                    throws Throwable {
                  counts[index].incrementAndGet();
                  return clientCall;
                }
              });
    }

    final ChannelPool pool = new ChannelPool(Arrays.asList(channels));

    int numThreads = 20;
    final int numPerThread = 1000;

    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    for (int i = 0; i < numThreads; i++) {
      executor.submit(
          new Runnable() {
            @Override
            public void run() {
              for (int j = 0; j < numPerThread; j++) {
                pool.newCall(methodDescriptor, callOptions);
              }
            }
          });
    }
    executor.shutdown();
    boolean shutdown = executor.awaitTermination(1, TimeUnit.MINUTES);
    Truth.assertThat(shutdown).isTrue();

    int expectedCount = (numThreads * numPerThread) / numChannels;
    for (AtomicInteger count : counts) {
      Truth.assertThat(count.get()).isAnyOf(expectedCount, expectedCount + 1);
    }
  }
}
