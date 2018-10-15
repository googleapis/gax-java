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

import org.threeten.bp.Duration;

public class NoopTracer implements Tracer {
  public static Tracer create() {
    return new NoopTracer();
  }

  @Override
  public Scope inScope() {
    return new NoopScope();
  }

  @Override
  public void operationSucceeded() {}

  @Override
  public void operationFailed(Throwable error) {}

  @Override
  public void connectionSelected(int id) {}

  @Override
  public void startAttempt() {}

  @Override
  public void attemptSucceeded() {}

  @Override
  public void retryableFailure(Throwable error, Duration delay) {}

  @Override
  public void retriesExhausted() {}

  @Override
  public void permanentFailure(Throwable error) {}

  @Override
  public void receivedResponse() {}

  @Override
  public void sentRequest() {}

  @Override
  public void sentBatchRequest(long elementCount, long requestSize) {}

  private static class NoopScope implements Scope {
    @Override
    public void close() {}
  }
}
