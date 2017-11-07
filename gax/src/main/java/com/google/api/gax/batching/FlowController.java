/*
 * Copyright 2016, Google LLC All rights reserved.
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
package com.google.api.gax.batching;

import com.google.api.core.BetaApi;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;

/** Provides flow control capability. */
@BetaApi
public class FlowController {
  /** Base exception that signals a flow control state. */
  public abstract static class FlowControlException extends Exception {
    private FlowControlException() {}
  }

  /**
   * Runtime exception that can be used in place of FlowControlException when an unchecked exception
   * is required.
   */
  @BetaApi
  public static class FlowControlRuntimeException extends RuntimeException {
    private FlowControlRuntimeException(FlowControlException e) {
      super(e);
    }

    public static FlowControlRuntimeException fromFlowControlException(FlowControlException e) {
      return new FlowControlRuntimeException(e);
    }
  }

  /**
   * Exception thrown when client-side flow control is enforced based on the maximum number of
   * outstanding in-memory elements.
   */
  @BetaApi
  public static final class MaxOutstandingElementCountReachedException
      extends FlowControlException {
    private final long currentMaxElementCount;

    public MaxOutstandingElementCountReachedException(long currentMaxElementCount) {
      this.currentMaxElementCount = currentMaxElementCount;
    }

    public long getCurrentMaxBatchElementCount() {
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

  /**
   * Enumeration of behaviors that FlowController can use in case the flow control limits are
   * exceeded.
   */
  @BetaApi
  public enum LimitExceededBehavior {
    ThrowException,
    Block,
    Ignore,
  }

  @Nullable private final Semaphore64 outstandingElementCount;
  @Nullable private final Semaphore64 outstandingByteCount;
  private final boolean failOnLimits;
  @Nullable private final Long maxOutstandingElementCount;
  @Nullable private final Long maxOutstandingRequestBytes;

  public FlowController(FlowControlSettings settings) {
    switch (settings.getLimitExceededBehavior()) {
      case ThrowException:
        this.failOnLimits = true;
        break;
      case Block:
        this.failOnLimits = false;
        break;
      case Ignore:
        this.failOnLimits = false;
        this.maxOutstandingElementCount = null;
        this.maxOutstandingRequestBytes = null;
        this.outstandingElementCount = null;
        this.outstandingByteCount = null;
        return;
      default:
        throw new IllegalArgumentException(
            "Unknown LimitBehaviour: " + settings.getLimitExceededBehavior());
    }
    this.maxOutstandingElementCount = settings.getMaxOutstandingElementCount();
    this.maxOutstandingRequestBytes = settings.getMaxOutstandingRequestBytes();
    outstandingElementCount =
        maxOutstandingElementCount != null ? new Semaphore64(maxOutstandingElementCount) : null;
    outstandingByteCount =
        maxOutstandingRequestBytes != null ? new Semaphore64(maxOutstandingRequestBytes) : null;
  }

  public void reserve(long elements, long bytes) throws FlowControlException {
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
      long permitsToDraw = Math.min(bytes, maxOutstandingRequestBytes);
      if (!failOnLimits) {
        outstandingByteCount.acquireUninterruptibly(permitsToDraw);
      } else if (!outstandingByteCount.tryAcquire(permitsToDraw)) {
        if (outstandingElementCount != null) {
          outstandingElementCount.release(elements);
        }
        throw new MaxOutstandingRequestBytesReachedException(maxOutstandingRequestBytes);
      }
    }
  }

  public void release(long elements, long bytes) {
    Preconditions.checkArgument(elements >= 0);
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
