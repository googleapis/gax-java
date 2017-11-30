/*
 * Copyright 2017, Google LLC All rights reserved.
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
package com.google.api.gax.rpc;

import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeCallableFactory;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.api.gax.rpc.testing.FakeStreamingApi.ServerStreamingStashCallable;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class ServerStreamingCallableTest {
  private ClientContext clientContext;

  @Before
  public void setUp() {
    clientContext =
        ClientContext.newBuilder()
            .setDefaultCallContext(FakeCallContext.createDefault())
            .setTransportChannel(FakeTransportChannel.create(new FakeChannel()))
            .build();
  }

  private static class AccumulatingStreamObserver implements ApiStreamObserver<Integer> {
    private List<Integer> values = new ArrayList<>();
    private Throwable error;
    private boolean completed = false;

    @Override
    public void onNext(Integer value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable t) {
      error = t;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }

    public List<Integer> getValues() {
      if (!completed) {
        throw new IllegalStateException("Stream not completed.");
      }
      if (error != null) {
        throw ApiExceptionFactory.createException(error, FakeStatusCode.of(Code.UNKNOWN), false);
      }
      return values;
    }
  }

  @Test
  public void serverStreaming() {
    ServerStreamingStashCallable<Integer, Integer> callIntList =
        new ServerStreamingStashCallable<>(Arrays.asList(0, 1, 2));

    ServerStreamingCallable<Integer, Integer> callable =
        FakeCallableFactory.createServerStreamingCallable(
            callIntList,
            StreamingCallSettings.<Integer, Integer>newBuilder().build(),
            clientContext);

    AccumulatingStreamObserver responseObserver = new AccumulatingStreamObserver();
    callable.serverStreamingCall(0, responseObserver);
    Truth.assertThat(ImmutableList.copyOf(responseObserver.getValues()))
        .containsExactly(0, 1, 2)
        .inOrder();
    Truth.assertThat(callIntList.getActualRequest()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCall() {
    ApiCallContext defaultCallContext = FakeCallContext.createDefault();
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        stashCallable.withDefaultCallContext(defaultCallContext);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    callable.serverStreamingCall(request, observer);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    Truth.assertThat(stashCallable.getContext()).isSameAs(defaultCallContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCallWithContext() {
    FakeChannel channel = new FakeChannel();
    Credentials credentials = Mockito.mock(Credentials.class);
    ApiCallContext context =
        FakeCallContext.createDefault().withChannel(channel).withCredentials(credentials);
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        stashCallable.withDefaultCallContext(FakeCallContext.createDefault());
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    callable.serverStreamingCall(request, observer, context);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    FakeCallContext actualContext = (FakeCallContext) stashCallable.getContext();
    Truth.assertThat(actualContext.getChannel()).isSameAs(channel);
    Truth.assertThat(actualContext.getCredentials()).isSameAs(credentials);
  }

  @Test
  public void blockingServerStreaming() {
    ServerStreamingStashCallable<Integer, Integer> callIntList =
        new ServerStreamingStashCallable<>(Arrays.asList(0, 1, 2));

    ServerStreamingCallable<Integer, Integer> callable =
        FakeCallableFactory.createServerStreamingCallable(
            callIntList,
            StreamingCallSettings.<Integer, Integer>newBuilder().build(),
            clientContext);
    Truth.assertThat(ImmutableList.copyOf(callable.blockingServerStreamingCall(0)))
        .containsExactly(0, 1, 2)
        .inOrder();
    Truth.assertThat(callIntList.getActualRequest()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCall() {
    ApiCallContext defaultCallContext = FakeCallContext.createDefault();
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        stashCallable.withDefaultCallContext(defaultCallContext);
    Integer request = 1;
    callable.blockingServerStreamingCall(request);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    Truth.assertThat(stashCallable.getContext()).isSameAs(defaultCallContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCallWithContext() {
    FakeChannel channel = new FakeChannel();
    Credentials credentials = Mockito.mock(Credentials.class);
    ApiCallContext context =
        FakeCallContext.createDefault().withChannel(channel).withCredentials(credentials);
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        stashCallable.withDefaultCallContext(FakeCallContext.createDefault());
    Integer request = 1;
    callable.blockingServerStreamingCall(request, context);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    FakeCallContext actualContext = (FakeCallContext) stashCallable.getContext();
    Truth.assertThat(actualContext.getChannel()).isSameAs(channel);
    Truth.assertThat(actualContext.getCredentials()).isSameAs(credentials);
  }
}
