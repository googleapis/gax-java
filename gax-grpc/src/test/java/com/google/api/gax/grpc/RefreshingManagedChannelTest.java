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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class RefreshingManagedChannelTest {
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

    ScheduledExecutorService scheduledExecutorService =
        Mockito.mock(ScheduledExecutorService.class);
    final List<Runnable> channelRefreshers = new ArrayList<>();
    // scheduleAtFixedRate gets called to schedule channelRefresher
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

    FakeChannelFactory fakeChannelFactory =
        new FakeChannelFactory(Arrays.asList(underlyingChannel1, underlyingChannel2));

    ManagedChannel refreshingManagedChannel =
        new RefreshingManagedChannel(fakeChannelFactory, scheduledExecutorService);

    // call 1
    MockClientCall<String, Integer> mockClientCall1 = new MockClientCall<>(1, Status.OK);
    final ClientCall<String, Integer> clientCall1 = Mockito.spy(mockClientCall1);
    Mockito.when(
            underlyingChannel1.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(clientCall1);

    // call 2
    MockClientCall<String, Integer> mockClientCall2 = new MockClientCall<>(1, Status.OK);
    final ClientCall<String, Integer> clientCall2 = Mockito.spy(mockClientCall2);
    Mockito.when(
            underlyingChannel1.newCall(
                Mockito.<MethodDescriptor<String, Integer>>any(), Mockito.any(CallOptions.class)))
        .thenReturn(clientCall2);

    RefreshingManagedChannel.terminationWait = Duration.ofMillis(100);

    MethodDescriptor<String, Integer> methodDescriptor = FakeMethodDescriptor.create();
    final CallOptions callOptions = CallOptions.DEFAULT;

    // call1
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener1 = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call1 =
        refreshingManagedChannel.newCall(methodDescriptor, callOptions);

    // call2
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener2 = Mockito.mock(ClientCall.Listener.class);
    ClientCall<String, Integer> call2 =
        refreshingManagedChannel.newCall(methodDescriptor, callOptions);

    // start call1
    call1.start(listener1, new Metadata());
    // force the refresh of channel
    Thread thread = new Thread(channelRefreshers.get(0));
    thread.start();
    // shutdown should not called
    Mockito.verify(underlyingChannel1, Mockito.after(300).never()).shutdown();
    // start call2
    call2.start(listener2, new Metadata());
    // send message and end the call
    call1.sendMessage("message");
    call2.sendMessage("message");
    Mockito.verify(underlyingChannel1, Mockito.never()).shutdown();
    try {
      thread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
