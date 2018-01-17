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

/**
 * Interface for the business logic of a stream transformation. All methods will be called in a
 * synchronized block, so implementations don't need to be thread safe or be concerned with back
 * pressure.
 *
 * <p>The flow is:
 *
 * <pre>
 * hasFullFrame?
 *  -&gt; true -&gt; pop()
 *  -&gt; false
 *    -&gt; upstream complete?
 *      -&gt; true
 *        -&gt; hasPartialFrame?
 *          -&gt; true
 *            =&gt; notify error
 *          -&gt; false
 *            =&gt; notify complete
 *      -&gt; false
 *        =&gt; push() and restart at hasFullFrame?
 * </pre>
 *
 * @param <InnerT> The type of responses coming from the inner ServerStreamingCallable.
 * @param <OuterT> The type of responses the outer {@link ResponseObserver} expects.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public interface Reframer<OuterT, InnerT> {
  /**
   * Refill internal buffers with inner/upstream response. Should only be invoked if {@link
   * #hasFullFrame} returns false.
   */
  void push(InnerT response);

  /** Checks if there is a frame to be popped. */
  boolean hasFullFrame();

  /** Checks if there is any incomplete data. Used to check if the stream closed prematurely. */
  boolean hasPartialFrame();

  /**
   * Returns and removes the current completed frame. Should only be called if hasFullFrame returns
   * true.
   */
  OuterT pop();
}
