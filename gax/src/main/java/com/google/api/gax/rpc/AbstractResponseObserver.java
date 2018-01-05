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

  /**
   * {@inheritDoc}
   *
   * <p>This implementation simply delegates to {@link #onStartImpl(StreamController)} after
   * ensuring consistent state.
   */
  public final void onStart(StreamController controller) {
    Preconditions.checkState(!isStarted, getClass() + " is already started.");
    isStarted = true;

    onStartImpl(controller);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation simply delegates to {@link #onResponse(Object)} after ensuring
   * consistent state.
   */
  public final void onResponse(V response) {
    Preconditions.checkState(!isClosed, getClass() + " received a response after being closed.");
    onResponseImpl(response);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation simply delegates to {@link #onComplete()} after ensuring consistent
   * state.
   */
  public final void onComplete() {
    Preconditions.checkState(!isClosed, getClass() + " tried to double close.");
    isClosed = true;
    onCompleteImpl();
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation simply delegates to {@link #onError(Throwable)} after ensuring
   * consistent state.
   */
  public final void onError(Throwable t) {
    Preconditions.checkState(!isClosed, getClass() + " received error after being closed", t);
    isClosed = true;
    onErrorImpl(t);
  }

  /** @see #onStart(StreamController) */
  protected abstract void onStartImpl(StreamController controller);

  /** @see #onResponse(Object) */
  protected abstract void onResponseImpl(V response);

  /** @see #onErrorImpl(Throwable) */
  protected abstract void onErrorImpl(Throwable t);

  /** @see #onComplete() */
  protected abstract void onCompleteImpl();
}
