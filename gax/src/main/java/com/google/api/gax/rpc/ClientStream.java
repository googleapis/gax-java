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
package com.google.api.gax.rpc;

import com.google.api.core.BetaApi;
/**
 * A stream used to send requests to a server.
 *
 * <p>Implementations are not required to be thread-safe.
 *
 * @param <V> The type of each request.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public interface ClientStream<V> {
  /**
   * Sends a value to the server.
   *
   * <p>It can be called many times, but should not be called after {@link #error(Throwable)} or
   * {@link #complete()} has been called.
   *
   * <p>If {@code send} throws an exception, the caller should call {@link #error(Throwable)} to
   * propagate it.
   */
  void send(V request);

  /**
   * Terminate the stream with an error.
   *
   * <p>It can be called only once and must be the last method called. If a call to {@code error}
   * throws, the caller should not call {@code error} again.
   */
  void error(Throwable t);

  /**
   * Terminate the stream.
   *
   * <p>It can be called only once and must be the last method called. If a call to {@code complete}
   * throws, the caller should not call {@code error}.
   */
  void complete();
}
