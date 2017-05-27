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
package com.google.api.gax.grpc;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public abstract class LatchCountDownScheduler implements ScheduledExecutorService {
  public static LatchCountDownScheduler get(
      final CountDownLatch latch, final long scheduleWaitTime) {
    LatchCountDownScheduler mock = Mockito.mock(LatchCountDownScheduler.class);

    // mock class fields:
    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

    // mock class methods:
    // ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);
    when(mock.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .then(
            new Answer<ScheduledFuture<?>>() {
              @Override
              public ScheduledFuture<?> answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                latch.countDown();
                if (scheduleWaitTime > 0L) {
                  Thread.sleep(scheduleWaitTime);
                }

                return executor.schedule((Runnable) args[0], (Long) args[1], (TimeUnit) args[2]);
              }
            });
    // List<Runnable> shutdownNow()
    when(mock.shutdownNow())
        .then(
            new Answer<List<Runnable>>() {
              @Override
              public List<Runnable> answer(InvocationOnMock invocation) throws Throwable {
                return executor.shutdownNow();
              }
            });

    return mock;
  }
}
