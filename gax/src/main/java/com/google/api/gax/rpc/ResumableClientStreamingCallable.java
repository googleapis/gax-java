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

public class ResumableClientStreamingCallable<RequestT, ResponseT>
    extends ClientStreamingCallable<RequestT, ResponseT> {

  private ClientStreamingCallable<RequestT, ResponseT> originalCallable;
  private ClientStreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;

  public ResumableClientStreamingCallable(
      ClientStreamingCallable<RequestT, ResponseT> callable,
      ClientStreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy) {
    this.originalCallable = callable;
    this.resumptionStrategy = resumptionStrategy;
  }

  @Override
  public ApiStreamObserver<RequestT> clientStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {

    // Initialize a resumable observer with the original callable and call contenxt (for
    // resumption), as well as the resumption strategy.
    ResumableStreamObserver<RequestT, ResponseT> observer =
        new ResumableStreamObserver<>(originalCallable, context, resumptionStrategy);

    // Wrap user-provided response observer.
    responseObserver = observer.asResponseObserver(responseObserver);

    // Initiate the client-stream stream.
    ApiStreamObserver<RequestT> requestObserver =
        originalCallable.clientStreamingCall(responseObserver, context);

    // Wrap the request observer in the Resumable form.
    return observer.asRequestObserver(requestObserver);
  }
}
