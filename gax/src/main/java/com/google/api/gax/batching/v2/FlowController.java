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
import com.google.common.base.Preconditions;
import java.util.concurrent.Semaphore;
import javax.annotation.Nullable;

/** Provides flow control capability. */
@BetaApi("The surface for batching is not stable yet and may change in the future.")
public class FlowController {

  /** Base exception that signals a flow control state. */
  public abstract static class FlowControlException extends RuntimeException {
    private FlowControlException() {}
  }

  /**
   * Exception thrown when client-side flow control is enforced based on the maximum number of
   * outstanding in-memory elements.
   */
  @BetaApi
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
          "The maximum number of batch elements: %d have been reached.", currentMaxElementCount);
    }
  }

  /**
   * Exception thrown when client-side flow control is enforced based on the maximum number of
   * unacknowledged in-memory bytes.
   */
  @BetaApi
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
          "The maximum number of batch bytes: %d have been reached.", currentMaxBytes);
    }
  }

  @Nullable private final Semaphore outstandingElementCount;
  @Nullable private final Semaphore64 outstandingByteCount;
  private final int maxOutstandingElementCount;
  private final long maxOutstandingRequestBytes;
  private final boolean isBlocking;

  public FlowController(FlowControlSettings settings) {
    isBlocking =
        settings.getLimitExceededBehavior() == FlowControlSettings.LimitExceededBehavior.Block;
    switch (settings.getLimitExceededBehavior()) {
      case ThrowException:
      case Block:
        break;
      case Ignore:
        this.maxOutstandingElementCount = 0;
        this.maxOutstandingRequestBytes = 0;
        this.outstandingElementCount = null;
        this.outstandingByteCount = null;
        return;
      default:
        throw new IllegalArgumentException(
            "Unknown LimitBehaviour: " + settings.getLimitExceededBehavior());
    }

    this.maxOutstandingElementCount = settings.getMaxOutstandingElementCount();
    if (maxOutstandingElementCount == 0) {
      outstandingElementCount = null;
    } else {
      outstandingElementCount = new Semaphore(maxOutstandingElementCount, true);
    }

    this.maxOutstandingRequestBytes = settings.getMaxOutstandingRequestBytes();
    if (maxOutstandingRequestBytes == 0) {
      outstandingByteCount = null;
    } else if (isBlocking) {
      outstandingByteCount = new BlockingSemaphore(maxOutstandingRequestBytes);
    } else {
      outstandingByteCount = new NonBlockingSemaphore(maxOutstandingRequestBytes);
    }
  }

  public void reserve(long bytes) {
    Preconditions.checkArgument(bytes >= 0);
    try {
      if (outstandingElementCount != null) {
        if (isBlocking) {
          outstandingElementCount.acquire();
        } else if (!outstandingElementCount.tryAcquire()) {
          throw new MaxOutstandingElementCountReachedException(maxOutstandingElementCount);
        }
      }

      // Will always allow to send a request even if it is larger than the flow control limit,
      // if it doesn't then it will deadlock the thread.
      if (outstandingByteCount != null) {
        long permitsToDraw = Math.min(bytes, maxOutstandingRequestBytes);
        if (!outstandingByteCount.acquire(permitsToDraw)) {
          if (outstandingElementCount != null) {
            outstandingElementCount.release();
          }
          throw new MaxOutstandingRequestBytesReachedException(maxOutstandingRequestBytes);
        }
      }
    } catch (InterruptedException e) {
      throw new MaxOutstandingElementCountReachedException(maxOutstandingElementCount);
    }
  }

  public void release(int elements, long bytes) {
    Preconditions.checkArgument(bytes >= 0);
    if (outstandingElementCount != null) {
      outstandingElementCount.release(elements);
    }

    if (outstandingByteCount != null) {
      // Need to return at most as much bytes as it can be drawn.
      long permitsToReturn = Math.min(bytes, maxOutstandingRequestBytes);
      outstandingByteCount.release(permitsToReturn);
    }
  }
}
