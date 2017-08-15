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
package com.google.api.gax.rpc.testing;

import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.common.base.Preconditions;
import java.util.Iterator;

@InternalApi("for testing")
public class FakeStreamingApi {

  public static class BidiStreamingStashCallable<RequestT, ResponseT>
      extends BidiStreamingCallable<RequestT, ResponseT> {
    private ApiCallContext context;
    private ApiStreamObserver<ResponseT> actualObserver;
    private RequestT actualRequest;

    @Override
    public ApiStreamObserver<RequestT> bidiStreamingCall(
        ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
      Preconditions.checkNotNull(responseObserver);
      actualObserver = responseObserver;
      this.context = context;
      return null;
    }

    public ApiCallContext getContext() {
      return context;
    }

    public ApiStreamObserver<ResponseT> getActualObserver() {
      return actualObserver;
    }

    public RequestT getActualRequest() {
      return actualRequest;
    }
  }

  public static class ServerStreamingStashCallable<RequestT, ResponseT>
      extends ServerStreamingCallable<RequestT, ResponseT> {
    private ApiCallContext context;
    private ApiStreamObserver<ResponseT> actualObserver;
    private RequestT actualRequest;

    @Override
    public void serverStreamingCall(
        RequestT request, ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
      Preconditions.checkNotNull(request);
      Preconditions.checkNotNull(responseObserver);
      actualRequest = request;
      actualObserver = responseObserver;
      this.context = context;
    }

    @Override
    public Iterator<ResponseT> blockingServerStreamingCall(
        RequestT request, ApiCallContext context) {
      Preconditions.checkNotNull(request);
      actualRequest = request;
      this.context = context;
      return null;
    }

    public ApiCallContext getContext() {
      return context;
    }

    public ApiStreamObserver<ResponseT> getActualObserver() {
      return actualObserver;
    }

    public RequestT getActualRequest() {
      return actualRequest;
    }
  }

  public static class ClientStreamingStashCallable<RequestT, ResponseT>
      extends ClientStreamingCallable<RequestT, ResponseT> {
    private ApiCallContext context;
    private ApiStreamObserver<ResponseT> actualObserver;
    private RequestT actualRequest;

    @Override
    public ApiStreamObserver<RequestT> clientStreamingCall(
        ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
      Preconditions.checkNotNull(responseObserver);
      actualObserver = responseObserver;
      this.context = context;
      return null;
    }

    public ApiCallContext getContext() {
      return context;
    }

    public ApiStreamObserver<ResponseT> getActualObserver() {
      return actualObserver;
    }

    public RequestT getActualRequest() {
      return actualRequest;
    }
  }
}
