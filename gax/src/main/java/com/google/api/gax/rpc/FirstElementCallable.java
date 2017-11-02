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

import com.google.api.core.AbstractApiFuture;
import com.google.api.core.ApiFuture;

/**
 * Wraps a {@link ServerStreamingCallable} in a {@link UnaryCallable}, by returning the first
 * element in the stream and cancelling the remainder. This allows for a generalized RPC to be used
 * ergonomically for point lookups.
 *
 * @param <RequestT> The type of the request.
 * @param <ResponseT> The type of the item in the stream.
 */
final class FirstElementCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
  private final ServerStreamingCallable<RequestT, ResponseT> streamingCallable;

  FirstElementCallable(ServerStreamingCallable<RequestT, ResponseT> streamingCallable) {
    this.streamingCallable = streamingCallable;
  }

  /**
   * Starts the RPC and returns a future wrapping the result. If the stream is empty, the result
   * will be null. If a request is cancelled, the future will be rejected with a {@link
   * java.util.concurrent.CancellationException}.
   *
   * @param request The request.
   * @param context {@link ApiCallContext} to make the call with
   * @return A {@link ApiFuture} wrapping a possible first element of the stream.
   */
  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext context) {
    ResponseObserverToFutureAdapter<ResponseT> adapter = new ResponseObserverToFutureAdapter<>();

    streamingCallable.call(request, adapter, context);
    return adapter.future;
  }

  /**
   * A {@link ResponseObserver} that wraps a future. The future will resolved upon receiving the
   * first element or completing the streaming. Manual flow control is used to allow cancelling the
   * remaining elements.
   *
   * @param <ResponseT> The type of the element in the stream.
   */
  static class ResponseObserverToFutureAdapter<ResponseT> implements ResponseObserver<ResponseT> {
    private final MyFuture future = new MyFuture();
    private StreamController controller;

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;

      // NOTE: the call is started before the future is exposed to the caller,
      // so isCancelled is guaranteed to be false.
      controller.disableAutoInboundFlowControl();
      controller.request(1);
    }

    @Override
    public void onResponse(ResponseT response) {
      future.set(response);
      controller.cancel();
    }

    @Override
    public void onError(Throwable t) {
      future.setException(t);
    }

    @Override
    public void onComplete() {
      future.set(null);
    }

    /**
     * Simple implementation of a future to prematurely interrupt the RPC before the first element
     * is received.
     */
    class MyFuture extends AbstractApiFuture<ResponseT> {
      @Override
      protected void interruptTask() {
        controller.cancel();
      }
    }
  }
}
