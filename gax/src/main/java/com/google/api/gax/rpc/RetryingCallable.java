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
package com.google.api.gax.rpc;

import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.RetryingExecutor;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.common.base.Preconditions;

/**
 * Implements retry and timeout functionality.
 *
 * <p>The behavior is controlled by the given {@link RetrySettings}.
 *
 * <p>Package-private for internal use.
 */
class RetryingCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
  private final ApiCallContext callContextPrototype;
  private final UnaryCallable<RequestT, ResponseT> callable;
  private final RetryingExecutor<ResponseT> executor;

  RetryingCallable(
      ApiCallContext callContextPrototype,
      UnaryCallable<RequestT, ResponseT> callable,
      RetryingExecutor<ResponseT> executor) {
    this.callContextPrototype = Preconditions.checkNotNull(callContextPrototype);
    this.callable = Preconditions.checkNotNull(callable);
    this.executor = Preconditions.checkNotNull(executor);
  }

  @Override
  public RetryingFuture<ResponseT> futureCall(RequestT request, ApiCallContext inputContext) {
    ApiCallContext context = callContextPrototype.nullToSelf(inputContext);
    AttemptCallable<RequestT, ResponseT> retryCallable =
        new AttemptCallable<>(callable, request, context);

    RetryingFuture<ResponseT> retryingFuture = executor.createFuture(retryCallable);
    retryCallable.setExternalFuture(retryingFuture);
    retryCallable.call();

    return retryingFuture;
  }

  @Override
  public String toString() {
    return String.format("retrying(%s)", callable);
  }
}
