/*
 * Copyright 2015, Google Inc.
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

package io.gapi.gax.grpc;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import io.grpc.CallOptions;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.Set;

/**
 * {@code RetryingCallable} provides retry/timeout functionality to {@link FutureCallable}.
 * The behavior is controlled by the given {@link RetryParams}.
 */
class RetryingCallable<RequestT, ResponseT> implements FutureCallable<RequestT, ResponseT> {
  private static final int THREAD_POOL_SIZE = 10;
  private static final ScheduledExecutorService executor =
      Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
  private static final Set<Status> retryableStatuses =
      ImmutableSet.of(Status.DEADLINE_EXCEEDED, Status.UNAVAILABLE);

  private final FutureCallable<RequestT, ResponseT> callable;
  private final RetryParams retryParams;

  RetryingCallable(FutureCallable<RequestT, ResponseT> callable, RetryParams retryParams) {
    this.callable = callable;
    this.retryParams = retryParams;
  }

  public ListenableFuture<ResponseT> futureCall(CallContext<RequestT> context) {
    SettableFuture<ResponseT> result = SettableFuture.<ResponseT>create();
    context =
        getCallContextWithDeadlineAfter(
            context, retryParams.getTotalTimeout(), TimeUnit.MILLISECONDS);
    Retryer retryer =
        new Retryer(
            context,
            result,
            retryParams.getInitialRetryDelay(),
            retryParams.getInitialRpcTimeout(),
            null);
    retryer.run();
    return result;
  }

  public String toString() {
    return String.format("retrying(%s)", callable);
  }

  private class Retryer implements Runnable {
    private final CallContext<RequestT> context;
    private final SettableFuture<ResponseT> result;
    private final long retryDelay;
    private final long rpcTimeout;
    private final Throwable savedThrowable;

    private Retryer(
        CallContext<RequestT> context,
        SettableFuture<ResponseT> result,
        long retryDelay,
        long rpcTimeout,
        Throwable savedThrowable) {
      this.context = context;
      this.result = result;
      this.retryDelay = retryDelay;
      this.rpcTimeout = rpcTimeout;
      this.savedThrowable = savedThrowable;
    }

    public void run() {
      if (context.getCallOptions().getDeadlineNanoTime() < System.nanoTime()) {
        if (savedThrowable == null) {
          result.setException(
              Status.DEADLINE_EXCEEDED
                  .withDescription("Total deadline exceeded without completing any call")
                  .asException());
        } else {
          result.setException(savedThrowable);
        }
        return;
      }
      CallContext<RequestT> deadlineContext =
          getCallContextWithDeadlineAfter(context, rpcTimeout, TimeUnit.MILLISECONDS);
      Futures.addCallback(
          callable.futureCall(deadlineContext),
          new FutureCallback<ResponseT>() {
            @Override
            public void onSuccess(ResponseT r) {
              result.set(r);
            }

            @Override
            public void onFailure(Throwable throwable) {
              if (!canRetry(throwable)) {
                result.setException(throwable);
                return;
              }
              long newRetryDelay = (long) (retryDelay * retryParams.getRetryDelayMult());
              newRetryDelay = Math.min(newRetryDelay, retryParams.getMaxRetryDelay());

              long newRpcTimeout = (long) (rpcTimeout * retryParams.getRpcTimeoutMult());
              newRpcTimeout = Math.min(newRpcTimeout, retryParams.getMaxRpcTimeout());

              long randomRetryDelay = ThreadLocalRandom.current().nextLong(retryDelay);

              Retryer retryer =
                  new Retryer(context, result, newRetryDelay, newRpcTimeout, throwable);
              executor.schedule(retryer, randomRetryDelay, TimeUnit.MILLISECONDS);
            }
          });
    }
  }

  private static <T> CallContext<T> getCallContextWithDeadlineAfter(
      CallContext<T> oldCtx, long duration, TimeUnit unit) {
    CallOptions oldOpt = oldCtx.getCallOptions();
    CallOptions newOpt = oldOpt.withDeadlineAfter(duration, unit);
    CallContext<T> newCtx = oldCtx.withCallOptions(newOpt);

    if (oldOpt.getDeadlineNanoTime() == null) {
      return newCtx;
    }
    if (oldOpt.getDeadlineNanoTime() < newOpt.getDeadlineNanoTime()) {
      return oldCtx;
    }
    return newCtx;
  }

  private static boolean canRetry(Throwable throwable) {
    if (throwable instanceof StatusException) {
      StatusException e = (StatusException) throwable;
      return retryableStatuses.contains(e.getStatus());
    }
    if (throwable instanceof StatusRuntimeException) {
      StatusRuntimeException e = (StatusRuntimeException) throwable;
      return retryableStatuses.contains(e.getStatus());
    }
    return false;
  }
}
