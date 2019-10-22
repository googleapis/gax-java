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

import static org.mockito.ArgumentMatchers.any;

import com.google.api.gax.grpc.testing.FakeChannelFactory;
import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
import com.google.api.gax.grpc.testing.FakeServiceGrpc;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class ChannelPoolTest {
  @Test
  public void testAuthority() throws IOException {
    ManagedChannel sub1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel sub2 = Mockito.mock(ManagedChannel.class);

    Mockito.when(sub1.authority()).thenReturn("myAuth");

    ChannelPool pool = new ChannelPool(2, new FakeChannelFactory(Arrays.asList(sub1, sub2)), null);
    Truth.assertThat(pool.authority()).isEqualTo("myAuth");
  }

  @Test
  public void testRoundRobin() throws IOException {
    ManagedChannel sub1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel sub2 = Mockito.mock(ManagedChannel.class);

    Mockito.when(sub1.authority()).thenReturn("myAuth");

    ArrayList<ManagedChannel> channels = Lists.newArrayList(sub1, sub2);
    ChannelPool pool = new ChannelPool(2, new FakeChannelFactory(Arrays.asList(sub1, sub2)), null);

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

    Truth.assertThat(actualCall).isSameInstanceAs(expectedClientCall);
    Mockito.verify(targetChannel, Mockito.times(1)).newCall(methodDescriptor, callOptions);

    for (ManagedChannel otherChannel : channels) {
      if (otherChannel != targetChannel) {
        Mockito.verify(otherChannel, Mockito.never()).newCall(methodDescriptor, callOptions);
      }
    }
  }

  @Test
  public void ensureEvenDistribution() throws InterruptedException, IOException {
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

    final ChannelPool pool =
        new ChannelPool(numChannels, new FakeChannelFactory(Arrays.asList(channels)), null);

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

  // Test channelPrimer is called same number of times as poolSize if executorService is set to null
  @Test
  public void channelPrimerIsCalled() throws IOException {
    ChannelPrimer mockChannelPrimer = Mockito.mock(ChannelPrimer.class);
    ManagedChannel channel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel channel2 = Mockito.mock(ManagedChannel.class);

    new ChannelPool(
        2, new FakeChannelFactory(Arrays.asList(channel1, channel2), mockChannelPrimer), null);
    Mockito.verify(mockChannelPrimer, Mockito.times(2))
        .primeChannel(Mockito.any(ManagedChannel.class));
  }

  // Test channelPrimer is called periodically, if there's an executorService
  @Test
  public void channelPrimerIsCalledPeriodically() throws IOException {
    ChannelPrimer mockChannelPrimer = Mockito.mock(ChannelPrimer.class);
    ManagedChannel channel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel channel2 = Mockito.mock(ManagedChannel.class);
    ManagedChannel channel3 = Mockito.mock(ManagedChannel.class);
    ManagedChannel channel4 = Mockito.mock(ManagedChannel.class);

    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);

    final List<Runnable> channelRefreshers = new ArrayList<>();

    // mock scheduleAtFixRate to invoke periodic refresh function to verify channelPrimer is called
    Mockito.when(
            scheduledExecutorService.scheduleAtFixedRate(
                Mockito.any(Runnable.class),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.eq(TimeUnit.SECONDS)))
        .thenAnswer(
            new Answer() {
              public Object answer(InvocationOnMock invocation) {
                channelRefreshers.add((Runnable) invocation.getArgument(0));
                return null;
              }
            });
    // 4 channels will be created, 2 during the initial creation, 2 more during the invocation of
    //  periodic refresh
    new ChannelPool(
        2,
        new FakeChannelFactory(
            Arrays.asList(channel1, channel2, channel3, channel4), mockChannelPrimer),
        scheduledExecutorService);
    // 2 calls during the creation
    Mockito.verify(mockChannelPrimer, Mockito.times(2))
        .primeChannel(Mockito.any(ManagedChannel.class));
    Mockito.verify(scheduledExecutorService, Mockito.times(2))
        .scheduleAtFixedRate(
            Mockito.any(Runnable.class),
            Mockito.anyLong(),
            Mockito.anyLong(),
            Mockito.eq(TimeUnit.SECONDS));

    Truth.assertThat(channelRefreshers).hasSize(2);
    for (Runnable channelRefresher : channelRefreshers) {
      channelRefresher.run();
    }
    // 2 more calls during channel refresh
    Mockito.verify(mockChannelPrimer, Mockito.times(4))
        .primeChannel(Mockito.any(ManagedChannel.class));
  }

  // Test that the old channel that's being swapped out will not be shutdown as long as there are

  // Test the concurrency problem of if a call starts on an "old channel" and a refresh happens,
  // all the calls (started or not) will be allowed to continue to complete.
  @Test
  public void channelRefreshDoesNotCancelCalls() throws IOException {
    ManagedChannel channel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel channel2 = Mockito.mock(ManagedChannel.class);

    final boolean[] channelsIsShutDown = {false};

    // when shutdown is called
    Mockito.doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) {
                channelsIsShutDown[0] = true;
                return null;
              }
            })
        .when(channel1)
        .shutdown();

    MockClientCall<String, Integer> mockClientCall = new MockClientCall<>(1, Status.OK);
    final ClientCall<String, Integer> channel1ClientCall = Mockito.spy(mockClientCall);
    // ensure that when the client call starts, the underlying channel has not shutdown
    Mockito.doAnswer(
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                Truth.assertThat(channelsIsShutDown[0]).isFalse();
                return invocation.callRealMethod();
              }
            })
        .when(channel1ClientCall)
        .start(Mockito.any(ClientCall.Listener.class), Mockito.any(Metadata.class));

    Mockito.when(
            channel1.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), any(CallOptions.class)))
        .thenReturn(channel1ClientCall);

    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);

    final List<Runnable> channelRefreshers = new ArrayList<>();

    Mockito.when(
            scheduledExecutorService.scheduleAtFixedRate(
                Mockito.any(Runnable.class),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.eq(TimeUnit.SECONDS)))
        .thenAnswer(
            new Answer() {
              public Object answer(InvocationOnMock invocation) {
                channelRefreshers.add((Runnable) invocation.getArgument(0));
                return null;
              }
            });

    ChannelPool.terminationWait = Duration.ofMillis(100);
    ManagedChannel channelPool =
        new ChannelPool(
            1, new FakeChannelFactory(Arrays.asList(channel1, channel2)), scheduledExecutorService);

    // final MethodDescriptor<Color, Money> methodDescriptor = FakeServiceGrpc.METHOD_RECOGNIZE;
    MethodDescriptor<String, Integer> methodDescriptor = FakeMethodDescriptor.create();
    final CallOptions callOptions = CallOptions.DEFAULT;

    // call1
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call1 = channelPool.newCall(methodDescriptor, callOptions);

    Thread thread = new Thread(channelRefreshers.get(0));
    thread.start();
    // shutdown should not called
    Mockito.verify(channel1, Mockito.after(300).never()).shutdown();
    call1.start(listener, new Metadata());
    call1.sendMessage("message");
    try {
      thread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
