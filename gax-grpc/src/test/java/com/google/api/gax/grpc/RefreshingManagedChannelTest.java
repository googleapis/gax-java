/*
 * Copyright 2019 Google LLC
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

import com.google.api.gax.grpc.testing.FakeChannelFactory;
import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
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
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class RefreshingManagedChannelTest {
  @Test
  public void channelRefreshShouldSwapChannels() throws IOException {
    ManagedChannel underlyingChannel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel underlyingChannel2 = Mockito.mock(ManagedChannel.class);

    // mock executor service to capture the runnable scheduled so we can invoke it when we want to
    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);
    final List<Runnable> channelRefreshers = new ArrayList<>();
    Answer extractChannelRefresher =
        new Answer() {
          public Object answer(InvocationOnMock invocation) {
            channelRefreshers.add((Runnable) invocation.getArgument(0));
            return null;
          }
        };

    Mockito.doAnswer(extractChannelRefresher)
        .when(scheduledExecutorService)
        .schedule(
            Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));

    FakeChannelFactory channelFactory =
        new FakeChannelFactory(Arrays.asList(underlyingChannel1, underlyingChannel2));

    ManagedChannel refreshingManagedChannel =
        new RefreshingManagedChannel(channelFactory, scheduledExecutorService);

    refreshingManagedChannel.newCall(
        FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);

    Mockito.verify(underlyingChannel1, Mockito.only())
        .newCall(Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class));

    // swap channel
    channelRefreshers.get(0).run();

    refreshingManagedChannel.newCall(
        FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);

    Mockito.verify(underlyingChannel2, Mockito.only())
        .newCall(Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class));
  }

  @Test
  public void randomizeTest() throws IOException, InterruptedException, ExecutionException {
    int channelCount = 10;
    final ManagedChannel[] underlyingChannels = new ManagedChannel[channelCount];
    final Random r = new Random();
    for (int i = 0; i < channelCount; i++) {
      final ManagedChannel mockManagedChannel = Mockito.mock(ManagedChannel.class);
      underlyingChannels[i] = mockManagedChannel;

      final Answer waitAndSendMessage =
          new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
              // add a little time to sleep so calls don't always complete right away
              TimeUnit.MICROSECONDS.sleep(r.nextInt(1000));
              // when sending message on the call, the channel cannot be shutdown
              Mockito.verify(mockManagedChannel, Mockito.never()).shutdown();
              return invocation.callRealMethod();
            }
          };

      Answer createNewCall =
          new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
              // create a new client call for every new call to the underlying channel
              MockClientCall<String, Integer> mockClientCall = new MockClientCall<>(1, Status.OK);
              MockClientCall<String, Integer> spyClientCall = Mockito.spy(mockClientCall);

              // spy into clientCall to verify that the channel is not shutdown
              Mockito.doAnswer(waitAndSendMessage)
                  .when(spyClientCall)
                  .sendMessage(Mockito.anyString());

              return spyClientCall;
            }
          };

      // return a new mocked client call when requesting new call on the channel
      Mockito.doAnswer(createNewCall)
          .when(underlyingChannels[i])
          .newCall(
              Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class));
    }

    // mock executor service to capture the runnable scheduled so we can invoke it when we want to
    final List<Runnable> channelRefreshers = new ArrayList<>();
    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);
    Answer extractChannelRefresher =
        new Answer() {
          public Object answer(InvocationOnMock invocation) {
            channelRefreshers.add((Runnable) invocation.getArgument(0));
            return null;
          }
        };
    Mockito.doAnswer(extractChannelRefresher)
        .when(scheduledExecutorService)
        .schedule(
            Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));

    FakeChannelFactory channelFactory = new FakeChannelFactory(Arrays.asList(underlyingChannels));
    final ManagedChannel refreshingManagedChannel =
        new RefreshingManagedChannel(channelFactory, scheduledExecutorService);

    // send a bunch of request to RefreshingManagedChannel, executor needs more than 1 thread to
    // test out concurrency
    ExecutorService executor = Executors.newFixedThreadPool(10);

    // channelCount - 1 because the last channel cannot be refreshed because the FakeChannelFactory
    // has no more channel to create
    for (int i = 0; i < channelCount - 1; i++) {
      List<Future<?>> futures = new ArrayList<>();
      int requestCount = 100;
      int whenToRefresh = r.nextInt(requestCount);
      for (int j = 0; j < requestCount; j++) {
        Runnable createNewCall =
            new Runnable() {
              @Override
              public void run() {
                // create a new call and send message on refreshingManagedChannel
                ClientCall<String, Integer> call =
                    refreshingManagedChannel.newCall(
                        FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);
                @SuppressWarnings("unchecked")
                ClientCall.Listener<Integer> listener = Mockito.mock(ClientCall.Listener.class);
                call.start(listener, new Metadata());
                call.sendMessage("message");
              }
            };
        futures.add(executor.submit(createNewCall));
        // at the randomly chosen point, refresh the channel
        if (j == whenToRefresh) {
          futures.add(executor.submit(channelRefreshers.get(i)));
        }
      }
      for (Future<?> future : futures) {
        future.get();
      }
      Mockito.verify(underlyingChannels[i], Mockito.atLeastOnce()).shutdown();
      Mockito.verify(underlyingChannels[i + 1], Mockito.never()).shutdown();
    }
  }
}
