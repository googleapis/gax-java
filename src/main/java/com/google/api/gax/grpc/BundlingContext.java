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

package com.google.api.gax.grpc;

import com.google.common.base.Preconditions;

/**
 * Holds the complete context to issue a call and notify the call's
 * listener. This includes a CallContext object, which contains the call
 * objects, the channel, and the request; a Callable object to issue
 * the request; and a SettableFuture object, to notify the response
 * listener.
 */
public class BundlingContext<RequestT, ResponseT>
    implements RequestIssuer<RequestT, ResponseT> {
  private final CallContext<RequestT> context;
  private final ApiCallable<RequestT, ResponseT> callable;
  private final BundlingFuture<ResponseT> bundlingFuture;
  private ResponseT responseToSend;
  private Throwable throwableToSend;

  public BundlingContext(CallContext<RequestT> context,
      ApiCallable<RequestT, ResponseT> callable,
      BundlingFuture<ResponseT> bundlingFuture) {
    this.context = context;
    this.callable = callable;
    this.bundlingFuture = bundlingFuture;
    this.responseToSend = null;
    this.throwableToSend = null;
  }

  public CallContext<RequestT> getCallContext() {
    return context;
  }

  public ApiCallable<RequestT, ResponseT> getCallable() {
    return callable;
  }

  @Override
  public RequestT getRequest() {
    return context.getRequest();
  }

  @Override
  public void setResponse(ResponseT response) {
    Preconditions.checkState(throwableToSend == null,
        "Cannot set both exception and response");
    responseToSend = response;
  }

  @Override
  public void setException(Throwable throwable) {
    Preconditions.checkState(throwableToSend == null,
        "Cannot set both exception and response");
    throwableToSend = throwable;
  }

  /**
   * Sends back the result that was stored by either setResponse or setException
   */
  public void sendResult() {
    if (responseToSend != null) {
      bundlingFuture.set(responseToSend);
    } else if (throwableToSend != null) {
      bundlingFuture.setException(throwableToSend);
    } else {
      throw new IllegalStateException(
          "Neither response nor exception were set in BundlingContext");
    }
  }
}
