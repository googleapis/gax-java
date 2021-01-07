/*
 * Copyright 2021 Google LLC
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
import java.util.concurrent.ScheduledExecutorService;

/**
 * The retry executor which uses {@link ScheduledExecutorService} to schedule an attempt tasks.
 *
 * <p>This implementation does not manage the lifecycle of the underlying {@link
 * ScheduledExecutorService}, so it should be managed outside of this class (like calling the {@link
 * ScheduledExecutorService#shutdown()} when the pool is not needed anymore). In a typical usage
 * pattern there are usually multiple instances of this class sharing same instance of the
 * underlying {@link ScheduledExecutorService}.
 *
 * <p>The executor uses a {@link ContextAwareRetryAlgorithm} to create attempt settings and to
 * determine whether to retry an attempt.
 *
 * <p>This class is thread-safe.
 *
 * @param <ResponseT> response type
 */
public class ContextAwareScheduledRetryingExecutor<ResponseT>
    extends ScheduledRetryingExecutor<ResponseT> {

  private final ContextAwareRetryAlgorithm<ResponseT> retryAlgorithm;

  public ContextAwareScheduledRetryingExecutor(
      ContextAwareRetryAlgorithm<ResponseT> retryAlgorithm, ScheduledExecutorService scheduler) {
    super(retryAlgorithm, scheduler);
    this.retryAlgorithm = retryAlgorithm;
  }

  @Override
  public RetryingFuture<ResponseT> createFuture(
      Callable<ResponseT> callable, RetryingContext context) {
    return new ContextAwareCallbackChainRetryingFuture<>(callable, retryAlgorithm, this, context);
  }
}
