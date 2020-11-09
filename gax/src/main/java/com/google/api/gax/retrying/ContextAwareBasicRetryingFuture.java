/*
 * Copyright 2020 Google LLC
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
package com.google.api.gax.retrying;

import java.util.concurrent.Callable;

/**
 * {@link BasicRetryingFuture} implementation that will use {@link RetrySettings} and retryable
 * codes from the {@link RetryingContext} if they have been set.
 */
class ContextAwareBasicRetryingFuture<ResponseT> extends BasicRetryingFuture<ResponseT> {

  ContextAwareBasicRetryingFuture(
      Callable<ResponseT> callable,
      ContextAwareRetryAlgorithm<ResponseT> retryAlgorithm,
      RetryingContext context) {
    super(callable, retryAlgorithm, context);
  }

  @Override
  ContextAwareRetryAlgorithm<ResponseT> getRetryAlgorithm() {
    return (ContextAwareRetryAlgorithm<ResponseT>) super.getRetryAlgorithm();
  }

  @Override
  TimedAttemptSettings createFirstAttempt(RetryingContext context) {
    return getRetryAlgorithm().createFirstAttempt(context);
  }

  @Override
  TimedAttemptSettings createNextAttempt(
      RetryingContext context, Throwable throwable, ResponseT response) {
    return getRetryAlgorithm()
        .createNextAttempt(context, throwable, response, getAttemptSettings());
  }

  @Override
  boolean shouldRetry(
      RetryingContext context,
      Throwable throwable,
      ResponseT response,
      TimedAttemptSettings nextAttemptSettings) {
    return getRetryAlgorithm().shouldRetry(context, throwable, response, nextAttemptSettings);
  }

  @Override
  boolean shouldRetryOnResult(RetryingContext context, Throwable throwable, ResponseT response) {
    return getRetryAlgorithm().getResultAlgorithm().shouldRetry(context, throwable, response);
  }
}
