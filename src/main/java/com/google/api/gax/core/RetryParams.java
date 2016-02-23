/*
 * Copyright 2015, Google Inc.
 * All rights reserved.
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

package com.google.api.gax.core;

import com.google.auto.value.AutoValue;

/**
 * {@code RetryParams} encapsulates a retry strategy used by
 * {@link com.google.api.gax.grpc.ApiCallable#retrying(RetryParams, ScheduledExecutorService)}.
 */
@AutoValue
public abstract class RetryParams {
  public abstract BackoffParams getRetryBackoff();

  public abstract BackoffParams getTimeoutBackoff();

  public abstract long getTotalTimeout();

  public static Builder newBuilder() {
    return new AutoValue_RetryParams.Builder();
  }

  public Builder toBuilder() {
    return new AutoValue_RetryParams.Builder(this);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setRetryBackoff(BackoffParams retryBackoff);

    public abstract Builder setTimeoutBackoff(BackoffParams timeoutBackoff);

    public abstract Builder setTotalTimeout(long totalTimeout);

    abstract RetryParams autoBuild();

    public RetryParams build() {
      RetryParams params = autoBuild();
      if (params.getTotalTimeout() < 0) {
        throw new IllegalStateException("total timeout must not be negative");
      }
      return params;
    }
  }
}
