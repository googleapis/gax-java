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
package com.google.api.gax.rpc;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeCallableFactory;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeStreamingApi.BidiStreamingStashCallable;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Test;

public class BidiStreamingCallableTest {
  private ClientContext clientContext =
      ClientContext.newBuilder()
          .setDefaultCallContext(FakeCallContext.createDefault())
          .setTransportChannel(FakeTransportChannel.create(new FakeChannel()))
          .build();

  @Test
  public void bidiStreaming() {
    BidiStreamingStashCallable<Integer, Integer> callIntList =
        new BidiStreamingStashCallable<>(Arrays.asList(0, 1, 2));

    BidiStreamingCallable<Integer, Integer> callable =
        FakeCallableFactory.createBidiStreamingCallable(
            callIntList,
            StreamingCallSettings.<Integer, Integer>newBuilder().build(),
            clientContext);

    ServerStreamingCallableTest.AccumulatingStreamObserver responseObserver =
        new ServerStreamingCallableTest.AccumulatingStreamObserver();

    ClientStream<Integer> stream = callable.call(responseObserver);
    stream.send(3);
    stream.send(4);
    stream.send(5);
    stream.close();

    assertThat(ImmutableList.copyOf(responseObserver.getValues()))
        .containsExactly(0, 1, 2)
        .inOrder();
    assertThat(callIntList.getActualRequests()).containsExactly(3, 4, 5).inOrder();
  }
}
