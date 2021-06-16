/*
 * Copyright 2021 Google LLC
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

import com.google.api.core.BetaApi;
import com.google.api.gax.rpc.ApiStreamObserver;

import javax.annotation.Nonnull;

@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public interface ClientStreamResumptionStrategy<RequestT, ResponseT> {

  /** Creates a new instance of this ClientStreamResumptionStrategy without accumulated state */
  @Nonnull
  ClientStreamResumptionStrategy<RequestT, ResponseT> createNew();

  /**
   * Called by the {@code ResumableStreamObserver} when a request is to be sent. This method
   * accomplishes two goals:
   *
   * <ol>
   *   <li>It allows the strategy implementation to update its internal state so that it can
   *       properly resume.
   *   <li>It allows the strategy to alter the incoming requests. For example, if each request
   *       should contain some extra metadata. Please note that all messages (even for the first
   *       attempt) will be passed through this method.
   * </ol>
   *
   * This is only called by ResumableRequestObserver.onRequest.
   */
  @Nonnull
  RequestT processRequest(RequestT request);

  /**
   * If the error is resumable or if the stream should be resumed. This could be based on the error
   * that the server reports, or based on some internal state the ClientStreamResumptionStrategy
   * maintains that informs when to stop attempting resumption or when it is no longer possible.
   *
   * <p>This is only called by ResumableResponseObserver.onError.
   */
  boolean resumable(Throwable t);

  /**
   * Called by the ResumableStreamObserver with the new client-side ApiStreamObserver
   * when the stream is resumed. This allows the resumption strategy to rewrite buffered data,
   * send a required initial message on behalf of the client, or do nothing.
  */
  void resume(ApiStreamObserver<RequestT> requestObserver);
}
