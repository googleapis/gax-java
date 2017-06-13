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
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * For internal use only.
 *
 * <p>Basic implementation of {@link RetryingFuture} interface. Suitable for usage in "busy loop"
 * retry implementations.
 *
 * <p>This class is thread-safe.
 */
class BasicRetryingFuture<ResponseT> extends AbstractFuture<ResponseT>
    implements RetryingFuture<ResponseT> {

  final Object lock = new Object();

  private final Callable<ResponseT> callable;

  private final RetryAlgorithm<ResponseT> retryAlgorithm;

  private volatile TimedAttemptSettings attemptSettings;

  private volatile ApiFuture<ResponseT> latestCompletedAttemptResult;
  private volatile ApiFuture<ResponseT> attemptResult;

  BasicRetryingFuture(Callable<ResponseT> callable, RetryAlgorithm<ResponseT> retryAlgorithm) {
    this.callable = checkNotNull(callable);
    this.retryAlgorithm = checkNotNull(retryAlgorithm);

    this.attemptSettings = retryAlgorithm.createFirstAttempt();

    // A micro crime, letting "this" reference to escape from constructor before initialization is
    // completed (via internal non-static class CompletionListener). But it is guaranteed to be ok,
    // since listener is guaranteed to be called only after this future is
    // completed and this future is guaranteed to be completed only after it is initialized. Also
    // since "super" is called explicitly here there are no unexpected overrides of addListener here.
    super.addListener(new CompletionListener(), MoreExecutors.directExecutor());
  }

  @Override
  public void setAttemptFuture(ApiFuture<ResponseT> attemptFuture) {
    throw new UnsupportedOperationException();
  }

  @Override
  // "super." is used here to avoid infinite loops of callback chains
  public void setAttempt(Throwable throwable, ResponseT response) {
    synchronized (lock) {
      try {
        if (throwable instanceof CancellationException) {
          // An attempt triggered cancellation.
          super.cancel(false);
        }
        if (isDone()) {
          return;
        }

        TimedAttemptSettings nextAttemptSettings =
            retryAlgorithm.createNextAttempt(throwable, response, attemptSettings);
        boolean shouldRetry = retryAlgorithm.shouldRetry(throwable, response, nextAttemptSettings);
        if (shouldRetry) {
          attemptSettings = nextAttemptSettings;
          setAttemptResult(false, throwable, response, true);
          // a new attempt will be (must be) scheduled by an external executor
        } else if (throwable != null) {
          super.setException(throwable);
        } else {
          super.set(response);
        }
      } catch (CancellationException e) {
        // A retry algorithm triggered cancellation.
        super.cancel(false);
      } catch (Throwable e) {
        // Should never happen, but still possible in case of buggy retry algorithm implementation.
        // Any bugs/exceptions (except CancellationException) in retry algorithms immediately
        // terminate retrying future and set the result to the thrown exception.
        super.setException(e);
      }
    }
  }

  /** Returns callable tracked by this future. */
  @Override
  public Callable<ResponseT> getCallable() {
    return callable;
  }

  @Override
  public TimedAttemptSettings getAttemptSettings() {
    synchronized (lock) {
      return attemptSettings;
    }
  }

  @Override
  public ApiFuture<ResponseT> peekAttemptResult() {
    synchronized (lock) {
      return latestCompletedAttemptResult;
    }
  }

  // Lazily initializes attempt result. This allows to prevent overhead of relatively
  // heavy (and in most cases redundant) settable future instantiation on each attempt, plus reduces
  // possibility of callback chaining going into an infinite loop in case of buggy external
  // callbacks implementation.
  public ApiFuture<ResponseT> getAttemptResult() {
    synchronized (lock) {
      if (attemptResult == null) {
        attemptResult = new NonCancellableFuture<>();
      }
      return attemptResult;
    }
  }

  // Sets attempt result futures. Note the "attempt result future" and "attempt future" are not same
  // things because there are more attempt futures than attempt result futures.
  // See AttemptCallable.call() for an example of such condition.
  //
  // The assignments order in this method is crucial. Wrong ordering may lead to infinite
  // loops in callback chains.
  //
  // If this is not the last attempt this method sets attemptResult to null, so the next
  // getAttemptResult() call will return a new future, tracking the new attempt. Otherwise
  // attemptResult is set to the same result as the one returned by peekAttemptResult(), indicating
  // that the ultimate unmodifiable result of the whole future was reached.
  private void setAttemptResult(
      boolean cancelled, Throwable throwable, ResponseT response, boolean shouldRetry) {
    ApiFuture<ResponseT> prevAttemptResult = attemptResult;
    try {
      if (cancelled) {
        NonCancellableFuture<ResponseT> future = new NonCancellableFuture<>();
        future.cancelPrivately();
        latestCompletedAttemptResult = future;
        attemptResult = shouldRetry ? null : latestCompletedAttemptResult;
        if (prevAttemptResult instanceof NonCancellableFuture) {
          ((NonCancellableFuture<ResponseT>) prevAttemptResult).cancelPrivately();
        }
      } else if (throwable != null) {
        latestCompletedAttemptResult = ApiFutures.immediateFailedFuture(throwable);
        attemptResult = shouldRetry ? null : latestCompletedAttemptResult;
        if (prevAttemptResult instanceof NonCancellableFuture) {
          ((NonCancellableFuture<ResponseT>) prevAttemptResult).setException(throwable);
        }
      } else {
        latestCompletedAttemptResult = ApiFutures.immediateFuture(response);
        attemptResult = shouldRetry ? null : latestCompletedAttemptResult;
        if (prevAttemptResult instanceof NonCancellableFuture) {
          ((NonCancellableFuture<ResponseT>) prevAttemptResult).set(response);
        }
      }
    } catch (Throwable e) {
      // Usually should not happen but is still possible, for example if one of the attempt callbacks
      // throws an exception. An example of such condition is the OperationFuture
      // which uses ApiFutures.transform(), which actually assign callbacks to the attempt result
      // futures, and those can fail, for example if metadata class is a wrong one.
    }
  }

  private class CompletionListener implements Runnable {
    @Override
    public void run() {
      synchronized (lock) {
        try {
          //setAttemptFuture(null); TODO: prove that this call is not required
          ResponseT response = get();
          setAttemptResult(false, null, response, false);
        } catch (CancellationException e) {
          setAttemptResult(true, null, null, false);
        } catch (ExecutionException e) {
          setAttemptResult(false, e.getCause(), null, false);
        } catch (Throwable e) {
          setAttemptResult(false, e, null, false);
        }
      }
    }
  }
}
