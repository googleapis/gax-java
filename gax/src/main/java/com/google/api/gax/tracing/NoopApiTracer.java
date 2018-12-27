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
package com.google.api.gax.tracing;

import com.google.api.core.InternalApi;
import org.threeten.bp.Duration;

/**
 * A fake implementation of {@link ApiTracer} that does nothing.
 *
 * <p>For internal use only.
 */
@InternalApi
public final class NoopApiTracer implements ApiTracer {
  private static final ApiTracer INSTANCE = new NoopApiTracer();

  private static final Scope NOOP_SCOPE =
      new Scope() {
        @Override
        public void close() {
          // noop
        }
      };

  private NoopApiTracer() {}

  public static ApiTracer create() {
    return INSTANCE;
  }

  @Override
  public Scope inScope() {
    return NOOP_SCOPE;
  }

  @Override
  public void operationSucceeded() {
    // noop
  }

  @Override
  public void operationFailed(Throwable error) {
    // noop
  }

  @Override
  public void connectionSelected(int id) {
    // noop
  }

  @Override
  public void startAttempt(int attemptNumber) {
    // noop
  }

  @Override
  public void attemptSucceeded() {
    // noop
  }

  @Override
  public void retryableFailure(Throwable error, Duration delay) {
    // noop
  }

  @Override
  public void retriesExhausted() {
    // noop
  }

  @Override
  public void permanentFailure(Throwable error) {
    // noop

  }

  @Override
  public void receivedResponse() {
    // noop
  }

  @Override
  public void sentRequest() {
    // noop
  }

  @Override
  public void sentBatchRequest(long elementCount, long requestSize) {
    // noop
  }
}
