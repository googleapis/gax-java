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
package com.google.api.gax.rpc;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.api.core.InternalApi;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A StreamingCallable that prepares the call context for the inner callable stack.
 *
 * <p>Public for technical reasons - for advanced usage.
 */
@InternalApi("For use by transport-specific implementations")
public class EntryPointStreamingCallable<RequestT, ResponseT>
    extends StreamingCallable<RequestT, ResponseT> {

  private final StreamingCallable<RequestT, ResponseT> callable;
  private final ApiCallContext defaultCallContext;
  private final List<ApiCallContextEnhancer> callContextEnhancers;

  public EntryPointStreamingCallable(
      StreamingCallable<RequestT, ResponseT> callable, ApiCallContext defaultCallContext) {
    this(callable, defaultCallContext, Collections.<ApiCallContextEnhancer>emptyList());
  }

  public EntryPointStreamingCallable(
      StreamingCallable<RequestT, ResponseT> callable,
      ApiCallContext defaultCallContext,
      List<ApiCallContextEnhancer> callContextEnhancers) {
    this.callable = Preconditions.checkNotNull(callable);
    this.defaultCallContext = Preconditions.checkNotNull(defaultCallContext);
    this.callContextEnhancers = Preconditions.checkNotNull(callContextEnhancers);
  }

  public ApiStreamObserver<RequestT> bidiStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, ApiCallContext thisCallContext) {
    ApiCallContext newCallContext =
        ApiCallContextEnhancers.applyEnhancers(
            defaultCallContext, thisCallContext, callContextEnhancers);
    return callable.bidiStreamingCall(responseObserver, newCallContext);
  }

  public void serverStreamingCall(
      RequestT request,
      ApiStreamObserver<ResponseT> responseObserver,
      ApiCallContext thisCallContext) {
    ApiCallContext newCallContext =
        ApiCallContextEnhancers.applyEnhancers(
            defaultCallContext, thisCallContext, callContextEnhancers);
    callable.serverStreamingCall(request, responseObserver, newCallContext);
  }

  public Iterator<ResponseT> blockingServerStreamingCall(
      RequestT request, ApiCallContext thisCallContext) {
    ApiCallContext newCallContext =
        ApiCallContextEnhancers.applyEnhancers(
            defaultCallContext, thisCallContext, callContextEnhancers);
    return callable.blockingServerStreamingCall(request, newCallContext);
  }

  public ApiStreamObserver<RequestT> clientStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, ApiCallContext thisCallContext) {
    ApiCallContext newCallContext =
        ApiCallContextEnhancers.applyEnhancers(
            defaultCallContext, thisCallContext, callContextEnhancers);
    return callable.clientStreamingCall(responseObserver, newCallContext);
  }
}
