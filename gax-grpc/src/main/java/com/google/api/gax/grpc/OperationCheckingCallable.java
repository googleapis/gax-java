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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.Operation;
import java.util.concurrent.ExecutionException;

/**
 * A future callable which relies on callback chaining to execute server polling (asking for a
 * status of a specific operation: in progress, completed, canceled etc.). This implementation is
 * called from {@link GrpcAttemptCallable}, essentially using for polling the same logic which is
 * used for retrying.
 *
 * <p>Package-private for internal use.
 *
 * @param <RequestT> type of the request
 */
class OperationCheckingCallable<RequestT> extends UnaryCallable<RequestT, Operation> {
  private final UnaryCallable<GetOperationRequest, Operation> callable;
  private final ApiFuture<Operation> initialFuture;

  OperationCheckingCallable(
      UnaryCallable<GetOperationRequest, Operation> callable, ApiFuture<Operation> initialFuture) {
    this.callable = checkNotNull(callable);
    this.initialFuture = checkNotNull(initialFuture);
  }

  /**
   * This method is supposed to be called from {@link GrpcAttemptCallable#call()}
   *
   * @param request request
   * @param context call context
   */
  @Override
  public ApiFuture<Operation> futureCall(RequestT request, ApiCallContext context) {
    try {
      if (!initialFuture.isDone() || initialFuture.isCancelled()) {
        return initialFuture;
      }
      // Since initialFuture is done at this point, the following call should be non-blocking
      Operation initialOperation = initialFuture.get();

      // Note Future.isDone() and Operation.getDone() are two fundamentally different things.
      if (initialOperation.getDone()) {
        return initialFuture;
      }

      GetOperationRequest pollingRequest =
          GetOperationRequest.newBuilder().setName(initialOperation.getName()).build();
      return callable.futureCall(pollingRequest);
    } catch (ExecutionException e) {
      return ApiFutures.immediateFailedFuture(e.getCause());
    } catch (InterruptedException e) {
      return ApiFutures.immediateFailedFuture(e);
    }
  }
}
