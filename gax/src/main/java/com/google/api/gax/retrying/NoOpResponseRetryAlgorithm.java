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

import com.google.api.core.BetaApi;

/**
 * A basic implementation of {@link ResponseRetryAlgorithm}. This implementation should be used when
 * any response (i.e. no exception) is considered a success and returned value should be accepted as
 * the final result of the retrying process. This should the most typical case for any retriable
 * operation.
 *
 * @param <ResponseT> attempt response type
 */
@BetaApi
public class NoOpResponseRetryAlgorithm<ResponseT> implements ResponseRetryAlgorithm<ResponseT> {
  /**
   * Always returns null, indicating that this algorithm does not provide any specific settings for
   * the next attempt.
   *
   * @param prevResponse response returned by the previous attempt
   * @param prevSettings previous attempt settings
   */
  @Override
  public TimedAttemptSettings createNextAttempt(
      ResponseT prevResponse, TimedAttemptSettings prevSettings) {
    return null;
  }

  /**
   * Always returns false, indicating that any response should be considered as successful.
   *
   * @param prevResponse response returned by the previous attempt
   */
  @Override
  public boolean shouldRetry(ResponseT prevResponse) {
    return false;
  }

  /**
   * Always return false.
   *
   * @param prevResponse response returned by the previous attempt
   */
  @Override
  public boolean shouldCancel(ResponseT prevResponse) {
    return false;
  }
}
