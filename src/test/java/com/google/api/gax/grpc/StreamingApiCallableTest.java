/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;

import java.util.Iterator;

@RunWith(JUnit4.class)
public class StreamingApiCallableTest {

  private static class StashCallable<RequestT, ResponseT>
      extends StreamingCallable<RequestT, ResponseT> {
    CallContext<RequestT, ResponseT> context;
    StreamObserver<ResponseT> actualObserver;
    RequestT actualRequest;

    StashCallable(ClientCallFactory<RequestT, ResponseT> factory) {
      super(factory);
    }

    @Override
    void serverStreamingCall(CallContext<RequestT, ResponseT> context) {
      Truth.assertThat(context.getRequest()).isNotNull();
      Truth.assertThat(context.getResponseObserver()).isNotNull();
      actualRequest = context.getRequest();
      actualObserver = context.getResponseObserver();
      this.context = context;
    }

    @Override
    Iterator<ResponseT> blockingServerStreamingCall(CallContext<RequestT, ResponseT> context) {
      Truth.assertThat(context.getRequest()).isNotNull();
      actualRequest = context.getRequest();
      this.context = context;
      return null;
    }

    @Override
    StreamObserver<RequestT> bidiStreamingCall(CallContext<RequestT, ResponseT> context) {
      Truth.assertThat(context.getResponseObserver()).isNotNull();
      actualObserver = context.getResponseObserver();
      this.context = context;
      return null;
    }

    @Override
    StreamObserver<RequestT> clientStreamingCall(CallContext<RequestT, ResponseT> context) {
      Truth.assertThat(context.getResponseObserver()).isNotNull();
      actualObserver = context.getResponseObserver();
      this.context = context;
      return null;
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testChannelBinding() {
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingApiCallable<Integer, Integer> apiCallable = new StreamingApiCallable<>(stash);
    apiCallable.bind(channel);
    StreamObserver<Integer> observer = Mockito.mock(StreamObserver.class);
    apiCallable.bidiStreamingCall(observer);
    Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testCreate() {
    MethodDescriptor<Integer, Integer> methodDescriptor = Mockito.mock(MethodDescriptor.class);
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    StreamingCallSettings<Integer, Integer> settings =
        StreamingCallSettings.newBuilder(methodDescriptor).build();
    StreamingApiCallable<Integer, Integer> apiCallable =
        settings.createStreamingApiCallable(channel);
    Truth.assertThat(apiCallable.getChannel()).isSameAs(channel);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCall() {
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingApiCallable<Integer, Integer> apiCallable = new StreamingApiCallable<>(stash);
    apiCallable.bind(channel);
    StreamObserver<Integer> observer = Mockito.mock(StreamObserver.class);
    apiCallable.bidiStreamingCall(observer);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCall() {
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingApiCallable<Integer, Integer> apiCallable = new StreamingApiCallable<>(stash);
    apiCallable.bind(channel);
    StreamObserver<Integer> observer = Mockito.mock(StreamObserver.class);
    apiCallable.clientStreamingCall(observer);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCall() {
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingApiCallable<Integer, Integer> apiCallable = new StreamingApiCallable<>(stash);
    apiCallable.bind(channel);
    StreamObserver<Integer> observer = Mockito.mock(StreamObserver.class);
    Integer request = 1;
    apiCallable.serverStreamingCall(observer, request);
    Truth.assertThat(stash.actualObserver).isSameAs(observer);
    Truth.assertThat(stash.actualRequest).isSameAs(request);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCall() {
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    ClientCallFactory<Integer, Integer> factory = Mockito.mock(ClientCallFactory.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(factory);
    StreamingApiCallable<Integer, Integer> apiCallable = new StreamingApiCallable<>(stash);
    apiCallable.bind(channel);
    Integer request = 1;
    apiCallable.serverStreamingCall(request);
    Truth.assertThat(stash.actualRequest).isSameAs(request);
  }
}
