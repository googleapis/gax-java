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

import com.google.api.gax.rpc.testing.FakeStreamingApi.StreamingStashCallable;
import com.google.common.truth.Truth;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class StreamingCallableTest {
  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCall() {
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.bidiStreamingCall(observer);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCallWithContext() {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.bidiStreamingCall(observer, context);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stash.getContext()).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testBidiStreamingCallWithCallContextDecorator() {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextDecorator decorator =
        new ApiCallContextDecorator() {
          @Override
          public ApiCallContext decorate(ApiCallContext context) {
            return outerContext;
          }
        };
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash, decorator);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.bidiStreamingCall(observer);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stash.getContext()).isSameAs(outerContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCall() {
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.clientStreamingCall(observer);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCallWithContext() {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.clientStreamingCall(observer, context);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stash.getContext()).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testClientStreamingCallWithCallContextDecorator() {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextDecorator decorator =
        new ApiCallContextDecorator() {
          @Override
          public ApiCallContext decorate(ApiCallContext context) {
            return outerContext;
          }
        };
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash, decorator);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    apiCallable.clientStreamingCall(observer);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stash.getContext()).isSameAs(outerContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCall() {
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    apiCallable.serverStreamingCall(request, observer);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stash.getActualRequest()).isSameAs(request);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCallWithContext() {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    apiCallable.serverStreamingCall(request, observer, context);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stash.getActualRequest()).isSameAs(request);
    Truth.assertThat(stash.getContext()).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testServerStreamingCallWithCallContextDecorator() {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextDecorator decorator =
        new ApiCallContextDecorator() {
          @Override
          public ApiCallContext decorate(ApiCallContext context) {
            return outerContext;
          }
        };
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash, decorator);
    ApiStreamObserver<Integer> observer = Mockito.mock(ApiStreamObserver.class);
    Integer request = 1;
    apiCallable.serverStreamingCall(request, observer);
    Truth.assertThat(stash.getActualObserver()).isSameAs(observer);
    Truth.assertThat(stash.getActualRequest()).isSameAs(request);
    Truth.assertThat(stash.getContext()).isSameAs(outerContext);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCall() {
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash);
    Integer request = 1;
    apiCallable.blockingServerStreamingCall(request);
    Truth.assertThat(stash.getActualRequest()).isSameAs(request);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCallWithContext() {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash);
    Integer request = 1;
    apiCallable.blockingServerStreamingCall(request, context);
    Truth.assertThat(stash.getActualRequest()).isSameAs(request);
    Truth.assertThat(stash.getContext()).isSameAs(context);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIteratedServerStreamingCallWithCallContextDecorator() {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextDecorator decorator =
        new ApiCallContextDecorator() {
          @Override
          public ApiCallContext decorate(ApiCallContext context) {
            return outerContext;
          }
        };
    StreamingStashCallable<Integer, Integer> stash = new StreamingStashCallable<>();
    StreamingCallable<Integer, Integer> apiCallable = StreamingCallable.create(stash, decorator);
    Integer request = 1;
    apiCallable.blockingServerStreamingCall(request);
    Truth.assertThat(stash.getActualRequest()).isSameAs(request);
    Truth.assertThat(stash.getContext()).isSameAs(outerContext);
  }
}
