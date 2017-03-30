/*
 * Copyright 2016, Google Inc. All rights reserved.
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
package com.google.api.gax.grpc;

import com.google.api.gax.core.ApiStreamObserver;
import com.google.common.truth.Truth;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class StreamingCallableTest {
  @Test
  @SuppressWarnings("unchecked")
  public void testChannelBinding() {
    Channel channel = Mockito.mock(Channel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.bidiStreamingCall(observer);
    Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testCreate() {
    MethodDescriptor<Integer, Integer> methodDescriptor = Mockito.mock(MethodDescriptor.class);
    Channel channel = Mockito.mock(Channel.class);
    StreamingCallSettings<Integer, Integer> settings =
        StreamingCallSettings.newBuilder(methodDescriptor).build();
    StreamingCallable<Integer, Integer> apiCallable = settings.createStreamingCallable(channel);
    Truth.assertThat(apiCallable.getChannel()).isSameAs(channel);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCall() {
    Channel channel = Mockito.mock(Channel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.bidiStreamingCall(observer);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCallWithContext() {
    Channel channel = Mockito.mock(Channel.class);
    CallContext context = CallContext.of(channel, CallOptions.DEFAULT);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.bidiStreamingCall(observer, context);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
    Truth.assertThat(stash.context).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCall() {
    Channel channel = Mockito.mock(Channel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.clientStreamingCall(observer);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCallWithContext() {
    Channel channel = Mockito.mock(Channel.class);
    CallContext context = CallContext.of(channel, CallOptions.DEFAULT);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.clientStreamingCall(observer, context);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
    Truth.assertThat(stash.context).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCall() {
    Channel channel = Mockito.mock(Channel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    apiCallable.serverStreamingCall(request, observer);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
    Truth.assertThat(stash.actualRequest).isSameAs(request);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCallWithContext() {
    Channel channel = Mockito.mock(Channel.class);
    CallContext context = CallContext.of(channel, CallOptions.DEFAULT);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    apiCallable.serverStreamingCall(request, observer, context);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
    Truth.assertThat(stash.actualRequest).isSameAs(request);
    Truth.assertThat(stash.context).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCall() {
    Channel channel = Mockito.mock(Channel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    Integer request = 1;
    apiCallable.serverStreamingCall(request);
    Truth.assertThat(stash.actualRequest).isSameAs(request);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCallWithContext() {
    Channel channel = Mockito.mock(Channel.class);
    CallContext context = CallContext.of(channel, CallOptions.DEFAULT);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingCallable<Integer, Integer> apiCallable = new StreamingCallable<>(stash, channel, null);
    Integer request = 1;
    apiCallable.serverStreamingCall(request, context);
    Truth.assertThat(stash.actualRequest).isSameAs(request);
    Truth.assertThat(stash.context).isSameAs(context);
  }

  private static class StashCallable<RequestT, ResponseT>
      extends DirectStreamingCallable<RequestT, ResponseT> {
    CallContext context;
    ApiStreamObserver<ResponseT> actualObserver;
    RequestT actualRequest;

    StashCallable(ClientCallFactory<RequestT, ResponseT> factory) {
      super(factory);
    }

    @Override
    void serverStreamingCall(
        RequestT request, ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
      Truth.assertThat(request).isNotNull();
      Truth.assertThat(responseObserver).isNotNull();
      actualRequest = request;
      actualObserver = responseObserver;
      this.context = context;
    }

    @Override
    Iterator<ResponseT> blockingServerStreamingCall(RequestT request, CallContext context) {
      Truth.assertThat(request).isNotNull();
      actualRequest = request;
      this.context = context;
      return null;
    }

    @Override
    ApiStreamObserver<RequestT> bidiStreamingCall(
        ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
      Truth.assertThat(responseObserver).isNotNull();
      actualObserver = responseObserver;
      this.context = context;
      return null;
    }

    @Override
    ApiStreamObserver<RequestT> clientStreamingCall(
        ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
      Truth.assertThat(responseObserver).isNotNull();
      actualObserver = responseObserver;
      this.context = context;
      return null;
    }
  }
}
