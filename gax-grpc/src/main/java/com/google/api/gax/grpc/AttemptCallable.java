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
package com.google.api.gax.grpc;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.retrying.NonCancellableFuture;
import com.google.api.gax.retrying.RetryingFuture;
import io.grpc.CallOptions;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * A callable representing a retriable grpc call. This class is used from {@link RetryingCallable}.
 *
 * @param <RequestT> request type
 * @param <ResponseT> response type
 */
class AttemptCallable<RequestT, ResponseT> implements Callable<ResponseT> {
  private final FutureCallable<RequestT, ResponseT> callable;
  private final RequestT request;

  private volatile RetryingFuture<ResponseT> externalFuture;
  private volatile CallContext callContext;

  AttemptCallable(
      FutureCallable<RequestT, ResponseT> callable, RequestT request, CallContext callContext) {
    this.callable = callable;
    this.request = request;
    this.callContext = callContext;
  }

  public void setExternalFuture(RetryingFuture<ResponseT> externalFuture) {
    this.externalFuture = externalFuture;
  }

  @Override
  public ResponseT call() {
    try {
      if (callContext != null) {
        callContext =
            getNextCallContext(callContext, externalFuture.getAttemptSettings().getRpcTimeout());
      }
      externalFuture.setAttemptFuture(new NonCancellableFuture<ResponseT>());
      if (externalFuture.isDone()) {
        return null;
      }
      ApiFuture<ResponseT> internalFuture = callable.futureCall(request, callContext);
      externalFuture.setAttemptFuture(internalFuture);
    } catch (Throwable e) {
      externalFuture.setAttemptFuture(ApiFutures.<ResponseT>immediateFailedFuture(e));
    }

    return null;
  }

  private CallContext getNextCallContext(CallContext oldContext, Duration rpcTimeout) {
    CallOptions oldOptions = oldContext.getCallOptions();
    CallOptions newOptions =
        oldOptions.withDeadlineAfter(rpcTimeout.toMillis(), TimeUnit.MILLISECONDS);
    CallContext nextContext = oldContext.withCallOptions(newOptions);

    if (oldOptions.getDeadline() == null) {
      return nextContext;
    }
    if (oldOptions.getDeadline().isBefore(newOptions.getDeadline())) {
      return oldContext;
    }
    return nextContext;
  }
}
