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
package com.google.api.gax.rpc;

import com.google.api.gax.retrying.ClientStreamResumptionStrategy;

public class ResumableStreamObserver<RequestT, ResponseT> {
  private ApiStreamObserver<RequestT> innerRequestObserver;
  private ApiStreamObserver<ResponseT> innerResponseObserver;
  private ApiCallContext originalContext;
  private ClientStreamingCallable<RequestT, ResponseT> originalCallable;
  private ClientStreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;

  public ResumableStreamObserver(
      ClientStreamingCallable<RequestT, ResponseT> originalCallable,
      ApiCallContext originalContext,
      ClientStreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy) {
    this.originalCallable = originalCallable;
    this.originalContext = originalContext;
  }

  public ApiStreamObserver<RequestT> asRequestObserver(
      ApiStreamObserver<RequestT> requestObserver) {
    this.innerRequestObserver = requestObserver;
    return new ResumableRequestObserver();
  }

  public ApiStreamObserver<ResponseT> asResponseObserver(
      ApiStreamObserver<ResponseT> responseObserver) {
    this.innerResponseObserver = responseObserver;
    return new ResumableResponseObserver();
  }

  private class ResumableRequestObserver implements ApiStreamObserver<RequestT> {
    @Override
    public void onNext(RequestT value) {
      value = resumptionStrategy.processRequest(value);
      innerRequestObserver.onNext(value);
    }

    @Override
    public void onError(Throwable t) {
      innerRequestObserver.onError(t);
    }

    @Override
    public void onCompleted() {
      innerRequestObserver.onCompleted();
    }
  }

  private class ResumableResponseObserver implements ApiStreamObserver<ResponseT> {
    @Override
    public void onNext(ResponseT value) {
      innerResponseObserver.onNext(value);
    }

    @Override
    public void onError(Throwable t) {
      // TODO(noahdietz): Should we resume when the client has already closed
      // the send side? Does this matter?
      if (!resumptionStrategy.resumable(t)) {
        innerResponseObserver.onError(t);
        return;
      }

      resume();
    }

    @Override
    public void onCompleted() {
      innerResponseObserver.onCompleted();
    }
  }

  private void resume() {
    this.innerRequestObserver =
        this.originalCallable.clientStreamingCall(innerResponseObserver, originalContext);
  }
}
