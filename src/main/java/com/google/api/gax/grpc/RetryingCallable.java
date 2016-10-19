/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

import com.google.api.gax.core.RetrySettings;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.CallOptions;
import io.grpc.Status;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import org.joda.time.Duration;

/**
 * Implements the retry and timeout functionality used in {@link UnaryApiCallable}. The behavior is
 * controlled by the given {@link RetrySettings}.
 */
class RetryingCallable<RequestT, ResponseT> implements FutureCallable<RequestT, ResponseT> {

  // Duration to sleep on if the error is DEADLINE_EXCEEDED.
  static final Duration DEADLINE_SLEEP_DURATION = Duration.millis(1);

  private final FutureCallable<RequestT, ResponseT> callable;
  private final RetrySettings retryParams;
  private final UnaryApiCallable.Scheduler executor;
  private final NanoClock clock;

  RetryingCallable(
      FutureCallable<RequestT, ResponseT> callable,
      RetrySettings retrySettings,
      UnaryApiCallable.Scheduler executor,
      NanoClock clock) {
    this.callable = Preconditions.checkNotNull(callable);
    this.retryParams = Preconditions.checkNotNull(retrySettings);
    this.executor = executor;
    this.clock = clock;
  }

  @Override
  public ListenableFuture<ResponseT> futureCall(RequestT request, CallContext context) {
    RetryingResultFuture resultFuture = new RetryingResultFuture();
    context = getCallContextWithDeadlineAfter(context, retryParams.getTotalTimeout());
    Retryer retryer =
        new Retryer(
            request,
            context,
            resultFuture,
            retryParams.getInitialRetryDelay(),
            retryParams.getInitialRpcTimeout(),
            null);
    resultFuture.issueCall(request, context, retryer);
    return resultFuture;
  }

  @Override
  public String toString() {
    return String.format("retrying(%s)", callable);
  }

  private class Retryer implements Runnable, FutureCallback<ResponseT> {
    private final RequestT request;
    private final CallContext context;
    private final RetryingResultFuture resultFuture;
    private final Duration retryDelay;
    private final Duration rpcTimeout;
    private final Throwable savedThrowable;

    private Retryer(
        RequestT request,
        CallContext context,
        RetryingResultFuture resultFuture,
        Duration retryDelay,
        Duration rpcTimeout,
        Throwable savedThrowable) {
      this.request = request;
      this.context = context;
      this.resultFuture = resultFuture;
      this.retryDelay = retryDelay;
      this.rpcTimeout = rpcTimeout;
      this.savedThrowable = savedThrowable;
    }

    @Override
    public void run() {
      if (context.getCallOptions().getDeadlineNanoTime() < clock.nanoTime()) {
        if (savedThrowable == null) {
          resultFuture.setException(
              Status.DEADLINE_EXCEEDED
                  .withDescription("Total deadline exceeded without completing any call")
                  .asException());
        } else {
          resultFuture.setException(savedThrowable);
        }
        return;
      }
      CallContext deadlineContext = getCallContextWithDeadlineAfter(context, rpcTimeout);
      resultFuture.issueCall(request, deadlineContext, this);
    }

    @Override
    public void onSuccess(ResponseT r) {
      resultFuture.set(r);
    }

    @Override
    public void onFailure(Throwable throwable) {
      if (!canRetry(throwable)) {
        resultFuture.setException(throwable);
        return;
      }
      if (isDeadlineExceeded(throwable)) {
        Retryer retryer =
            new Retryer(request, context, resultFuture, retryDelay, rpcTimeout, throwable);
        resultFuture.scheduleNext(
            executor, retryer, DEADLINE_SLEEP_DURATION.getMillis(), TimeUnit.MILLISECONDS);
        return;
      }

      long newRetryDelay = (long) (retryDelay.getMillis() * retryParams.getRetryDelayMultiplier());
      newRetryDelay = Math.min(newRetryDelay, retryParams.getMaxRetryDelay().getMillis());

      long newRpcTimeout = (long) (rpcTimeout.getMillis() * retryParams.getRpcTimeoutMultiplier());
      newRpcTimeout = Math.min(newRpcTimeout, retryParams.getMaxRpcTimeout().getMillis());

      long randomRetryDelay = ThreadLocalRandom.current().nextLong(retryDelay.getMillis());
      Retryer retryer =
          new Retryer(
              request,
              context,
              resultFuture,
              Duration.millis(newRetryDelay),
              Duration.millis(newRpcTimeout),
              throwable);
      resultFuture.scheduleNext(executor, retryer, randomRetryDelay, TimeUnit.MILLISECONDS);
    }
  }

  private class RetryingResultFuture extends AbstractFuture<ResponseT> {
    private volatile Future<?> activeFuture = null;
    private volatile boolean cancelled = false;
    private final Lock lock = new ReentrantLock();

    @Override
    protected void interruptTask() {
      final Lock lock = this.lock;
      lock.lock();
      try {
        cancelled = true;
        if (activeFuture != null) {
          activeFuture.cancel(true);
        }
      } finally {
        lock.unlock();
      }
    }

    @Override
    public boolean set(@Nullable ResponseT value) {
      final Lock lock = this.lock;
      lock.lock();
      try {
        activeFuture = null;
        return super.set(value);
      } finally {
        lock.unlock();
      }
    }

    @Override
    public boolean setException(Throwable throwable) {
      final Lock lock = this.lock;
      lock.lock();
      try {
        activeFuture = null;
        if (throwable instanceof CancellationException) {
          if (cancelled) {
            // this is just circling back - ignore.
          } else {
            // somehow someone else got ahold of the call-issuing future and
            // cancelled it.
            super.cancel(false);
          }
          return true;
        } else {
          return super.setException(throwable);
        }
      } finally {
        lock.unlock();
      }
    }

    private void scheduleNext(
        UnaryApiCallable.Scheduler executor, Runnable retryer, long delay, TimeUnit unit) {
      final Lock lock = this.lock;
      lock.lock();
      try {
        if (isCancelledImpl()) {
          return;
        } else {
          activeFuture = executor.schedule(retryer, delay, TimeUnit.MILLISECONDS);
        }
      } finally {
        lock.unlock();
      }
    }

    public void issueCall(
        RequestT request,
        CallContext deadlineContext,
        RetryingCallable<RequestT, ResponseT>.Retryer retryer) {
      final Lock lock = this.lock;
      lock.lock();
      try {
        if (isCancelledImpl()) {
          return;
        } else {
          ListenableFuture<ResponseT> callFuture = callable.futureCall(request, deadlineContext);
          Futures.addCallback(callFuture, retryer);
          activeFuture = callFuture;
        }
      } finally {
        lock.unlock();
      }
    }

    private boolean isCancelledImpl() {
      return cancelled || (activeFuture != null && activeFuture.isCancelled());
    }
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

  private static boolean canRetry(Throwable throwable) {
    if (!(throwable instanceof ApiException)) {
      return false;
    }
    ApiException apiException = (ApiException) throwable;
    return apiException.isRetryable();
  }

  private static boolean isDeadlineExceeded(Throwable throwable) {
    if (!(throwable instanceof ApiException)) {
      return false;
    }
    ApiException apiException = (ApiException) throwable;
    return apiException.getStatusCode() == Status.Code.DEADLINE_EXCEEDED;
  }
}
