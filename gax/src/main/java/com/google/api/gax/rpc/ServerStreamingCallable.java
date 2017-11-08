/*
 * Copyright 2017, Google LLC All rights reserved.
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
import java.util.Iterator;

/**
 * A ServerStreamingCallable is an immutable object which is capable of making RPC calls to server
 * streaming API methods. Not all transports support streaming.
 *
 * <p>It is considered advanced usage for a user to create a ServerStreamingCallable themselves.
 * This class is intended to be created by a generated client class, and configured by instances of
 * StreamingCallSettings.Builder which are exposed through the client settings class.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public abstract class ServerStreamingCallable<RequestT, ResponseT> {

  protected ServerStreamingCallable() {}

  /**
   * Conduct a server streaming call with the given {@link ApiCallContext}.
   *
   * @param request request
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @param context {@link ApiCallContext} to provide context information for the RPC call.
   */
  public abstract void serverStreamingCall(
      RequestT request, ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context);

  /**
   * Conduct a server streaming call
   *
   * @param request request
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   */
  public void serverStreamingCall(RequestT request, ApiStreamObserver<ResponseT> responseObserver) {
    serverStreamingCall(request, responseObserver, null);
  }

  /**
   * Conduct a iteration server streaming call
   *
   * @param request request
   * @return {@link Iterator} which is used for iterating the responses.
   */
  public abstract Iterator<ResponseT> blockingServerStreamingCall(
      RequestT request, ApiCallContext context);

  public Iterator<ResponseT> blockingServerStreamingCall(RequestT request) {
    return blockingServerStreamingCall(request, null);
  }

  /**
   * Returns a new {@code ServerStreamingCallable} with an {@link ApiCallContext} that is used as a
   * default when none is supplied in individual calls.
   *
   * @param defaultCallContext the default {@link ApiCallContext}.
   */
  public ServerStreamingCallable<RequestT, ResponseT> withDefaultCallContext(
      final ApiCallContext defaultCallContext) {
    return new ServerStreamingCallable<RequestT, ResponseT>() {
      @Override
      public void serverStreamingCall(
          RequestT request,
          ApiStreamObserver<ResponseT> responseObserver,
          ApiCallContext thisCallContext) {
        ServerStreamingCallable.this.serverStreamingCall(
            request, responseObserver, defaultCallContext.merge(thisCallContext));
      }

      @Override
      public Iterator<ResponseT> blockingServerStreamingCall(
          RequestT request, ApiCallContext thisCallContext) {
        return ServerStreamingCallable.this.blockingServerStreamingCall(
            request, defaultCallContext.merge(thisCallContext));
      }
    };
  }
}
