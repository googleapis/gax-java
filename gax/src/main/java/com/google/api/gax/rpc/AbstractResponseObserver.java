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
import com.google.common.base.Preconditions;

/** Base implementation of {@link ResponseObserver} that performs state sanity checks. */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public abstract class AbstractResponseObserver<V> implements ResponseObserver<V> {
  private boolean isStarted;
  private boolean isClosed;

  public final void onStart(StreamController controller) {
    Preconditions.checkState(!isStarted, getClass() + " is already started.");
    isStarted = true;

    onStartImpl(controller);
  }

  public final void onResponse(V response) {
    Preconditions.checkState(!isClosed, getClass() + " received a response after being closed.");
    onResponseImpl(response);
  }

  public final void onComplete() {
    Preconditions.checkState(!isClosed, getClass() + " tried to double close.");
    isClosed = true;
    onCompleteImpl();
  }

  public final void onError(Throwable t) {
    Preconditions.checkState(!isClosed, getClass() + " received error after being closed", t);
    isClosed = true;
    onErrorImpl(t);
  }

  /**
   * Called before the stream is started. This must be invoked synchronously on the same thread that
   * called {@link ServerStreamingCallable#call(Object, ResponseObserver, ApiCallContext)}
   *
   * <p>Allows for disabling flow control and early stream termination via {@code StreamController}.
   *
   * @param controller The controller for the stream.
   */
  protected abstract void onStartImpl(StreamController controller);

  /**
   * Receives a value from the stream.
   *
   * <p>Can be called many times but is never called after {@link #onError(Throwable)} or {@link
   * #onComplete()} are called.
   *
   * <p>Clients may may receive 0 or more onResponse callbacks.
   *
   * <p>If an exception is thrown by an implementation the caller will terminate the stream by
   * calling {@link #onError(Throwable)} with the caught exception as the cause.
   *
   * @param response the value passed to the stream
   */
  protected abstract void onResponseImpl(V response);

  /**
   * Receives a terminating error from the stream.
   *
   * <p>May only be called once, and if called, it must be the last method called. In particular, if
   * an exception is thrown by an implementation of {@code onError}, no further calls to any method
   * are allowed.
   *
   * @param t the error occurred on the stream
   */
  protected abstract void onErrorImpl(Throwable t);

  /**
   * Receives a notification of successful stream completion.
   *
   * <p>May only be called once, and if called, it must be the last method called. In particular, if
   * an exception is thrown by an implementation of {@code onComplete}, no further calls to any
   * method are allowed.
   */
  protected abstract void onCompleteImpl();
}
