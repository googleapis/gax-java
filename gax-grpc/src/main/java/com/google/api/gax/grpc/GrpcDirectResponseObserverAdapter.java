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
package com.google.api.gax.grpc;

import com.google.api.gax.rpc.ResponseObserver;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;

/**
 * Adapts the events from a {@link ClientCall.Listener} to a {@link ResponseObserver} and handles
 * automatic flow control.
 *
 * <p>Package-private for internal use.
 *
 * @param <ResponseT> The type of the response.
 */
class GrpcDirectResponseObserverAdapter<ResponseT> extends ClientCall.Listener<ResponseT> {
  private final ClientCall<?, ResponseT> clientCall;
  private final boolean autoflowControl;
  private final ResponseObserver<ResponseT> outerObserver;

  GrpcDirectResponseObserverAdapter(
      ClientCall<?, ResponseT> clientCall,
      boolean autoflowControl,
      ResponseObserver<ResponseT> outerObserver) {
    this.clientCall = clientCall;
    this.autoflowControl = autoflowControl;
    this.outerObserver = outerObserver;
  }

  /**
   * Notifies the outerObserver of the new message and if automatic flow control is enabled,
   * requests the next message. Any errors raised by the outerObserver will be bubbled up to GRPC,
   * which cancel the ClientCall and close this listener.
   *
   * @param message The new message.
   */
  @Override
  public void onMessage(ResponseT message) {
    outerObserver.onResponse(message);

    if (autoflowControl) {
      clientCall.request(1);
    }
  }

  @Override
  public void onClose(Status status, Metadata trailers) {
    if (status.isOk()) {
      outerObserver.onComplete();
    } else {
      outerObserver.onError(status.asRuntimeException(trailers));
    }
  }
}
