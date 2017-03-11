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

import com.google.api.gax.core.NanoClock;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A retry handler which uses {@link ScheduledExecutorService} to schedule attempt tasks. Unless a
 * direct executor service is used, this handler will schedule attempts for an execution in another
 * thread.
 *
 * This class is thread-safe.
 *
 * @param <ResponseT>
 */
public class ScheduledRetryHandler<ResponseT> extends AbstractRetryHandler<ResponseT> {
  private final ListeningScheduledExecutorService scheduler;

  /**
   * Creates new scheduled retry handler, which will be using {@link ScheduledExecutorService} for
   * acutal attempts scheduling.
   *
   * @param clock clock to use for scheduling operations
   * @param scheduler scheduler
   */
  public ScheduledRetryHandler(NanoClock clock, ScheduledExecutorService scheduler) {
    super(clock);
    this.scheduler = MoreExecutors.listeningDecorator(scheduler);
  }

  /**
   * Executes attempt using previously provided shceduller.
   *
   * @param callable the actual callable to execute
   * @param attemptSettings current attempt settings
   * @return actual attempt future
   */
  @Override
  public Future<ResponseT> executeAttempt(
      Callable<ResponseT> callable, RetryAttemptSettings attemptSettings) {
    try {
      System.out.println(
          "Scheduling with delay = " + attemptSettings.getRandomizedRetryDelay().getMillis());

      return scheduler.schedule(
          callable, attemptSettings.getRandomizedRetryDelay().getMillis(), TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      System.out.println("Rejected");
      return Futures.immediateCancelledFuture();
    }
  }
}
