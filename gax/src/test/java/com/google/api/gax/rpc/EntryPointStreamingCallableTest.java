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

import com.google.api.gax.rpc.testing.FakeApiCallContext;
import com.google.api.gax.rpc.testing.FakeStreamingApi.BidiStreamingStashCallable;
import com.google.api.gax.rpc.testing.FakeStreamingApi.ClientStreamingStashCallable;
import com.google.api.gax.rpc.testing.FakeStreamingApi.ServerStreamingStashCallable;
import com.google.common.truth.Truth;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class EntryPointStreamingCallableTest {
  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCall() {
    ApiCallContext defaultCallContext = FakeApiCallContext.of();
    BidiStreamingStashCallable<Integer, Integer> stashCallable = new BidiStreamingStashCallable<>();
    BidiStreamingCallable<Integer, Integer> callable =
        new EntryPointBidiStreamingCallable<>(stashCallable, defaultCallContext);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    callable.bidiStreamingCall(observer);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getContext()).isSameAs(defaultCallContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCallWithContext() {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    BidiStreamingStashCallable<Integer, Integer> stashCallable = new BidiStreamingStashCallable<>();
    BidiStreamingCallable<Integer, Integer> callable =
        new EntryPointBidiStreamingCallable<>(stashCallable, FakeApiCallContext.of());
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    callable.bidiStreamingCall(observer, context);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getContext()).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCallWithCallContextEnhancer() {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextEnhancer enhancer =
        new ApiCallContextEnhancer() {
          @Override
          public ApiCallContext enhance(ApiCallContext context) {
            return outerContext;
          }
        };
    BidiStreamingStashCallable<Integer, Integer> stashCallable = new BidiStreamingStashCallable<>();
    BidiStreamingCallable<Integer, Integer> callable =
        new EntryPointBidiStreamingCallable<>(
            stashCallable, FakeApiCallContext.of(), Collections.singletonList(enhancer));
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    callable.bidiStreamingCall(observer);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getContext()).isSameAs(outerContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCall() {
    ApiCallContext defaultCallContext = FakeApiCallContext.of();
    ClientStreamingStashCallable<Integer, Integer> stashCallable =
        new ClientStreamingStashCallable<>();
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    ClientStreamingCallable<Integer, Integer> callable =
        new EntryPointClientStreamingCallable<>(stashCallable, defaultCallContext);
    callable.clientStreamingCall(observer);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getContext()).isSameAs(defaultCallContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCallWithContext() {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    ClientStreamingStashCallable<Integer, Integer> stashCallable =
        new ClientStreamingStashCallable<>();
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    ClientStreamingCallable<Integer, Integer> callable =
        new EntryPointClientStreamingCallable<>(stashCallable, FakeApiCallContext.of());
    callable.clientStreamingCall(observer, context);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getContext()).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCallWithCallContextEnhancer() {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextEnhancer enhancer =
        new ApiCallContextEnhancer() {
          @Override
          public ApiCallContext enhance(ApiCallContext context) {
            return outerContext;
          }
        };
    ClientStreamingStashCallable<Integer, Integer> stashCallable =
        new ClientStreamingStashCallable<>();
    ClientStreamingCallable<Integer, Integer> callable =
        new EntryPointClientStreamingCallable<>(
            stashCallable, FakeApiCallContext.of(), Collections.singletonList(enhancer));
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    callable.clientStreamingCall(observer);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getContext()).isSameAs(outerContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCall() {
    ApiCallContext defaultCallContext = FakeApiCallContext.of();
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        new EntryPointServerStreamingCallable<>(stashCallable, defaultCallContext);
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
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        new EntryPointServerStreamingCallable<>(stashCallable, FakeApiCallContext.of());
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    callable.serverStreamingCall(request, observer, context);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    Truth.assertThat(stashCallable.getContext()).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCallWithCallContextEnhancer() {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextEnhancer enhancer =
        new ApiCallContextEnhancer() {
          @Override
          public ApiCallContext enhance(ApiCallContext context) {
            return outerContext;
          }
        };
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        new EntryPointServerStreamingCallable<>(
            stashCallable, FakeApiCallContext.of(), Collections.singletonList(enhancer));
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    callable.serverStreamingCall(request, observer);
    Truth.assertThat(stashCallable.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    Truth.assertThat(stashCallable.getContext()).isSameAs(outerContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCall() {
    ApiCallContext defaultCallContext = FakeApiCallContext.of();
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        new EntryPointServerStreamingCallable<>(stashCallable, defaultCallContext);
    Integer request = 1;
    callable.blockingServerStreamingCall(request);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    Truth.assertThat(stashCallable.getContext()).isSameAs(defaultCallContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCallWithContext() {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        new EntryPointServerStreamingCallable<>(stashCallable, FakeApiCallContext.of());
    Integer request = 1;
    callable.blockingServerStreamingCall(request, context);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    Truth.assertThat(stashCallable.getContext()).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCallWithCallContextEnhancer() {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextEnhancer enhancer =
        new ApiCallContextEnhancer() {
          @Override
          public ApiCallContext enhance(ApiCallContext context) {
            return outerContext;
          }
        };
    ServerStreamingStashCallable<Integer, Integer> stashCallable =
        new ServerStreamingStashCallable<>();
    ServerStreamingCallable<Integer, Integer> callable =
        new EntryPointServerStreamingCallable<>(
            stashCallable, FakeApiCallContext.of(), Collections.singletonList(enhancer));
    Integer request = 1;
    callable.blockingServerStreamingCall(request);
    Truth.assertThat(stashCallable.getActualRequest()).isSameAs(request);
    Truth.assertThat(stashCallable.getContext()).isSameAs(outerContext);
  }
}
