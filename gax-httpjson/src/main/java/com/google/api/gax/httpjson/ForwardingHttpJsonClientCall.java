/*
 * Copyright 2022 Google LLC
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
package com.google.api.gax.httpjson;

import com.google.api.core.BetaApi;
import javax.annotation.Nullable;

/**
 * A {@link HttpJsonClientCall} which forwards all of its methods to another {@link
 * HttpJsonClientCall}.
 */
@BetaApi
public abstract class ForwardingHttpJsonClientCall<RequestT, ResponseT>
    extends HttpJsonClientCall<RequestT, ResponseT> {

  protected abstract HttpJsonClientCall<RequestT, ResponseT> delegate();

  @Override
  public void start(Listener<ResponseT> responseListener, HttpJsonMetadata requestHeaders) {
    delegate().start(responseListener, requestHeaders);
  }

  @Override
  public void request(int numMessages) {
    delegate().request(numMessages);
  }

  @Override
  public void cancel(@Nullable String message, @Nullable Throwable cause) {
    delegate().cancel(message, cause);
  }

  @Override
  public void halfClose() {
    delegate().halfClose();
  }

  @Override
  public void sendMessage(RequestT message) {
    delegate().sendMessage(message);
  }

  /**
   * A simplified version of {@link ForwardingHttpJsonClientCall} where subclasses can pass in a
   * {@link HttpJsonClientCall} as the delegate.
   */
  public abstract static class SimpleForwardingHttpJsonClientCall<RequestT, ResponseT>
      extends ForwardingHttpJsonClientCall<RequestT, ResponseT> {

    private final HttpJsonClientCall<RequestT, ResponseT> delegate;

    protected SimpleForwardingHttpJsonClientCall(HttpJsonClientCall<RequestT, ResponseT> delegate) {
      this.delegate = delegate;
    }

    @Override
    protected HttpJsonClientCall<RequestT, ResponseT> delegate() {
      return delegate;
    }
  }
}
