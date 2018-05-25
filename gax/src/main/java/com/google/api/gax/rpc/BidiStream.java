/*
 * Copyright 2018 Google LLC
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
import com.google.api.core.InternalApi;
import java.util.Iterator;
import javax.annotation.Nonnull;

/**
 * A wrapper around a bidirectional stream.
 *
 * <p>This class asynchronously pulls responses from upstream via {@link
 * StreamController#request(int)} and exposes them via its Iterator. The implementation is back
 * pressure aware and uses a constant buffer of 1 item.
 *
 * <p>Please note that the stream can only be consumed once and must either be fully consumed or be
 * canceled.
 *
 * <p>This class can also be used to send requests to the server using {@link #send(Object)}.
 *
 * <p>Neither this class nor the iterator it returns is thread-safe.
 *
 * <p>In the example below, we iterate through responses from the server and echo back the items we
 * see:
 *
 * <pre>{@code
 * BidiStream<Item> stream = ...;
 *
 * for (Item item : stream) {
 *   System.out.println(item.id());
 *
 *   stream.send(item.id());
 *
 *   // Allow for early termination
 *   if (item.id().equals("needle")) {
 *     // Cancelling the stream will cause `hasNext()` to return false on the next iteration,
 *     // naturally breaking the loop.
 *     stream.cancel();
 *   }
 * }
 * }</pre>
 *
 * @param <RequestT> The type of each request.
 * @param <ResponseT> The type of each response.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public class BidiStream<RequestT, ResponseT> implements Iterable<ResponseT> {
  private final QueuingResponseObserver<ResponseT> observer = new QueuingResponseObserver<>();
  private final ServerStreamIterator<ResponseT> iterator = new ServerStreamIterator<>(observer);
  private ClientStream<RequestT> clientStream;
  private boolean consumed;

  @InternalApi("For use by BidiStreamingCallable only.")
  BidiStream() {}

  @Override
  @Nonnull
  public Iterator<ResponseT> iterator() {
    if (consumed) {
      throw new IllegalStateException("Iterator already consumed");
    }
    consumed = true;

    return iterator;
  }

  /**
   * Cleanly cancels a partially consumed stream. The associated iterator will return false for the
   * hasNext() in the next iteration. This maintains the contract that an observed true from
   * hasNext() will yield an item in next(), but afterwards will return false.
   */
  public void cancel() {
    observer.cancel();
  }

  @InternalApi("For use by BidiStreamingCallable only.")
  ResponseObserver<ResponseT> observer() {
    return observer;
  }

  @InternalApi("For use by BidiStreamingCallable only.")
  void setClientStream(ClientStream<RequestT> clientStream) {
    this.clientStream = clientStream;
  }

  /** Send {@code req} to the server. */
  public void send(RequestT req) {
    clientStream.send(req);
  }

  /**
   * Reports whether a message can be sent without requiring excessive buffering internally.
   *
   * <p>This method only provides a hint. It is still correct for the user to call {@link
   * #send(Object)} even when this method returns {@code false}.
   */
  public boolean isSendReady() {
    return clientStream.isReady();
  }

  /**
   * Closes the sending side of the stream. Once called, no further calls to {@link #send(Object)},
   * {@link #closeSend()}, or {@link #closeSendWithError(Throwable)} are allowed.
   *
   * <p>Calling this method does not affect the receiving side, the iterator will continue to yield
   * responses from the server.
   */
  public void closeSend() {
    clientStream.close();
  }

  /**
   * Closes the sending side of the stream with error. The error is propagated to the server. Once
   * called, no further calls to {@link #send(Object)}, {@link #closeSend()}, or {@link
   * #closeSendWithError(Throwable)} are allowed.
   *
   * <p>Calling this method does not affect the receiving side, the iterator will continue to yield
   * responses from the server.
   */
  public void closeSendWithError(Throwable t) {
    clientStream.closeWithError(t);
  }
}
