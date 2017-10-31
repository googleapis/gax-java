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

import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;

/**
 * Receives notifications from server-streaming calls..
 *
 * <p>It is used for sending messages in bidi (bidirectional) or client-streaming calls, or for
 * receiving messages in bidi or server-streaming calls.
 *
 * <p>The application implements the {@code ResponseObserver} and passes it to GAX, which then calls
 * the observer with the messages for the application to receive them. The methods might be called
 * by different threads, but are guaranteed to happen sequentially. The order of callbacks is
 * guaranteed to be:
 *
 * <ul>
 *   <li>exactly 1 onStart
 *   <li>0 or more on Response
 *   <li>exactly 1 onError or onComplete
 * </ul>
 *
 * <p>By default, the stream uses automatic flow control, where the next response will be delivered
 * as soon as the current one is processed by onResponse. A consumer can disable automatic flow
 * control by calling {@code disableAutoInboundFlowControl()} in {@code onStart}. After which, the
 * consumer must request responses by calling {@code request()}.
 */
public interface ResponseObserver<V> {

  /**
   * Called before the stream is started.
   *
   * <p>Allows for disabling flow control and early stream termination via {@code StreamController}.
   *
   * @param controller The controller for the stream.
   */
  void onStart(StreamController controller);

  /**
   * Receives a value from the stream.
   *
   * <p>Can be called many times but is never called after {@link #onError(Throwable)} or {@link
   * #onComplete()} are called.
   *
   * <p>Clients may may receive 0 or more onNext callbacks.
   *
   * <p>If an exception is thrown by an implementation the caller will terminate the stream by
   * calling {@link #onError(Throwable)} with the caught exception as the cause.
   *
   * @param response the value passed to the stream
   */
  void onResponse(V response);

  /**
   * Receives a terminating error from the stream.
   *
   * <p>May only be called once and if called, it must be the last method called. In particular if
   * an exception is thrown by an implementation of {@code onError}, no further calls to any method
   * are allowed.
   *
   * @param t the error occurred on the stream
   */
  void onError(Throwable t);

  /**
   * Receives a notification of successful stream completion.
   *
   * <p>May only be called once, and if called it must be the last method called. In particular if
   * an exception is thrown by an implementation of {@code onComplete}, no further calls to any
   * method are allowed.
   */
  void onComplete();

  /**
   * Allows the implementor of {@link ResponseObserver} to control flow of responses.
   *
   * <p>An instance of this class will be passed to {@code onStart}, at which point the receiver can
   * disable automatic flow control. The receiver can also save a reference to the instance and
   * terminate the stream early using {@code cancel()}.
   */
  abstract class StreamController {
    public static final RuntimeException DEFAULT_CANCELLATION_EXCEPTION =
        new CancellationException("User cancelled stream");

    /**
     * Cancel the stream early.
     *
     * <p>This will manifest as an onError on the {@link ResponseObserver} with the cause being
     * DEFAULT_CANCELLATION_EXCEPTION.
     */
    public void cancel() {
      cancel(null, DEFAULT_CANCELLATION_EXCEPTION);
    }

    /**
     * Cancel the stream early with a custom description and/or cause.
     *
     * <p>This will manifest as a onError on the {@link ResponseObserver} with the specified
     * description and/or cause.
     */
    public abstract void cancel(@Nullable String message, @Nullable Throwable cause);

    /**
     * Disables automatic flow control where a token is returned to the peer after a call to the
     * {@link ResponseObserver#onResponse(Object)} has completed. If disabled an application must
     * make explicit calls to {@link #request} to receive messages.
     */
    public abstract void disableAutoInboundFlowControl();

    /**
     * Requests up to the given number of response from the call to be delivered to {@link
     * ResponseObserver#onResponse(Object)}. No additional messages will be delivered.
     *
     * <p>This method can only be called after disabling automatic flow control
     *
     * <p>Message delivery is guaranteed to be sequential in the order received. In addition, the
     * listener methods will not be accessed concurrently. While it is not guaranteed that the same
     * thread will always be used, it is guaranteed that only a single thread will access the
     * listener at a time.
     *
     * <p>If called multiple times, the number of messages able to delivered will be the sum of the
     * calls.
     *
     * <p>This method is safe to call from multiple threads without external synchronizaton.
     *
     * @param count the requested number of messages to be delivered to the listener. Must be
     *     non-negative.
     */
    public abstract void request(int count);
  }
}
