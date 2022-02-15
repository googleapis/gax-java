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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.grpc.testing.FakeChannelFactory;
import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
import com.google.api.gax.grpc.testing.FakeServiceGrpc;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.ClientCalls;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class ChannelPoolTest {
  @Test
  public void testAuthority() throws IOException {
    ManagedChannel sub1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel sub2 = Mockito.mock(ManagedChannel.class);

    Mockito.when(sub1.authority()).thenReturn("myAuth");

    ChannelPool pool =
        ChannelPool.create(
            ChannelPoolSettings.staticallySized(2),
            new FakeChannelFactory(Arrays.asList(sub1, sub2)));
    assertThat(pool.authority()).isEqualTo("myAuth");
  }

  @Test
  public void testRoundRobin() throws IOException {
    ManagedChannel sub1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel sub2 = Mockito.mock(ManagedChannel.class);

    Mockito.when(sub1.authority()).thenReturn("myAuth");

    ArrayList<ManagedChannel> channels = Lists.newArrayList(sub1, sub2);
    ChannelPool pool =
        ChannelPool.create(
            ChannelPoolSettings.staticallySized(channels.size()), new FakeChannelFactory(channels));

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

    channels.forEach(Mockito::reset);
    Mockito.doReturn(expectedClientCall).when(targetChannel).newCall(methodDescriptor, callOptions);

    ClientCall<Color, Money> actualCall = pool.newCall(methodDescriptor, callOptions);
    Mockito.verify(targetChannel, Mockito.times(1)).newCall(methodDescriptor, callOptions);
    actualCall.start(null, null);
    Mockito.verify(expectedClientCall, Mockito.times(1)).start(Mockito.any(), Mockito.any());

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
              (ignored) -> {
                counts[index].incrementAndGet();
                return clientCall;
              });
    }

    final ChannelPool pool =
        ChannelPool.create(
            ChannelPoolSettings.staticallySized(numChannels),
            new FakeChannelFactory(Arrays.asList(channels)));

    int numThreads = 20;
    final int numPerThread = 1000;

    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    for (int i = 0; i < numThreads; i++) {
      executor.submit(
          () -> {
            for (int j = 0; j < numPerThread; j++) {
              pool.newCall(methodDescriptor, callOptions);
            }
          });
    }
    executor.shutdown();
    boolean shutdown = executor.awaitTermination(1, TimeUnit.MINUTES);
    assertThat(shutdown).isTrue();

    int expectedCount = (numThreads * numPerThread) / numChannels;
    for (AtomicInteger count : counts) {
      assertThat(count.get()).isAnyOf(expectedCount, expectedCount + 1);
    }
  }

  // Test channelPrimer is called same number of times as poolSize if executorService is set to null
  @Test
  public void channelPrimerShouldCallPoolConstruction() throws IOException {
    ChannelPrimer mockChannelPrimer = Mockito.mock(ChannelPrimer.class);
    ManagedChannel channel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel channel2 = Mockito.mock(ManagedChannel.class);

    ChannelPool.create(
        ChannelPoolSettings.staticallySized(2)
            .toBuilder()
            .setPreemptiveRefreshEnabled(true)
            .build(),
        new FakeChannelFactory(Arrays.asList(channel1, channel2), mockChannelPrimer));
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

    List<Runnable> channelRefreshers = new ArrayList<>();

    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);

    Answer<?> extractChannelRefresher =
        invocation -> {
          channelRefreshers.add(invocation.getArgument(0));
          return Mockito.mock(ScheduledFuture.class);
        };

    Mockito.doAnswer(extractChannelRefresher)
        .when(scheduledExecutorService)
        .scheduleAtFixedRate(
            Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any());

    FakeChannelFactory channelFactory =
        new FakeChannelFactory(Arrays.asList(channel1, channel2, channel3), mockChannelPrimer);

    new ChannelPool(
        ChannelPoolSettings.staticallySized(1)
            .toBuilder()
            .setPreemptiveRefreshEnabled(true)
            .build(),
        channelFactory,
        scheduledExecutorService);
    // 1 call during the creation
    Mockito.verify(mockChannelPrimer, Mockito.times(1))
        .primeChannel(Mockito.any(ManagedChannel.class));

    channelRefreshers.get(0).run();
    // 1 more call during channel refresh
    Mockito.verify(mockChannelPrimer, Mockito.times(2))
        .primeChannel(Mockito.any(ManagedChannel.class));

    channelRefreshers.get(0).run();
    // 1 more call during channel refresh
    Mockito.verify(mockChannelPrimer, Mockito.times(3))
        .primeChannel(Mockito.any(ManagedChannel.class));
  }

  // ----
  // call should be allowed to complete and the channel should not be shutdown
  @Test
  public void callShouldCompleteAfterCreation() throws IOException {
    ManagedChannel underlyingChannel = Mockito.mock(ManagedChannel.class);
    ManagedChannel replacementChannel = Mockito.mock(ManagedChannel.class);
    FakeChannelFactory channelFactory =
        new FakeChannelFactory(ImmutableList.of(underlyingChannel, replacementChannel));
    ChannelPool pool = ChannelPool.create(ChannelPoolSettings.staticallySized(1), channelFactory);

    // create a mock call when new call comes to the underlying channel
    MockClientCall<String, Integer> mockClientCall = new MockClientCall<>(1, Status.OK);
    MockClientCall<String, Integer> spyClientCall = Mockito.spy(mockClientCall);
    Mockito.when(
            underlyingChannel.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(spyClientCall);

    Answer<Object> verifyChannelNotShutdown =
        invocation -> {
          Mockito.verify(underlyingChannel, Mockito.never()).shutdown();
          return invocation.callRealMethod();
        };

    // verify that underlying channel is not shutdown when clientCall is still sending message
    Mockito.doAnswer(verifyChannelNotShutdown).when(spyClientCall).sendMessage(Mockito.anyString());

    // create a new call on entry
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call =
        pool.newCall(FakeMethodDescriptor.create(), CallOptions.DEFAULT);

    pool.refresh();
    // shutdown is not called because there is still an outstanding call, even if it hasn't started
    Mockito.verify(underlyingChannel, Mockito.after(200).never()).shutdown();

    // start clientCall
    call.start(listener, new Metadata());
    // send message and end the call
    call.sendMessage("message");
    // shutdown is called because the outstanding call has completed
    Mockito.verify(underlyingChannel, Mockito.atLeastOnce()).shutdown();

    // Replacement channel shouldn't be touched
    Mockito.verify(replacementChannel, Mockito.never()).shutdown();
    Mockito.verify(replacementChannel, Mockito.never()).newCall(Mockito.any(), Mockito.any());
  }

  // call should be allowed to complete and the channel should not be shutdown
  @Test
  public void callShouldCompleteAfterStarted() throws IOException {
    final ManagedChannel underlyingChannel = Mockito.mock(ManagedChannel.class);
    ManagedChannel replacementChannel = Mockito.mock(ManagedChannel.class);

    FakeChannelFactory channelFactory =
        new FakeChannelFactory(ImmutableList.of(underlyingChannel, replacementChannel));
    ChannelPool pool = ChannelPool.create(ChannelPoolSettings.staticallySized(1), channelFactory);

    // create a mock call when new call comes to the underlying channel
    MockClientCall<String, Integer> mockClientCall = new MockClientCall<>(1, Status.OK);
    MockClientCall<String, Integer> spyClientCall = Mockito.spy(mockClientCall);
    Mockito.when(
            underlyingChannel.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(spyClientCall);

    Answer<Object> verifyChannelNotShutdown =
        invocation -> {
          Mockito.verify(underlyingChannel, Mockito.never()).shutdown();
          return invocation.callRealMethod();
        };

    // verify that underlying channel is not shutdown when clientCall is still sending message
    Mockito.doAnswer(verifyChannelNotShutdown).when(spyClientCall).sendMessage(Mockito.anyString());

    // create a new call on safeShutdownManagedChannel
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call =
        pool.newCall(FakeMethodDescriptor.create(), CallOptions.DEFAULT);

    // start clientCall
    call.start(listener, new Metadata());
    pool.refresh();

    // shutdown is not called because there is still an outstanding call
    Mockito.verify(underlyingChannel, Mockito.after(200).never()).shutdown();
    // send message and end the call
    call.sendMessage("message");
    // shutdown is called because the outstanding call has completed
    Mockito.verify(underlyingChannel, Mockito.atLeastOnce()).shutdown();
  }

  // Channel should be shutdown after a refresh all the calls have completed
  @Test
  public void channelShouldShutdown() throws IOException {
    ManagedChannel underlyingChannel = Mockito.mock(ManagedChannel.class);
    ManagedChannel replacementChannel = Mockito.mock(ManagedChannel.class);

    FakeChannelFactory channelFactory =
        new FakeChannelFactory(ImmutableList.of(underlyingChannel, replacementChannel));
    ChannelPool pool = ChannelPool.create(ChannelPoolSettings.staticallySized(1), channelFactory);

    // create a mock call when new call comes to the underlying channel
    MockClientCall<String, Integer> mockClientCall = new MockClientCall<>(1, Status.OK);
    MockClientCall<String, Integer> spyClientCall = Mockito.spy(mockClientCall);
    Mockito.when(
            underlyingChannel.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(spyClientCall);

    Answer<Object> verifyChannelNotShutdown =
        invocation -> {
          Mockito.verify(underlyingChannel, Mockito.never()).shutdown();
          return invocation.callRealMethod();
        };

    // verify that underlying channel is not shutdown when clientCall is still sending message
    Mockito.doAnswer(verifyChannelNotShutdown).when(spyClientCall).sendMessage(Mockito.anyString());

    // create a new call on safeShutdownManagedChannel
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call =
        pool.newCall(FakeMethodDescriptor.create(), CallOptions.DEFAULT);

    // start clientCall
    call.start(listener, new Metadata());
    // send message and end the call
    call.sendMessage("message");
    // shutdown is not called because it has not been shutdown yet
    Mockito.verify(underlyingChannel, Mockito.after(200).never()).shutdown();
    pool.refresh();
    // shutdown is called because the outstanding call has completed
    Mockito.verify(underlyingChannel, Mockito.atLeastOnce()).shutdown();
  }

  @Test
  public void channelRefreshShouldSwapChannels() throws IOException {
    ManagedChannel underlyingChannel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel underlyingChannel2 = Mockito.mock(ManagedChannel.class);

    // mock executor service to capture the runnable scheduled, so we can invoke it when we want to
    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);

    Mockito.doReturn(null)
        .when(scheduledExecutorService)
        .schedule(
            Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));

    FakeChannelFactory channelFactory =
        new FakeChannelFactory(ImmutableList.of(underlyingChannel1, underlyingChannel2));
    ChannelPool pool =
        new ChannelPool(
            ChannelPoolSettings.staticallySized(1)
                .toBuilder()
                .setPreemptiveRefreshEnabled(true)
                .build(),
            channelFactory,
            scheduledExecutorService);
    Mockito.reset(underlyingChannel1);

    pool.newCall(FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);

    Mockito.verify(underlyingChannel1, Mockito.only())
        .newCall(Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class));

    // swap channel
    pool.refresh();

    pool.newCall(FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);

    Mockito.verify(underlyingChannel2, Mockito.only())
        .newCall(Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class));
  }

  @Test
  public void channelCountShouldNotChangeWhenOutstandingRpcsAreWithinLimits() throws Exception {
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);

    List<ManagedChannel> channels = new ArrayList<>();
    List<ClientCall<Object, Object>> startedCalls = new ArrayList<>();

    ChannelFactory channelFactory =
        () -> {
          ManagedChannel channel = Mockito.mock(ManagedChannel.class);
          Mockito.when(channel.newCall(Mockito.any(), Mockito.any()))
              .thenAnswer(
                  invocation -> {
                    @SuppressWarnings("unchecked")
                    ClientCall<Object, Object> clientCall = Mockito.mock(ClientCall.class);
                    startedCalls.add(clientCall);
                    return clientCall;
                  });

          channels.add(channel);
          return channel;
        };

    ChannelPool pool =
        new ChannelPool(
            ChannelPoolSettings.builder()
                .setInitialChannelCount(2)
                .setMinRpcsPerChannel(1)
                .setMaxRpcsPerChannel(2)
                .build(),
            channelFactory,
            executor);
    assertThat(pool.entries.get()).hasSize(2);

    // Start the minimum number of
    for (int i = 0; i < 2; i++) {
      ClientCalls.futureUnaryCall(
          pool.newCall(FakeServiceGrpc.METHOD_RECOGNIZE, CallOptions.DEFAULT),
          Color.getDefaultInstance());
    }
    pool.resize();
    assertThat(pool.entries.get()).hasSize(2);

    // Add enough RPCs to be just at the brink of expansion
    for (int i = startedCalls.size(); i < 4; i++) {
      ClientCalls.futureUnaryCall(
          pool.newCall(FakeServiceGrpc.METHOD_RECOGNIZE, CallOptions.DEFAULT),
          Color.getDefaultInstance());
    }
    pool.resize();
    assertThat(pool.entries.get()).hasSize(2);

    // Add another RPC to push expansion
    pool.newCall(FakeServiceGrpc.METHOD_RECOGNIZE, CallOptions.DEFAULT);
    pool.resize();
    assertThat(pool.entries.get()).hasSize(4); // += ChannelPool::MAX_RESIZE_DELTA
    assertThat(startedCalls).hasSize(5);

    // Complete RPCs to the brink of shrinking
    @SuppressWarnings("unchecked")
    ArgumentCaptor<ClientCall.Listener<Object>> captor =
        ArgumentCaptor.forClass(ClientCall.Listener.class);
    Mockito.verify(startedCalls.remove(0)).start(captor.capture(), Mockito.any());
    captor.getValue().onClose(Status.ABORTED, new Metadata());
    // Resize twice: the first round maintains the peak from the last cycle
    pool.resize();
    pool.resize();
    assertThat(pool.entries.get()).hasSize(4);
    assertThat(startedCalls).hasSize(4);

    // Complete another RPC to trigger shrinking
    Mockito.verify(startedCalls.remove(0)).start(captor.capture(), Mockito.any());
    captor.getValue().onClose(Status.ABORTED, new Metadata());
    // Resize twice: the first round maintains the peak from the last cycle
    pool.resize();
    pool.resize();
    assertThat(startedCalls).hasSize(3);
    // range of channels is [2-3] rounded down average is 2
    assertThat(pool.entries.get()).hasSize(2);
  }

  @Test
  public void removedIdleChannelsAreShutdown() throws Exception {
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);

    List<ManagedChannel> channels = new ArrayList<>();
    List<ClientCall<Object, Object>> startedCalls = new ArrayList<>();

    ChannelFactory channelFactory =
        () -> {
          ManagedChannel channel = Mockito.mock(ManagedChannel.class);
          Mockito.when(channel.newCall(Mockito.any(), Mockito.any()))
              .thenAnswer(
                  invocation -> {
                    @SuppressWarnings("unchecked")
                    ClientCall<Object, Object> clientCall = Mockito.mock(ClientCall.class);
                    startedCalls.add(clientCall);
                    return clientCall;
                  });

          channels.add(channel);
          return channel;
        };

    ChannelPool pool =
        new ChannelPool(
            ChannelPoolSettings.builder()
                .setInitialChannelCount(2)
                .setMinRpcsPerChannel(1)
                .setMaxRpcsPerChannel(2)
                .build(),
            channelFactory,
            executor);
    assertThat(pool.entries.get()).hasSize(2);

    // With no outstanding RPCs, the pool should shrink
    pool.resize();
    assertThat(pool.entries.get()).hasSize(1);
    Mockito.verify(channels.get(1), Mockito.times(1)).shutdown();
  }

  @Test
  public void removedActiveChannelsAreShutdown() throws Exception {
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);

    List<ManagedChannel> channels = new ArrayList<>();
    List<ClientCall<Object, Object>> startedCalls = new ArrayList<>();

    ChannelFactory channelFactory =
        () -> {
          ManagedChannel channel = Mockito.mock(ManagedChannel.class);
          Mockito.when(channel.newCall(Mockito.any(), Mockito.any()))
              .thenAnswer(
                  invocation -> {
                    @SuppressWarnings("unchecked")
                    ClientCall<Object, Object> clientCall = Mockito.mock(ClientCall.class);
                    startedCalls.add(clientCall);
                    return clientCall;
                  });

          channels.add(channel);
          return channel;
        };

    ChannelPool pool =
        new ChannelPool(
            ChannelPoolSettings.builder()
                .setInitialChannelCount(2)
                .setMinRpcsPerChannel(1)
                .setMaxRpcsPerChannel(2)
                .build(),
            channelFactory,
            executor);
    assertThat(pool.entries.get()).hasSize(2);

    // Start 2 RPCs
    for (int i = 0; i < 2; i++) {
      ClientCalls.futureUnaryCall(
          pool.newCall(FakeServiceGrpc.METHOD_RECOGNIZE, CallOptions.DEFAULT),
          Color.getDefaultInstance());
    }
    // Complete the first one
    @SuppressWarnings("unchecked")
    ArgumentCaptor<ClientCall.Listener<Object>> captor =
        ArgumentCaptor.forClass(ClientCall.Listener.class);
    Mockito.verify(startedCalls.get(0)).start(captor.capture(), Mockito.any());
    captor.getValue().onClose(Status.ABORTED, new Metadata());

    // With a single RPC, the pool should shrink
    pool.resize();
    pool.resize();
    assertThat(pool.entries.get()).hasSize(1);

    // While the RPC is outstanding, the channel should still be open
    Mockito.verify(channels.get(1), Mockito.never()).shutdown();

    // Complete the RPC
    Mockito.verify(startedCalls.get(1)).start(captor.capture(), Mockito.any());
    captor.getValue().onClose(Status.ABORTED, new Metadata());
    // Now the channel should be closed
    Mockito.verify(channels.get(1), Mockito.times(1)).shutdown();
  }
}
