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

import com.google.api.gax.core.ApiFuture;
import com.google.api.gax.core.NanoClock;
import com.google.api.gax.core.RetrySettings;
import com.google.api.gax.core.internal.ApiFutureToListenableFuture;
import com.google.api.gax.retrying.RetryAttemptSettings;
import com.google.api.gax.retrying.RetryFuture;
import com.google.api.gax.retrying.ScheduledRetryHandler;
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
 * The behavior is controlled by the given {@link RetrySettings}.
 */
class RetryingCallable<RequestT, ResponseT> implements FutureCallable<RequestT, ResponseT> {
  // Duration to sleep on if the error is DEADLINE_EXCEEDED.
  static final Duration DEADLINE_SLEEP_DURATION = Duration.millis(1);

  private final FutureCallable<RequestT, ResponseT> callable;
  private final RetrySettings retryParams;
  private final ScheduledExecutorService scheduler;
  private final NanoClock clock;

  RetryingCallable(
      FutureCallable<RequestT, ResponseT> callable,
      RetrySettings retrySettings,
      ScheduledExecutorService scheduler,
      NanoClock clock) {
    this.callable = Preconditions.checkNotNull(callable);
    this.retryParams = Preconditions.checkNotNull(retrySettings);
    this.scheduler = scheduler;
    this.clock = clock;
  }

  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, CallContext context) {
    GrpcRetryCallable<RequestT, ResponseT> retryCallable =
        new GrpcRetryCallable<>(callable, request, context);
    GrpcRetryHandler<ResponseT> retryHelper = new GrpcRetryHandler<>(clock, scheduler);
    RetryFuture<ResponseT> rv = retryHelper.createFirstAttempt(retryCallable, retryParams);
    retryCallable.setExternalFuture(rv);
    retryCallable.call();
    return rv;
  }

  @Override
  public String toString() {
    return String.format("retrying(%s)", callable);
  }

  private static CallContext getCallContextWithDeadlineAfter(
      CallContext oldCtx, Duration rpcTimeout) {
    CallOptions oldOpt = oldCtx.getCallOptions();
    CallOptions newOpt = oldOpt.withDeadlineAfter(rpcTimeout.getMillis(), TimeUnit.MILLISECONDS);
    CallContext newCtx = oldCtx.withCallOptions(newOpt);

    if (oldOpt.getDeadlineNanoTime() == null) {
      return newCtx;
    }
    if (oldOpt.getDeadlineNanoTime() < newOpt.getDeadlineNanoTime()) {
      return oldCtx;
    }
    return newCtx;
  }

  private static class GrpcRetryCallable<RequestT, ResponseT> implements Callable<ResponseT> {
    private final FutureCallable<RequestT, ResponseT> callable;
    private final RequestT request;

    private RetryFuture<ResponseT> externalFuture;
    private CallContext callContext;

    private GrpcRetryCallable(
        FutureCallable<RequestT, ResponseT> callable, RequestT request, CallContext callContext) {
      this.callable = callable;
      this.request = request;
      this.callContext = callContext;
    }

    private void setExternalFuture(RetryFuture<ResponseT> externalFuture) {
      this.externalFuture = externalFuture;
    }

    @Override
    public ResponseT call() {
      callContext =
          getCallContextWithDeadlineAfter(
              callContext, externalFuture.getAttemptSettings().getRpcTimeout());
      ApiFuture<ResponseT> internalFuture = callable.futureCall(request, callContext);
      externalFuture.setAttemptFuture(new ApiFutureToListenableFuture<>(internalFuture));
      return null;
    }
  }

  private static class GrpcRetryHandler<ResponseT> extends ScheduledRetryHandler<ResponseT> {
    private GrpcRetryHandler(NanoClock clock, ScheduledExecutorService scheduler) {
      super(clock, scheduler);
    }

    @Override
    public boolean accept(Throwable e, RetryAttemptSettings nextAttemptSettings) {
      return super.accept(e, nextAttemptSettings)
          && (e instanceof ApiException)
          && ((ApiException) e).isRetryable();
    }

    @Override
    public RetryAttemptSettings createNextAttemptSettings(
        Throwable e, RetryAttemptSettings prevSettings) {
      if (((ApiException) e).getStatusCode() == Code.DEADLINE_EXCEEDED) {
        return new RetryAttemptSettings(
            prevSettings.getGlobalSettings(),
            prevSettings.getRetryDelay(),
            prevSettings.getRpcTimeout(),
            DEADLINE_SLEEP_DURATION,
            prevSettings.getAttemptCount() + 1,
            prevSettings.getFirstAttemptStartTime());
      }

      return super.createNextAttemptSettings(e, prevSettings);
    }
  }
}
