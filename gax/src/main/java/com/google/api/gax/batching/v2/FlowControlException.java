/*
 * Copyright 2019 Google LLC
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
package com.google.api.gax.batching.v2;

import com.google.api.core.BetaApi;
import com.google.api.core.InternalExtensionOnly;

/** Base exception that signals a flow control state. */
@BetaApi("The surface for batching is not stable yet and may change in the future.")
@InternalExtensionOnly("For google-cloud-java client use only.")
public abstract class FlowControlException extends RuntimeException {
  private FlowControlException() {}

  FlowControlException(String message) {
    super(message);
  }

  /**
   * Exception thrown when client-side flow control is enforced based on the maximum number of
   * outstanding in-memory elements.
   */
  @BetaApi("The surface for batching is not stable yet and may change in the future.")
  public static final class MaxOutstandingElementCountReachedException
      extends FlowControlException {
    private final int currentMaxElementCount;

    public MaxOutstandingElementCountReachedException(int currentMaxElementCount) {
      this.currentMaxElementCount = currentMaxElementCount;
    }

    public int getCurrentMaxBatchElementCount() {
      return currentMaxElementCount;
    }

    @Override
    public String toString() {
      return String.format(
          "Maximum number of outstanding batch elements: %d have been reached.",
          currentMaxElementCount);
    }
  }

  /**
   * Exception thrown when client-side flow control is enforced based on the maximum number of
   * unacknowledged in-memory bytes.
   */
  @BetaApi("The surface for batching is not stable yet and may change in the future.")
  public static final class MaxOutstandingRequestBytesReachedException
      extends FlowControlException {
    private final long currentMaxBytes;

    public MaxOutstandingRequestBytesReachedException(long currentMaxBytes) {
      this.currentMaxBytes = currentMaxBytes;
    }

    public long getCurrentMaxBatchBytes() {
      return currentMaxBytes;
    }

    @Override
    public String toString() {
      return String.format(
          "Maximum number of outstanding batch bytes: %d have been reached.", currentMaxBytes);
    }
  }
}
