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

package com.google.api.gax.retrying;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * For internal use only.
 *
 * <p>This class is the key component of the retry logic. It implements the {@link RetryingFuture}
 * facade interface, and does the following:
 *
 * <ul>
 *   <li>Schedules the next attempt in case of a failure using the callback chaining technique.
 *   <li>Terminates retrying process if no more retries are accepted.
 *   <li>Propagates future cancellation in both directions (from this to the attempt and from the
 *       attempt to this)
 * </ul>
 *
 * This class is thread-safe.
 */
final class RetryingFutureImpl<ResponseT> extends AbstractFuture<ResponseT>
    implements RetryingFuture<ResponseT> {
  private final Object lock = new Object();

  private final Callable<ResponseT> callable;

  private final RetryAlgorithm<ResponseT> retryAlgorithm;
  private final RetryingExecutor<ResponseT> retryingExecutor;

  private volatile TimedAttemptSettings attemptSettings;
  private volatile AttemptFutureCallback attemptFutureCallback;

  private volatile boolean cancellationInterruptStatus;

  RetryingFutureImpl(
      Callable<ResponseT> callable,
      RetryAlgorithm<ResponseT> retryAlgorithm,
      RetryingExecutor<ResponseT> retryingExecutor) {
    this.callable = checkNotNull(callable);
    this.retryAlgorithm = checkNotNull(retryAlgorithm);
    this.retryingExecutor = checkNotNull(retryingExecutor);

    this.attemptSettings = retryAlgorithm.createFirstAttempt();
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    synchronized (lock) {
      if (attemptFutureCallback != null) {
        if (attemptFutureCallback.attemptFuture.cancel(mayInterruptIfRunning)) {
          cancellationInterruptStatus = mayInterruptIfRunning;
          super.cancel(mayInterruptIfRunning);
        }
        return isCancelled();
      } else {
        cancellationInterruptStatus = mayInterruptIfRunning;
        return super.cancel(mayInterruptIfRunning);
      }
    }
  }

  @Override
  public void setAttemptFuture(ApiFuture<ResponseT> attemptFuture) {
    if (isDone()) {
      return;
    }
    synchronized (lock) {
      if (isDone()) {
        return;
      }
      if (attemptFuture != null) {
        attemptFutureCallback = new AttemptFutureCallback(attemptFuture);
        ApiFutures.addCallback(attemptFuture, attemptFutureCallback);
        if (isCancelled()) {
          attemptFuture.cancel(cancellationInterruptStatus);
        }
      } else {
        attemptFutureCallback = null;
      }
    }
  }

  @Override
  public TimedAttemptSettings getAttemptSettings() {
    synchronized (lock) {
      return attemptSettings;
    }
  }

  @Override
  public Callable<ResponseT> getCallable() {
    return callable;
  }

  private void handleAttemptResult(
      Throwable throwable, ResponseT response, Future<ResponseT> prevAttemptFuture) {
    try {
      if (prevAttemptFuture.isCancelled()) {
        cancel(false);
      }
      if (isDone()) {
        return;
      }

      TimedAttemptSettings nextAttemptSettings =
          retryAlgorithm.createNextAttempt(throwable, response, attemptSettings);

      if (retryAlgorithm.shouldCancel(throwable, response, nextAttemptSettings)) {
        cancel(false);
        return;
      }

      if (retryAlgorithm.shouldRetry(throwable, response, nextAttemptSettings)) {
        attemptSettings = nextAttemptSettings;
        retryingExecutor.submit(this);
      } else if (throwable != null) {
        setException(throwable);
      } else {
        set(response);
      }
    } catch (Throwable e) {
      setException(throwable != null ? throwable : e);
    }
  }

  private class AttemptFutureCallback
      implements FutureCallback<ResponseT>, ApiFutureCallback<ResponseT> {

    private Future<ResponseT> attemptFuture;

    private AttemptFutureCallback(Future<ResponseT> attemptFuture) {
      this.attemptFuture = attemptFuture;
    }

    @Override
    public void onSuccess(ResponseT response) {
      handle(null, response);
    }

    @Override
    public void onFailure(Throwable t) {
      handle(t, null);
    }

    private void handle(Throwable t, ResponseT response) {
      if (this != attemptFutureCallback || isDone()) {
        return;
      }
      synchronized (lock) {
        if (this != attemptFutureCallback || isDone()) {
          return;
        }
        setAttemptFuture(null);
        handleAttemptResult(t, response, this.attemptFuture);
      }
    }
  }
}
