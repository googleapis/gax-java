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

import com.google.api.gax.rpc.testing.FakeOperationApi.FakeOperation;
import com.google.api.gax.rpc.testing.FakeOperationApi.OperationStashCallable;
import com.google.common.truth.Truth;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class EntryPointOperationCallableTest {

  @Test
  public void call() throws Exception {
    ApiCallContext defaultCallContext = new ApiCallContext() {};
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(stashCallable, defaultCallContext);

    String response = callable.call(1);
    Truth.assertThat(response).isEqualTo("1");
    Truth.assertThat(stashCallable.getContext()).isSameAs(defaultCallContext);
  }

  @Test
  public void callWithContext() throws Exception {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(stashCallable, new ApiCallContext() {});

    String response = callable.call(2, context);
    Truth.assertThat(response).isEqualTo("2");
    Truth.assertThat(stashCallable.getContext()).isSameAs(context);
  }

  @Test
  public void callWithCallContextEnhancer() throws Exception {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextEnhancer enhancer =
        new ApiCallContextEnhancer() {
          @Override
          public ApiCallContext enhance(ApiCallContext context) {
            return outerContext;
          }
        };
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(
            stashCallable, new ApiCallContext() {}, Collections.singletonList(enhancer));

    String response = callable.call(3);
    Truth.assertThat(response).isEqualTo("3");
    Truth.assertThat(stashCallable.getContext()).isSameAs(outerContext);
  }

  @Test
  public void callResume() throws Exception {
    ApiCallContext defaultCallContext = new ApiCallContext() {};
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(stashCallable, defaultCallContext);

    OperationFuture<String, Long, FakeOperation> operationFuture = callable.futureCall(45);

    String response = callable.resumeFutureCall(operationFuture.getName()).get();
    Truth.assertThat(response).isEqualTo("45");
    Truth.assertThat(stashCallable.getResumeContext()).isSameAs(defaultCallContext);
  }

  @Test
  public void callResumeWithContext() throws Exception {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(stashCallable, new ApiCallContext() {});

    OperationFuture<String, Long, FakeOperation> operationFuture = callable.futureCall(45);

    String response = callable.resumeFutureCall(operationFuture.getName(), context).get();
    Truth.assertThat(response).isEqualTo("45");
    Truth.assertThat(stashCallable.getResumeContext()).isSameAs(context);
  }

  @Test
  public void callResumeWithCallContextDecorator() throws Exception {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextEnhancer enhancer =
        new ApiCallContextEnhancer() {
          @Override
          public ApiCallContext enhance(ApiCallContext context) {
            return outerContext;
          }
        };
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(
            stashCallable, new ApiCallContext() {}, Collections.singletonList(enhancer));

    OperationFuture<String, Long, FakeOperation> operationFuture = callable.futureCall(45);

    String response = callable.resumeFutureCall(operationFuture.getName()).get();
    Truth.assertThat(response).isEqualTo("45");
    Truth.assertThat(stashCallable.getResumeContext()).isSameAs(outerContext);
  }

  @Test
  public void callCancel() throws Exception {
    ApiCallContext defaultCallContext = new ApiCallContext() {};
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(stashCallable, defaultCallContext);

    OperationFuture<String, Long, FakeOperation> operationFuture = callable.futureCall(45);

    callable.cancel(operationFuture.getName()).get();
    Truth.assertThat(stashCallable.wasCancelCalled()).isTrue();
    Truth.assertThat(stashCallable.getCancelContext()).isSameAs(defaultCallContext);
  }

  @Test
  public void callCancelWithContext() throws Exception {
    ApiCallContext context = Mockito.mock(ApiCallContext.class);
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(stashCallable, new ApiCallContext() {});

    OperationFuture<String, Long, FakeOperation> operationFuture = callable.futureCall(45);

    callable.cancel(operationFuture.getName(), context).get();
    Truth.assertThat(stashCallable.wasCancelCalled()).isTrue();
    Truth.assertThat(stashCallable.getCancelContext()).isSameAs(context);
  }

  @Test
  public void callCancelWithCallContextDecorator() throws Exception {
    final ApiCallContext outerContext = Mockito.mock(ApiCallContext.class);
    ApiCallContextEnhancer enhancer =
        new ApiCallContextEnhancer() {
          @Override
          public ApiCallContext enhance(ApiCallContext context) {
            return outerContext;
          }
        };
    OperationStashCallable stashCallable = new OperationStashCallable();
    OperationCallable<Integer, String, Long, FakeOperation> callable =
        new EntryPointOperationCallable<>(
            stashCallable, new ApiCallContext() {}, Collections.singletonList(enhancer));

    OperationFuture<String, Long, FakeOperation> operationFuture = callable.futureCall(45);

    callable.cancel(operationFuture.getName()).get();
    Truth.assertThat(stashCallable.wasCancelCalled()).isTrue();
    Truth.assertThat(stashCallable.getCancelContext()).isSameAs(outerContext);
  }
}
