/*
 * Copyright 2018 Google LLC
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

import com.google.api.core.BetaApi;
import com.google.api.core.InternalExtensionOnly;
import java.util.concurrent.Callable;
import javax.annotation.Nonnull;

/**
 * A retrying executor is responsible for the following operations:
 *
 * <ol>
 *   <li>Creating first attempt {@link RetryingFuture}, which acts as a facade, hiding from client
 *       code the actual execution of scheduled retry attempts.
 *   <li>Executing the actual {@link Callable} in a retriable context.
 * </ol>
 *
 * <p>This interface is for internal/advanced use only. It builds on {@link RetryingExecutor} to
 * allow passing of per invocation state to the {@link RetryingFuture}.
 *
 * <p>This interface considered unstable and users are encouraged to use the concrete
 * implementations like {@link ScheduledRetryingExecutor} directly.
 *
 * @param <ResponseT> response type
 */
// TODO(igorbernstein2): Consider replacing this with a default implementation in RetryingExecutor
// once support for java 7 is dropped
@BetaApi("The surface for per invocation state is unstable and will probably change in the future")
@InternalExtensionOnly
public interface RetryingExecutorWithContext<ResponseT> extends RetryingExecutor<ResponseT> {
  RetryingFuture<ResponseT> createFuture(
      @Nonnull Callable<ResponseT> callable, @Nonnull RetryingContext retryingContext);
}
