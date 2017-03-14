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

import com.google.api.gax.core.ApiFutureCallback;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * For internal use only.
 *
 * This class is the key component of the retry logic. It implements the {@link RetryFuture} facade
 * interface, and does the following:
 *
 * <ul>
 * <li>Schedules the next attempt in case of a failure using the callback chaining technique.</li>
 * <li>Terminates retrying process if no more retries are accepted.</li>
 * <li>Propagates future cancellation in both directions (from this to the attempt and from the
 * attempt to this)</li>
 * </ul>
 *
 * This class is thread-safe.
 */
class RetryFutureImpl<ResponseT> extends AbstractFuture<ResponseT>
    implements RetryFuture<ResponseT> {

  private final Object lock = new Object();
  private final Callable<ResponseT> callable;
  private final RetryHandler<ResponseT> retryHandler;

  private volatile RetryAttemptSettings attemptSettings;
  private volatile AttemptFutureCallback callbackFutureCallback;

  RetryFutureImpl(
      Callable<ResponseT> callable,
      RetryAttemptSettings attemptSettings,
      RetryHandler<ResponseT> retryHandler) {
    this.callable = callable;
    this.attemptSettings = attemptSettings;
    this.retryHandler = retryHandler;
  }

  @Override
  protected boolean set(ResponseT value) {
    synchronized (lock) {
      return super.set(value);
    }
  }

  @Override
  protected boolean setException(Throwable throwable) {
    synchronized (lock) {
      return super.setException(throwable);
    }
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    synchronized (lock) {
      if (callbackFutureCallback != null) {
        boolean rv = callbackFutureCallback.attemptFuture.cancel(mayInterruptIfRunning);
        super.cancel(mayInterruptIfRunning);
        return rv;
      } else {
        return super.cancel(mayInterruptIfRunning);
      }
    }
  }

  @Override
  public RetryAttemptSettings getAttemptSettings() {
    synchronized (lock) {
      return attemptSettings;
    }
  }

  @Override
  public void setAttemptFuture(Future<ResponseT> attemptFuture) {
    synchronized (lock) {
      if (attemptFuture != null) {
        callbackFutureCallback = new AttemptFutureCallback(attemptFuture);
        Futures.addCallback((ListenableFuture) attemptFuture, callbackFutureCallback);
        if (isCancelled()) {
          attemptFuture.cancel(false);
        }
      } else {
        callbackFutureCallback = null;
      }
    }
  }

  private void executeAttempt(Throwable delegateThrowable, Future<ResponseT> prevAttemptFuture) {
    try {
      if (prevAttemptFuture.isCancelled()) {
        cancel(false);
      }
      if (isDone()) {
        return;
      }

      RetryAttemptSettings nextAttemptSettings =
          retryHandler.createNextAttemptSettings(delegateThrowable, attemptSettings);
      if (retryHandler.accept(delegateThrowable, nextAttemptSettings)) {
        attemptSettings = nextAttemptSettings;
        Future<ResponseT> nextInternalFuture =
            retryHandler.executeAttempt(callable, attemptSettings);
        setAttemptFuture(nextInternalFuture);
      } else {
        setException(delegateThrowable);
      }
    } catch (Throwable e) {
      setException(delegateThrowable);
    }
  }

  private class AttemptFutureCallback
      implements FutureCallback<ResponseT>, ApiFutureCallback<ResponseT> {

    private Future<ResponseT> attemptFuture;

    private AttemptFutureCallback(Future<ResponseT> attemptFuture) {
      this.attemptFuture = attemptFuture;
    }

    @Override
    public void onSuccess(ResponseT result) {
      if (this == callbackFutureCallback && !isDone()) {
        synchronized (lock) {
          if (this == callbackFutureCallback && !isDone()) {
            setAttemptFuture(null);
            set(result);
          }
        }
      }
    }

    @Override
    public void onFailure(Throwable t) {
      if (this == callbackFutureCallback && !isDone()) {
        synchronized (lock) {
          if (this == callbackFutureCallback && !isDone()) {
            setAttemptFuture(null);
            executeAttempt(t, this.attemptFuture);
          }
        }
      }
    }
  }
}
