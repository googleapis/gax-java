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
package com.google.api.gax.httpjson;

import com.google.api.core.BetaApi;
import com.google.api.gax.rpc.Callables;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;

/** Class with utility methods to create http/json-based direct callables. */
@BetaApi
public class HttpJsonCallableFactory {

  private HttpJsonCallableFactory() {}

  private static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createDirectUnaryCallable(
      HttpJsonCallSettings<RequestT, ResponseT> httpJsonCallSettings) {
    return new HttpJsonDirectCallable<>(httpJsonCallSettings.getMethodDescriptor());
  }

  static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createUnaryCallable(
      UnaryCallable<RequestT, ResponseT> innerCallable,
      UnaryCallSettings<?, ?> callSettings,
      ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> callable =
        new HttpJsonExceptionCallable<>(innerCallable, callSettings.getRetryableCodes());
    callable = Callables.retrying(callable, callSettings, clientContext);
    return callable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }

  /**
   * Create a callable object with http/json-specific functionality. Designed for use by generated
   * code.
   *
   * @param httpJsonCallSettings the http/json call settings
   * @param callSettings {@link UnaryCallSettings} to configure the method-level settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createUnaryCallable(
      HttpJsonCallSettings<RequestT, ResponseT> httpJsonCallSettings,
      UnaryCallSettings<RequestT, ResponseT> callSettings,
      ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> innerCallable =
        createDirectUnaryCallable(httpJsonCallSettings);
    return createUnaryCallable(innerCallable, callSettings, clientContext);
  }
}
