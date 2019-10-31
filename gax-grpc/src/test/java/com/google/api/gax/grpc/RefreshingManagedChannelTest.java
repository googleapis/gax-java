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

import com.google.api.gax.grpc.testing.FakeChannelFactory;
import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
import com.google.common.truth.Truth;
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
  // Channel should not shutdown after channel refresh if there are calls still on the channel
  // It should shutdown after the last call completes
  @Test
  public void oldChannelShouldNotShutdownIfThereAreOutstandingRequests() throws IOException {
    ManagedChannel underlyingChannel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel underlyingChannel2 = Mockito.mock(ManagedChannel.class);

    // mock executor service to capture the runnable scheduled so we can invoke it when we want to
    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);
    final List<Runnable> channelRefreshers = new ArrayList<>();
    Mockito.when(
            scheduledExecutorService.schedule(
                Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenAnswer(
            new Answer() {
              public Object answer(InvocationOnMock invocation) {
                channelRefreshers.add((Runnable) invocation.getArgument(0));
                return null;
              }
            });

    ManagedChannel refreshingManagedChannel =
        new RefreshingManagedChannel(
            new FakeChannelFactory(Arrays.asList(underlyingChannel1, underlyingChannel2)),
            scheduledExecutorService);

    // create a mock call when new call comes to the underlying channel
    MockClientCall<String, Integer> mockClientCall = new MockClientCall<>(1, Status.OK);
    Mockito.when(
            underlyingChannel1.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(mockClientCall);

    // create a new call on refreshingManagedChannel
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call =
        refreshingManagedChannel.newCall(
            FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);

    // start clientCall
    call.start(listener, new Metadata());
    new Thread(channelRefreshers.get(0)).start();
    // shutdownNow is not called because there is still an outstanding request
    Mockito.verify(underlyingChannel1, Mockito.after(200).never()).shutdownNow();
    // send message and end the call
    call.sendMessage("message");
    // shutdownNow is called because the outstanding request has completed
    Mockito.verify(underlyingChannel1, Mockito.timeout(200).atLeastOnce()).shutdownNow();
    Mockito.verify(underlyingChannel2, Mockito.never()).shutdownNow();
  }

  // Channel should shutdown after a refresh all the calls have completed
  @Test
  public void oldChannelShouldShutdownAfterRefresh() throws IOException {
    ManagedChannel underlyingChannel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel underlyingChannel2 = Mockito.mock(ManagedChannel.class);

    // mock executor service to capture the runnable scheduled so we can invoke it when we want to
    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);
    final List<Runnable> channelRefreshers = new ArrayList<>();
    Mockito.when(
            scheduledExecutorService.schedule(
                Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenAnswer(
            new Answer() {
              public Object answer(InvocationOnMock invocation) {
                channelRefreshers.add((Runnable) invocation.getArgument(0));
                return null;
              }
            });

    ManagedChannel refreshingManagedChannel =
        new RefreshingManagedChannel(
            new FakeChannelFactory(Arrays.asList(underlyingChannel1, underlyingChannel2)),
            scheduledExecutorService);

    // create a mock call when new call comes to the underlying channel
    MockClientCall<String, Integer> mockClientCall = new MockClientCall<>(1, Status.OK);
    Mockito.when(
            underlyingChannel1.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(mockClientCall);

    // create a new call on refreshingManagedChannel
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call =
        refreshingManagedChannel.newCall(
            FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);

    // start clientCall
    call.start(listener, new Metadata());
    // send message and end the call
    call.sendMessage("message");
    // shutdownNow is not called because the channel has not been swapped
    Mockito.verify(underlyingChannel1, Mockito.after(200).never()).shutdownNow();
    new Thread(channelRefreshers.get(0)).start();
    // shutdownNow is called because there are no outstanding requests and refresh has been called
    Mockito.verify(underlyingChannel1, Mockito.timeout(200).atLeastOnce()).shutdownNow();
    Mockito.verify(underlyingChannel2, Mockito.never()).shutdownNow();
  }

  // Test the concurrency problem of if a call starts on an "old channel" and a refresh happens,
  // all the calls (started or not) will be allowed to continue to complete.
  // This test will perform 2 calls on the old channel
  // 1. Create a new call and start it
  // 2. Create another new call, but do not start it
  // 3. Start the refresh, channel should be swapped
  // 4. Check that the old channel has not been shutdown
  // 5. Complete the first call
  // 6. Start the second call, and complete it
  // 7. Refresh should finish now
  @Test
  public void channelRefreshDoesNotCancelCalls() throws IOException {
    ManagedChannel underlyingChannel1 = Mockito.mock(ManagedChannel.class);
    ManagedChannel underlyingChannel2 = Mockito.mock(ManagedChannel.class);

    // mock executor service to capture the runnable scheduled so we can invoke it when we want to
    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);
    final List<Runnable> channelRefreshers = new ArrayList<>();
    Mockito.when(
            scheduledExecutorService.schedule(
                Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenAnswer(
            new Answer() {
              public Object answer(InvocationOnMock invocation) {
                channelRefreshers.add((Runnable) invocation.getArgument(0));
                return null;
              }
            });

    ManagedChannel refreshingManagedChannel =
        new RefreshingManagedChannel(
            new FakeChannelFactory(Arrays.asList(underlyingChannel1, underlyingChannel2)),
            scheduledExecutorService);

    // first mock call when new call comes to the underlying channel
    MockClientCall<String, Integer> mockClientCall1 = new MockClientCall<>(1, Status.OK);
    Mockito.when(
            underlyingChannel1.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(mockClientCall1);

    // first mock call when new call comes to the underlying channel
    MockClientCall<String, Integer> mockClientCall2 = new MockClientCall<>(1, Status.OK);
    Mockito.when(
            underlyingChannel1.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(mockClientCall2);

    // first new call on refreshingManagedChannel
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener1 = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call1 =
        refreshingManagedChannel.newCall(
            FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);

    // second new call on refreshingManagedChannel
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener2 = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call2 =
        refreshingManagedChannel.newCall(
            FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);

    // start call1
    call1.start(listener1, new Metadata());
    // force the refresh of channel
    new Thread(channelRefreshers.get(0)).start();
    // shutdownNow is not called because there are outstanding requests
    Mockito.verify(underlyingChannel1, Mockito.after(200).never()).shutdownNow();
    // start call2
    call2.start(listener2, new Metadata());
    // send message and end the call
    call1.sendMessage("message");
    // shutdownNow is not called because there is still another outstanding request
    Mockito.verify(underlyingChannel1, Mockito.after(200).never()).shutdownNow();
    call2.sendMessage("message");
    Mockito.verify(underlyingChannel1, Mockito.timeout(200).atLeastOnce()).shutdownNow();
    Mockito.verify(underlyingChannel2, Mockito.never()).shutdownNow();
  }

  @Test
  public void randomizeTest() throws IOException, InterruptedException, ExecutionException {
    int channelCount = 10;
    ManagedChannel[] underlyingChannels = new ManagedChannel[channelCount];
    final boolean[] underlyingChannelsShutdown = new boolean[channelCount];
    for (int i = 0; i < channelCount; i++) {
      underlyingChannels[i] = Mockito.mock(ManagedChannel.class);
      underlyingChannelsShutdown[i] = false;
      final int finalI = i;
      // return a new mocked client call when requesting new call on the channel, also check that
      // the channel is not shutdown
      Mockito.doAnswer(
              new Answer() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                  // this channel cannot be shutdown
                  Truth.assertThat(underlyingChannelsShutdown[finalI]).isFalse();

                  MockClientCall<String, Integer> mockClientCall =
                      new MockClientCall<>(1, Status.OK);
                  MockClientCall<String, Integer> spyClientCall = Mockito.spy(mockClientCall);

                  Mockito.doAnswer(
                          new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                              // when sending message on the call, the channel cannot be shutdown
                              Truth.assertThat(underlyingChannelsShutdown[finalI]).isFalse();
                              // add a little time to sleep so calls don't always complete right away
                              TimeUnit.MILLISECONDS.sleep(10);
                              return invocation.callRealMethod();
                            }
                          })
                      .when(spyClientCall)
                      .sendMessage(Mockito.anyString());

                  return spyClientCall;
                }
              })
          .when(underlyingChannels[i])
          .newCall(
              Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class));

      // track when the underlying channel has been shutdown
      Mockito.doAnswer(
              new Answer() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                  underlyingChannelsShutdown[finalI] = true;
                  return null;
                }
              })
          .when(underlyingChannels[i])
          .shutdownNow();
    }

    // mock executor service to capture the runnable scheduled so we can invoke it when we want to
    final List<Runnable> channelRefreshers = new ArrayList<>();
    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);
    Mockito.when(
            scheduledExecutorService.schedule(
                Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenAnswer(
            new Answer() {
              public Object answer(InvocationOnMock invocation) {
                channelRefreshers.add((Runnable) invocation.getArgument(0));
                return null;
              }
            });
    final ManagedChannel refreshingManagedChannel =
        new RefreshingManagedChannel(
            new FakeChannelFactory(Arrays.asList(underlyingChannels)), scheduledExecutorService);

    Random r = new Random();

    ExecutorService executor = Executors.newFixedThreadPool(10);

    for (int i = 0; i < channelCount - 1; i++) {
      List<Future<?>> futures = new ArrayList<>();
      int requestCount = 100;

      int whenToRefresh = r.nextInt(requestCount);
      for (int j = 0; j < requestCount; j++) {
        futures.add(
            executor.submit(
                new Runnable() {
                  @Override
                  public void run() {
                    // create a new call on refreshingManagedChannel
                    ClientCall<String, Integer> call =
                        refreshingManagedChannel.newCall(
                            FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT);
                    @SuppressWarnings("unchecked")
                    ClientCall.Listener<Integer> listener = Mockito.mock(ClientCall.Listener.class);
                    call.start(listener, new Metadata());
                    call.sendMessage("message");
                  }
                }));
        if (j == whenToRefresh) {
          futures.add(executor.submit(channelRefreshers.get(i)));
        }
      }
      for (Future<?> future : futures) {
        future.get();
      }
      Mockito.verify(underlyingChannels[i], Mockito.atLeastOnce()).shutdownNow();
      Mockito.verify(underlyingChannels[i + 1], Mockito.never()).shutdownNow();
    }
  }
}
