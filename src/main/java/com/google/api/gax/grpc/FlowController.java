/*
 * Copyright 2016, Google Inc. All rights reserved.
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
package com.google.api.gax.grpc;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.util.concurrent.Semaphore;
import javax.annotation.Nullable;

/** Provides flow control capability. */
public class FlowController {
  /** Base exception that signals a flow control state. */
  public abstract static class FlowControlException extends Exception {
    private FlowControlException() {}
  }

  /**
   * Exception thrown when client-side flow control is enforced based on the maximum number of
   * outstanding in-memory elements.
   */
  public final static class MaxOutstandingElementCountReachedException
      extends FlowControlException {
    private final int currentMaxElementCount;

    public MaxOutstandingElementCountReachedException(int currentMaxElementCount) {
      this.currentMaxElementCount = currentMaxElementCount;
    }

    public int getCurrentMaxBundleElementCount() {
      return currentMaxElementCount;
    }

    @Override
    public String toString() {
      return String.format(
          "The maximum number of bundle elements: %d have been reached.", currentMaxElementCount);
    }
  }

  /**
   * Exception thrown when client-side flow control is enforced based on the maximum number of
   * unacknowledged in-memory bytes.
   */
  public final static class MaxOutstandingRequestBytesReachedException
      extends FlowControlException {
    private final int currentMaxBytes;

    public MaxOutstandingRequestBytesReachedException(int currentMaxBytes) {
      this.currentMaxBytes = currentMaxBytes;
    }

    public int getCurrentMaxBundleBytes() {
      return currentMaxBytes;
    }

    @Override
    public String toString() {
      return String.format(
          "The maximum number of bundle bytes: %d have been reached.", currentMaxBytes);
    }
  }

  @Nullable private final Semaphore outstandingElementCount;
  @Nullable private final Semaphore outstandingByteCount;
  private final boolean failOnLimits;
  @Nullable private final Integer maxOutstandingElementCount;
  @Nullable private final Integer maxOutstandingRequestBytes;

  public FlowController(FlowControlSettings settings, boolean failOnFlowControlLimits) {
    this.maxOutstandingElementCount = settings.getMaxOutstandingElementCount();
    this.maxOutstandingRequestBytes = settings.getMaxOutstandingRequestBytes();
    outstandingElementCount =
        maxOutstandingElementCount != null ? new Semaphore(maxOutstandingElementCount) : null;
    outstandingByteCount =
        maxOutstandingRequestBytes != null ? new Semaphore(maxOutstandingRequestBytes) : null;
    this.failOnLimits = failOnFlowControlLimits;
  }

  public void reserve(int elements, int bytes) throws FlowControlException {
    Preconditions.checkArgument(elements >= 0);
    Preconditions.checkArgument(bytes >= 0);

    if (outstandingElementCount != null) {
      if (!failOnLimits) {
        outstandingElementCount.acquireUninterruptibly(elements);
      } else if (!outstandingElementCount.tryAcquire(elements)) {
        throw new MaxOutstandingElementCountReachedException(maxOutstandingElementCount);
      }
    }

    // Will always allow to send a request even if it is larger than the flow control limit,
    // if it doesn't then it will deadlock the thread.
    if (outstandingByteCount != null) {
      int permitsToDraw = Math.min(bytes, maxOutstandingRequestBytes);
      if (!failOnLimits) {
        outstandingByteCount.acquireUninterruptibly(permitsToDraw);
      } else if (!outstandingByteCount.tryAcquire(permitsToDraw)) {
        throw new MaxOutstandingRequestBytesReachedException(maxOutstandingRequestBytes);
      }
    }
  }

  public void release(int elements, int bytes) {
    Preconditions.checkArgument(elements >= 0);
    Preconditions.checkArgument(bytes >= 0);

    if (outstandingElementCount != null) {
      outstandingElementCount.release(elements);
    }
    if (outstandingByteCount != null) {
      // Need to return at most as much bytes as it can be drawn.
      int permitsToReturn = Math.min(bytes, maxOutstandingRequestBytes);
      outstandingByteCount.release(permitsToReturn);
    }
  }
}
