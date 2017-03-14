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

import com.google.api.gax.core.RetrySettings;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * A retry handler is responsible for the following operations:
 * <ol>
 * <li>Accepting or rejecting a task for retry depending on the previous attempt result (exception)
 * and/or other attempt properties (like number of already executed attempts or total time spent
 * retrying).</li>
 *
 * <li>Creating first attempt {@link RetryFuture}, which acts as a facade, hiding from client code
 * the actual scheduled retry attempts execution.</li>
 *
 * <li>Creating {@link RetryAttemptSettings} for each subsequent retry attempt.</li>
 *
 * <li>Executing the actual {@link Callable} in a retriable context.</li>
 *
 * </ol>
 *
 * This interface is for internal/advanced use only.
 *
 * @param <ResponseT> response type
 */
public interface RetryHandler<ResponseT> {

  /**
   * Returns {@code true} if another attempt should be made, or {@code false} otherwise.
   *
   * @param e exception thrown by the previous attempt
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   * accepted
   * @return {@code true} if another attempt should be made, or {@code false} otherwise
   */
  boolean accept(Throwable e, RetryAttemptSettings nextAttemptSettings);

  /**
   * Creates a first try {@link RetryFuture}, which is a facade, returned to the client code to wait
   * for any retriable operation to complete.
   *
   * @param callable the actual callable, which should be executed in a retriable context
   * @param globalSettings global retry settings (attempt independent)
   * @return retriable future facade
   */
  RetryFuture<ResponseT> createFirstAttempt(
      Callable<ResponseT> callable, RetrySettings globalSettings);

  /**
   * Creates the next attempt {@link RetryAttemptSettings}, which defines properties of the next
   * attempt. Eventually this object will be passed to
   * {@link RetryHandler#accept(Throwable, RetryAttemptSettings)} and
   * {@link RetryHandler#executeAttempt(Callable, RetryAttemptSettings)}.
   *
   * @param e exception thrown by the previous attempt
   * @param prevSettings previous attempt settings
   * @return next attempt settings
   */
  RetryAttemptSettings createNextAttemptSettings(Throwable e, RetryAttemptSettings prevSettings);

  /**
   * Executes an attempt. A typical implementation will either try to execute in the current thread
   * or schedule it for an execution, using some sort of async execution service.
   *
   * @param callable the actual callable to execute
   * @param attemptSettings current attempt settings
   * @return the {@link Future}, representing the scheduled execution
   */
  Future<ResponseT> executeAttempt(
      Callable<ResponseT> callable, RetryAttemptSettings attemptSettings);
}
