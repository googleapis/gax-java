/*
 * Copyright 2017 Google LLC
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

import com.google.api.core.InternalApi;
import java.util.Iterator;
import javax.annotation.Nonnull;

/** Used to send and receive messages from the server. */
public class BidiStream<RequestT, ResponseT> implements Iterable<ResponseT>, AutoCloseable {
  private final QueuingResponseObserver<ResponseT> observer = new QueuingResponseObserver<>();
  private final ServerStreamIterator<ResponseT> iterator = new ServerStreamIterator<>(observer);
  private ClientStream<RequestT> clientStream;
  private boolean consumed;

  @InternalApi("For use by BidiStreamingCallable only.")
  BidiStream() {}

  @Override
  public void close() {}

  @Override
  @Nonnull
  public Iterator<ResponseT> iterator() {
    if (consumed) {
      throw new IllegalStateException("Iterator already consumed");
    }
    consumed = true;

    return iterator;
  }

  @InternalApi("For use by BidiStreamingCallable only.")
  ResponseObserver<ResponseT> observer() {
    return observer;
  }

  @InternalApi("For use by BidiStreamingCallable only.")
  void setClientStream(ClientStream<RequestT> clientStream) {
    this.clientStream = clientStream;
  }

  public void send(RequestT req) {
    clientStream.send(req);
  }

  public boolean isSendReady() {
    return clientStream.isReady();
  }
}
