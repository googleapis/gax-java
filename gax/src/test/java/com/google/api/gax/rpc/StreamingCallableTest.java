/*
 * Copyright 2017, Google Inc. All rights reserved.
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
 *     * Neither the name of Google Inc. nor the names of its
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
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.api.gax.rpc.testing.FakeStreamingApi.BidiStreamingStashCallable;
import com.google.api.gax.rpc.testing.FakeStreamingApi.ClientStreamingStashCallable;
import com.google.api.gax.rpc.testing.FakeStreamingApi.ServerStreamingStashCallable;
import com.google.api.gax.rpc.testing.FakeTransportDescriptor;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StreamingCallableTest {
  private CallableFactory callableFactory =
      CallableFactory.create(FakeTransportDescriptor.create());

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
        callableFactory.create(
            callIntList,
            StreamingCallSettings.<Integer, Integer>newBuilder().build(),
            ClientContext.newBuilder().build());

    AccumulatingStreamObserver responseObserver = new AccumulatingStreamObserver();
    callable.serverStreamingCall(0, responseObserver);
    Truth.assertThat(ImmutableList.copyOf(responseObserver.getValues()))
        .containsExactly(0, 1, 2)
        .inOrder();
    Truth.assertThat(callIntList.getActualRequest()).isEqualTo(0);
  }

  @Test
  public void blockingServerStreaming() {
    ServerStreamingStashCallable<Integer, Integer> callIntList =
        new ServerStreamingStashCallable<>(Arrays.asList(0, 1, 2));

    ServerStreamingCallable<Integer, Integer> callable =
        callableFactory.create(
            callIntList,
            StreamingCallSettings.<Integer, Integer>newBuilder().build(),
            ClientContext.newBuilder().build());
    Truth.assertThat(ImmutableList.copyOf(callable.blockingServerStreamingCall(0)))
        .containsExactly(0, 1, 2)
        .inOrder();
    Truth.assertThat(callIntList.getActualRequest()).isEqualTo(0);
  }

  @Test
  public void bidiStreaming() {
    BidiStreamingStashCallable<Integer, Integer> callIntList =
        new BidiStreamingStashCallable<>(Arrays.asList(0, 1, 2));

    BidiStreamingCallable<Integer, Integer> callable =
        callableFactory.create(
            callIntList,
            StreamingCallSettings.<Integer, Integer>newBuilder().build(),
            ClientContext.newBuilder().build());

    AccumulatingStreamObserver responseObserver = new AccumulatingStreamObserver();
    ApiStreamObserver<Integer> requestObserver = callable.bidiStreamingCall(responseObserver);
    requestObserver.onNext(0);
    requestObserver.onNext(2);
    requestObserver.onNext(4);
    requestObserver.onCompleted();

    Truth.assertThat(ImmutableList.copyOf(responseObserver.getValues()))
        .containsExactly(0, 1, 2)
        .inOrder();
    Truth.assertThat(callIntList.getActualRequests()).containsExactly(0, 2, 4).inOrder();
  }

  @Test
  public void clientStreaming() {
    ClientStreamingStashCallable<Integer, Integer> callIntList =
        new ClientStreamingStashCallable<>(100);

    ClientStreamingCallable<Integer, Integer> callable =
        callableFactory.create(
            callIntList,
            StreamingCallSettings.<Integer, Integer>newBuilder().build(),
            ClientContext.newBuilder().build());

    AccumulatingStreamObserver responseObserver = new AccumulatingStreamObserver();
    ApiStreamObserver<Integer> requestObserver = callable.clientStreamingCall(responseObserver);
    requestObserver.onNext(0);
    requestObserver.onNext(2);
    requestObserver.onNext(4);
    requestObserver.onCompleted();

    Truth.assertThat(ImmutableList.copyOf(responseObserver.getValues()))
        .containsExactly(100)
        .inOrder();
    Truth.assertThat(callIntList.getActualRequests()).containsExactly(0, 2, 4).inOrder();
  }
}
