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

import com.google.api.gax.retrying.RetryingExecutor;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.common.base.Preconditions;

/**
 * A UnaryCallable that will keep issuing calls to an inner callable until a terminal condition is
 * met.
 *
 * <p>Note: Any request or context passed to this class is ignored.
 *
 * <p>Package-private for internal use.
 */
class RecheckingCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
  private final UnaryCallable<RequestT, ResponseT> callable;
  private final RetryingExecutor<ResponseT> executor;

  RecheckingCallable(
      UnaryCallable<RequestT, ResponseT> callable, RetryingExecutor<ResponseT> executor) {
    this.callable = Preconditions.checkNotNull(callable);
    this.executor = Preconditions.checkNotNull(executor);
  }

  @Override
  public RetryingFuture<ResponseT> futureCall(RequestT request, ApiCallContext inputContext) {
    CheckingAttemptCallable<RequestT, ResponseT> checkingAttemptCallable =
        new CheckingAttemptCallable<>(callable);

    RetryingFuture<ResponseT> retryingFuture = executor.createFuture(checkingAttemptCallable);
    checkingAttemptCallable.setExternalFuture(retryingFuture);
    checkingAttemptCallable.call();

    return retryingFuture;
  }

  @Override
  public String toString() {
    return String.format("rechecking(%s)", callable);
  }
}
