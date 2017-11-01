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
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;

class SpoolingCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, List<ResponseT>> {
  private final ServerStreamingCallable<RequestT, ResponseT> streamingCallable;

  SpoolingCallable(ServerStreamingCallable<RequestT, ResponseT> streamingCallable) {
    this.streamingCallable = streamingCallable;
  }

  @Override
  public ApiFuture<List<ResponseT>> futureCall(RequestT request, ApiCallContext context) {
    ResponseObserverToFutureAdapter<ResponseT> adapter = new ResponseObserverToFutureAdapter<>();
    streamingCallable.call(request, adapter, context);
    return adapter.future;
  }

  static class ResponseObserverToFutureAdapter<ResponseT> implements ResponseObserver<ResponseT> {
    private final MyFuture future = new MyFuture();
    private final Object lock = new Object();

    @GuardedBy("lock")
    private StreamController controller;

    @GuardedBy("lock")
    private boolean isCancelled = false;

    private List<ResponseT> buffer = Lists.newArrayList();

    @Override
    public void onStart(StreamController controller) {
      final boolean wasCancelled;

      synchronized (lock) {
        wasCancelled = isCancelled;
        this.controller = controller;
      }
      if (wasCancelled) {
        controller.cancel();
      }
    }

    @Override
    public void onResponse(ResponseT response) {
      buffer.add(response);
    }

    @Override
    public void onError(Throwable t) {
      future.setException(t);
    }

    @Override
    public void onComplete() {
      future.set(buffer);
    }

    class MyFuture extends AbstractApiFuture<List<ResponseT>> {
      @Override
      protected void interruptTask() {
        final StreamController currentController;

        synchronized (lock) {
          isCancelled = true;
          currentController = controller;
        }
        if (currentController != null) {
          currentController.cancel();
        }
      }
    }
  }
}
