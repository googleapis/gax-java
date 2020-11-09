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

import static org.mockito.Mockito.mock;

import com.google.api.gax.tracing.ApiTracer;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class ContextAwareBasicRetryingFutureTest {

  @Test
  public void testUsesRetryingContext() throws Exception {
    @SuppressWarnings("unchecked")
    Callable<Integer> callable = mock(Callable.class);
    @SuppressWarnings("unchecked")
    ContextAwareRetryAlgorithm<Integer> retryAlgorithm = mock(ContextAwareRetryAlgorithm.class);
    RetryingContext retryingContext = mock(RetryingContext.class);
    ApiTracer tracer = mock(ApiTracer.class);
    TimedAttemptSettings timedAttemptSettings = mock(TimedAttemptSettings.class);
    Mockito.when(retryingContext.getTracer()).thenReturn(tracer);

    Mockito.when(retryAlgorithm.createFirstAttempt(retryingContext)).thenReturn(timedAttemptSettings);
    Mockito.when(
            retryAlgorithm.createNextAttempt(
                ArgumentMatchers.eq(retryingContext),
                ArgumentMatchers.<Throwable>any(),
                ArgumentMatchers.<Integer>any(),
                ArgumentMatchers.<TimedAttemptSettings>any()))
        .thenReturn(timedAttemptSettings);
    Mockito.when(
            retryAlgorithm.shouldRetry(
                ArgumentMatchers.eq(retryingContext),
                ArgumentMatchers.<Throwable>any(),
                ArgumentMatchers.<Integer>any(),
                ArgumentMatchers.<TimedAttemptSettings>any()))
        .thenReturn(true);

    ContextAwareBasicRetryingFuture<Integer> future =
        new ContextAwareBasicRetryingFuture<>(callable, retryAlgorithm, retryingContext);

    future.handleAttempt(null, null);
    
    Mockito.verify(retryAlgorithm).createFirstAttempt(retryingContext);
    Mockito.verify(retryAlgorithm).createNextAttempt(
                ArgumentMatchers.eq(retryingContext),
                ArgumentMatchers.<Throwable>any(),
                ArgumentMatchers.<Integer>any(),
                ArgumentMatchers.<TimedAttemptSettings>any());
    Mockito.verify(retryAlgorithm).shouldRetry(
            ArgumentMatchers.eq(retryingContext),
            ArgumentMatchers.<Throwable>any(),
            ArgumentMatchers.<Integer>any(),
            ArgumentMatchers.<TimedAttemptSettings>any());
    Mockito.verifyNoMoreInteractions(retryAlgorithm);
  }
}
