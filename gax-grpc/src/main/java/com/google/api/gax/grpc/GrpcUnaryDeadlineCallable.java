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
package com.google.api.gax.grpc;

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.base.Preconditions;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

public class GrpcUnaryDeadlineCallable<RequestT, ResponseT>
    extends UnaryCallable<RequestT, ResponseT> {
  private final UnaryCallable<RequestT, ResponseT> callable;
  @Nullable private final Duration defaultOverallTimeout;

  GrpcUnaryDeadlineCallable(UnaryCallable<RequestT, ResponseT> callable, Duration overallTimeout) {
    this.callable = Preconditions.checkNotNull(callable);
    this.defaultOverallTimeout = overallTimeout;
  }

  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext inputContext) {
    GrpcCallContext context = GrpcCallContext.createDefault().nullToSelf(inputContext);

    Duration timeout = defaultOverallTimeout;
    if (context.getOverallTimeout() != null) {
      timeout = context.getOverallTimeout();
    }

    if (context.getCallOptions().getDeadline() == null && timeout != null && !timeout.isZero()) {
      context =
          context
              .withOverallTimeout(timeout)
              .withCallOptions(
                  context
                      .getCallOptions()
                      .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    return callable.futureCall(request, context);
  }

  Duration getDefaultOverallTimeout() {
    return defaultOverallTimeout;
  }

  @Override
  public String toString() {
    return String.format("deadline(%s)", callable);
  }
}
