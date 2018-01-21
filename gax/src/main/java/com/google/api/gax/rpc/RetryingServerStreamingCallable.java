/*
 * Copyright 2018, Google LLC All rights reserved.
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
package com.google.api.gax.rpc;

import com.google.api.gax.retrying.RetryingServerStream;
import com.google.api.gax.retrying.StreamResumptionStrategy;
import com.google.api.gax.retrying.TimedRetryAlgorithm;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A ServerStreamingCallable that will keep issuing calls to an inner callable until it succeeds or
 * times out. On error, the stream can be resumed from where it left off via a {@link
 * StreamResumptionStrategy}.
 *
 * <p>Package-private for internal use.
 */
class RetryingServerStreamingCallable<RequestT, ResponseT>
    extends ServerStreamingCallable<RequestT, ResponseT> {
  private final ServerStreamingCallable<RequestT, ResponseT> innerCallable;
  private final ScheduledExecutorService executor;
  private final Watchdog<ResponseT> watchdog;
  private final TimedRetryAlgorithm retryAlgorithm;
  private final StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategyPrototype;

  RetryingServerStreamingCallable(
      ServerStreamingCallable<RequestT, ResponseT> innerCallable,
      ScheduledExecutorService executor,
      Watchdog<ResponseT> watchdog,
      TimedRetryAlgorithm retryAlgorithm,
      StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategyPrototype) {
    this.innerCallable = innerCallable;
    this.executor = executor;
    this.watchdog = watchdog;
    this.retryAlgorithm = retryAlgorithm;
    this.resumptionStrategyPrototype = resumptionStrategyPrototype;
  }

  @Override
  public void call(
      RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context) {

    RetryingServerStream<RequestT, ResponseT> retryer =
        RetryingServerStream.<RequestT, ResponseT>newBuilder()
            .setExecutor(executor)
            .setWatchdog(watchdog)
            .setInnerCallable(innerCallable)
            .setRetryAlgorithm(retryAlgorithm)
            .setResumptionStrategy(resumptionStrategyPrototype.createNew())
            .setInitialRequest(request)
            .setContext(context)
            .setOuterObserver(responseObserver)
            .build();

    retryer.start();
  }
}
