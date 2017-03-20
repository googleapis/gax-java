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
package com.google.api.gax.grpc;

import com.google.api.gax.core.AbstractApiFuture;
import com.google.api.gax.core.ApiClock;
import com.google.api.gax.core.ApiFuture;
import com.google.api.gax.core.ApiFutures;
import com.google.api.gax.core.RetrySettings;
import com.google.api.gax.retrying.ExceptionRetryAlgorithm;
import com.google.api.gax.retrying.ExponentialRetryAlgorithm;
import com.google.api.gax.retrying.RetryAlgorithm;
import com.google.api.gax.retrying.RetryingExecutor;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.api.gax.retrying.ScheduledRetryingExecutor;
import com.google.api.gax.retrying.TimedAttemptSettings;
import com.google.common.base.Preconditions;
import io.grpc.CallOptions;
import io.grpc.Status.Code;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.joda.time.Duration;

/**
 * Implements the retry and timeout functionality used in {@link UnaryCallable}.
 *
 * <p>
 * The behavior is controlled by the given {@link RetrySettings}.
 */
class RetryingCallable<RequestT, ResponseT> implements FutureCallable<RequestT, ResponseT> {
  // Duration to sleep on if the error is DEADLINE_EXCEEDED.
  static final Duration DEADLINE_SLEEP_DURATION = Duration.millis(1);

  private final FutureCallable<RequestT, ResponseT> callable;
  private final RetryingExecutor<ResponseT> scheduler;

  RetryingCallable(
      FutureCallable<RequestT, ResponseT> callable,
      RetrySettings retrySettings,
      ScheduledExecutorService scheduler,
      ApiClock clock) {
    this.callable = Preconditions.checkNotNull(callable);
    RetryAlgorithm retryAlgorithm =
        new RetryAlgorithm(
            new GrpcExceptionRetryAlgorithm(), new ExponentialRetryAlgorithm(retrySettings, clock));
    this.scheduler = new ScheduledRetryingExecutor<>(retryAlgorithm, scheduler);
  }

  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, CallContext context) {
    GrpcRetryCallable<RequestT, ResponseT> retryCallable =
        new GrpcRetryCallable<>(callable, request, context);

    RetryingFuture<ResponseT> retryingFuture = scheduler.createFuture(retryCallable);
    retryCallable.setExternalFuture(retryingFuture);
    retryCallable.call();

    return retryingFuture;
  }

  @Override
  public String toString() {
    return String.format("retrying(%s)", callable);
  }

  private static CallContext getCallContextWithDeadlineAfter(
      CallContext oldContext, Duration rpcTimeout) {
    CallOptions oldOptions = oldContext.getCallOptions();
    CallOptions newOptions =
        oldOptions.withDeadlineAfter(rpcTimeout.getMillis(), TimeUnit.MILLISECONDS);
    CallContext newContext = oldContext.withCallOptions(newOptions);

    if (oldOptions.getDeadlineNanoTime() == null) {
      return newContext;
    }
    if (oldOptions.getDeadlineNanoTime() < newOptions.getDeadlineNanoTime()) {
      return oldContext;
    }
    return newContext;
  }

  private static class GrpcRetryCallable<RequestT, ResponseT> implements Callable<ResponseT> {
    private final FutureCallable<RequestT, ResponseT> callable;
    private final RequestT request;

    private volatile RetryingFuture<ResponseT> externalFuture;
    private volatile CallContext callContext;

    private GrpcRetryCallable(
        FutureCallable<RequestT, ResponseT> callable, RequestT request, CallContext callContext) {
      this.callable = callable;
      this.request = request;
      this.callContext = callContext;
    }

    private void setExternalFuture(RetryingFuture<ResponseT> externalFuture) {
      this.externalFuture = externalFuture;
    }

    @Override
    public ResponseT call() {
      callContext =
          getCallContextWithDeadlineAfter(
              callContext, externalFuture.getAttemptSettings().getRpcTimeout());

      try {
        externalFuture.setAttemptFuture(new NonCancelableFuture<ResponseT>());
        if (externalFuture.isDone()) {
          return null;
        }
        ApiFuture<ResponseT> internalFuture = callable.futureCall(request, callContext);
        externalFuture.setAttemptFuture(internalFuture);
      } catch (Throwable e) {
        externalFuture.setAttemptFuture(ApiFutures.<ResponseT>immediateFailedFuture(e));
        throw e;
      }

      return null;
    }
  }

  private static class GrpcExceptionRetryAlgorithm implements ExceptionRetryAlgorithm {
    @Override
    public TimedAttemptSettings createNextAttempt(
        Throwable prevThrowable, TimedAttemptSettings prevSettings) {
      if (((ApiException) prevThrowable).getStatusCode() == Code.DEADLINE_EXCEEDED) {
        return new TimedAttemptSettings(
            prevSettings.getGlobalSettings(),
            prevSettings.getRetryDelay(),
            prevSettings.getRpcTimeout(),
            DEADLINE_SLEEP_DURATION,
            prevSettings.getAttemptCount() + 1,
            prevSettings.getFirstAttemptStartTime());
      }
      return null;
    }

    @Override
    public boolean accept(Throwable prevThrowable) {
      return (prevThrowable instanceof ApiException)
          && ((ApiException) prevThrowable).isRetryable();
    }
  }

  private static class NonCancelableFuture<ResponseT> extends AbstractApiFuture<ResponseT> {
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }
  }
}
