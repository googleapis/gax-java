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

import com.google.api.core.ApiClock;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.retrying.ExponentialRetryAlgorithm;
import com.google.api.gax.retrying.NoOpResponseRetryAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.TimedAttemptSettings;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import io.grpc.Status;
import java.util.concurrent.ExecutionException;

class OperationPollingCallable<RequestT> implements FutureCallable<RequestT, Operation> {
  private final OperationsClient operationsClient;
  private final ApiFuture<Operation> initialFuture;

  OperationPollingCallable(ApiFuture<Operation> initialFuture, OperationsClient operationsClient) {
    this.initialFuture = initialFuture;
    this.operationsClient = operationsClient;
  }

  @Override
  public ApiFuture<Operation> futureCall(RequestT request, CallContext context) {
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
      return operationsClient.getOperationCallable().futureCall(pollingRequest);

    } catch (ExecutionException e) {
      return ApiFutures.immediateFailedFuture(e.getCause());
    } catch (InterruptedException e) {
      return ApiFutures.immediateFailedFuture(e);
    }
  }

  public static class OperationRetryAlgorithm extends NoOpResponseRetryAlgorithm<Operation> {
    @Override
    public boolean shouldRetry(Operation prevResponse) {
      return !prevResponse.getDone();
    }

    @Override
    public boolean shouldCancel(Operation prevResponse) {
      if (prevResponse.getError() != null) {
        Status status = Status.fromCodeValue(prevResponse.getError().getCode());
        return status.getCode().equals(Status.Code.CANCELLED);
      }
      return false;
    }
  }

  public static class OperationTimedAlgorithm extends ExponentialRetryAlgorithm {
    public OperationTimedAlgorithm(RetrySettings globalSettings, ApiClock clock) {
      super(globalSettings, clock);
    }

    @Override
    public boolean shouldCancel(TimedAttemptSettings nextAttemptSettings) {
      return !super.shouldRetry(nextAttemptSettings);
    }
  }
}
