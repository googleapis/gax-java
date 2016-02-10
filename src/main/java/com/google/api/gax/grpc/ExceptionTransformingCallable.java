/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
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

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * {@code ExceptionTransformingCallable} transforms all {@code Throwable}s
 * thrown during a call into an instance of {@link ApiException}.
 */
class ExceptionTransformingCallable<RequestT, ResponseT>
    implements FutureCallable<RequestT, ResponseT> {
  private final FutureCallable<RequestT, ResponseT> callable;
  private final ImmutableSet<Status.Code> retryableCodes;

  ExceptionTransformingCallable(
      FutureCallable<RequestT, ResponseT> callable, ImmutableSet<Status.Code> retryableCodes) {
    this.callable = Preconditions.checkNotNull(callable);
    this.retryableCodes = Preconditions.checkNotNull(retryableCodes);
  }

  public ListenableFuture<ResponseT> futureCall(CallContext<RequestT> context) {
    SettableFuture<ResponseT> result = SettableFuture.<ResponseT>create();
    ListenableFuture<ResponseT> innerCall = callable.futureCall(context);
    Futures.addCallback(
        innerCall,
        new FutureCallback<ResponseT>() {
          @Override
          public void onSuccess(ResponseT r) {
            result.set(r);
          }

          @Override
          public void onFailure(Throwable throwable) {
            boolean canRetry = false;
            if (throwable instanceof StatusException) {
              StatusException e = (StatusException) throwable;
              canRetry = retryableCodes.contains(e.getStatus().getCode());
            } else if (throwable instanceof StatusRuntimeException) {
              StatusRuntimeException e = (StatusRuntimeException) throwable;
              canRetry = retryableCodes.contains(e.getStatus().getCode());
            }
            result.setException(new ApiException(throwable, canRetry));
          }
        });
    return result;
  }
}
