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

import com.google.api.core.ApiClock;
import com.google.api.core.BetaApi;
import com.google.api.core.NanoClock;
import com.google.api.gax.retrying.ExponentialRetryAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.TimedAttemptSettings;
import java.util.concurrent.CancellationException;

/**
 * Operation timed polling algorithm, which uses exponential backoff factor for determining when the
 * next polling operation should be executed. If the polling exceeds the total timeout this
 * algorithm cancels polling.
 */
@BetaApi
public class OperationTimedPollAlgorithm extends ExponentialRetryAlgorithm {
  /**
   * Creates the polling algorithm which will be using default {@code NanoClock} for time
   * computations.
   *
   * @param globalSettings the settings
   * @return timed poll algorithm
   */
  public static OperationTimedPollAlgorithm create(RetrySettings globalSettings) {
    return new OperationTimedPollAlgorithm(globalSettings, NanoClock.getDefaultClock());
  }

  OperationTimedPollAlgorithm(RetrySettings globalSettings, ApiClock clock) {
    super(globalSettings, clock);
  }

  /**
   * Returns {@code true} if another poll operation should be made or throws {@link
   * CancellationException} otherwise.
   *
   * @param nextAttemptSettings attempt settings, which will be used for the next attempt, if
   *     accepted
   * @return {@code true} if more attempts should be made, never returns {@code false} (throws
   *     {@code CancellationException} instead)
   * @throws CancellationException if no more attempts should be made
   */
  @Override
  public boolean shouldRetry(TimedAttemptSettings nextAttemptSettings)
      throws CancellationException {
    if (super.shouldRetry(nextAttemptSettings)) {
      return true;
    }
    throw new CancellationException();
  }
}
