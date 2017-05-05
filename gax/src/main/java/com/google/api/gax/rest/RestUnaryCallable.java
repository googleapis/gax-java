/*
 * Copyright 2016, Google Inc. All rights reserved.
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
package com.google.api.gax.rest;

import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.gax.rpc.UnaryCallable;

/**
 * A UnaryCallable is an immutable object which is capable of making RPC calls to non-streaming API
 * methods over HTTP/1.1.
 *
 * In typical usage, the request to send to the remote service will not be bound to the
 * UnaryCallable, but instead is provided at call time, which allows for a UnaryCallable to be saved
 * and used indefinitely.
 *
 * <p>There are two styles of calls that can be made through a UnaryCallable: synchronous and
 * asynchronous.
 */
@BetaApi
public final class RestUnaryCallable<RequestT, ResponseT>
    implements UnaryCallable<RequestT, ResponseT> {

  /**
   * Perform a call asynchronously.
   *
   * @return {@link ApiFuture} for the call result
   */
  public ApiFuture<ResponseT> futureCall(RequestT request) {
    return null;
  };

  /**
   * Perform a call synchronously.
   *
   * @param request The request to send to the service.
   * @return the call result
   * @throws Exception
   */
  public ResponseT call(RequestT request) {
    return null;
  };
}
