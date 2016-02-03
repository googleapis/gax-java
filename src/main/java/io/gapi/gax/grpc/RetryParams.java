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

package io.gapi.gax.grpc;

/**
 * {@code RetryParams} encapsulates a retry strategy used by
 * {@link io.gapi.gax.grpc.ApiCallable#retrying(RetryParams)}.
 */
public final class RetryParams {
  public static final long DEFAULT_INITIAL_RETRY_DELAY = 10L;
  public static final long DEFAULT_MAX_RETRY_DELAY = 1000L;
  public static final double DEFAULT_RETRY_DELAY_MULT = 1.2;
  public static final long DEFAULT_INITIAL_RPC_TIMEOUT = 3000L;
  public static final long DEFAULT_MAX_RPC_TIMEOUT = 10000L;
  public static final double DEFAULT_RPC_TIMEOUT_MULT = 1.2;
  public static final long DEFAULT_TOTAL_TIMEOUT = 30000L;

  public static final RetryParams DEFAULT =
      new RetryParams(
          DEFAULT_INITIAL_RETRY_DELAY,
          DEFAULT_MAX_RETRY_DELAY,
          DEFAULT_RETRY_DELAY_MULT,
          DEFAULT_INITIAL_RPC_TIMEOUT,
          DEFAULT_MAX_RPC_TIMEOUT,
          DEFAULT_RPC_TIMEOUT_MULT,
          DEFAULT_TOTAL_TIMEOUT);

  private final long initialRetryDelay;
  private final long maxRetryDelay;
  private final double retryDelayMult;

  private final long initialRpcTimeout;
  private final long maxRpcTimeout;
  private final double rpcTimeoutMult;

  private final long totalTimeout;

  public RetryParams(
      long initialRetryDelay,
      long maxRetryDelay,
      double retryDelayMult,
      long initialRpcTimeout,
      long maxRpcTimeout,
      double rpcTimeoutMult,
      long totalTimeout) {
    this.initialRetryDelay = initialRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
    this.retryDelayMult = retryDelayMult;
    this.initialRpcTimeout = initialRpcTimeout;
    this.maxRpcTimeout = maxRpcTimeout;
    this.rpcTimeoutMult = rpcTimeoutMult;
    this.totalTimeout = totalTimeout;
  }

  public long getInitialRetryDelay() {
    return initialRetryDelay;
  }

  public long getMaxRetryDelay() {
    return maxRetryDelay;
  }

  public double getRetryDelayMult() {
    return retryDelayMult;
  }

  public long getInitialRpcTimeout() {
    return initialRpcTimeout;
  }

  public long getMaxRpcTimeout() {
    return maxRpcTimeout;
  }

  public double getRpcTimeoutMult() {
    return rpcTimeoutMult;
  }

  public long getTotalTimeout() {
    return totalTimeout;
  }
}
