/*
 * Copyright 2016 Google LLC
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
import com.google.api.core.InternalApi;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;

/** Provides flow control capability. */
@BetaApi("The surface for batching is not stable yet and may change in the future.")
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
    /**
     * Throws {@link MaxOutstandingElementCountReachedException} or {@link
     * MaxOutstandingRequestBytesReachedException}.
     *
     * <p>This might be appropriate in interactive scenarios. For example, a web server might catch
     * these exceptions and report to the user that the system is overloaded and that the user could
     * try again later. It could also be useful in applications that implement custom rate-limiting
     * logic.
     */
    ThrowException,

    /**
     * Waits until the request can be made without exceeding the limit.
     *
     * <p>This might be appropriate in batch-processing, where latencies of individual requests are
     * not important.
     */
    Block,

    /**
     * Disables flow-control.
     *
     * <p>This is provided mainly for debugging and not recommended for production use. Having too
     * many requests in-flight might cause RPCs to fail due to congested network or the computer to
     * run out of memory due to excessive buffering, etc.
     */
    Ignore,
  }

  @Nullable private final Semaphore64 outstandingElementCount;
  @Nullable private final Semaphore64 outstandingByteCount;
  @Nullable private final Long maxOutstandingElementCount;
  @Nullable private final Long maxOutstandingRequestBytes;
  @Nullable private final Long minOutstandingElementCount;
  @Nullable private final Long minOutstandingRequestBytes;
  @Nullable private Long currentOutstandingElementCount;
  @Nullable private Long currentOutstandingRequestBytes;
  private final LimitExceededBehavior limitExceededBehavior;
  private final Object updateThresholdsLock = new Object();

  public FlowController(FlowControlSettings settings) {
    this.limitExceededBehavior = settings.getLimitExceededBehavior();
    switch (settings.getLimitExceededBehavior()) {
      case ThrowException:
      case Block:
        break;
      case Ignore:
        this.maxOutstandingElementCount = null;
        this.maxOutstandingRequestBytes = null;
        this.outstandingElementCount = null;
        this.outstandingByteCount = null;
        this.minOutstandingElementCount = null;
        this.minOutstandingRequestBytes = null;
        this.currentOutstandingElementCount = null;
        this.currentOutstandingRequestBytes = null;
        return;
      default:
        throw new IllegalArgumentException(
            "Unknown LimitBehaviour: " + settings.getLimitExceededBehavior());
    }
    this.maxOutstandingElementCount = settings.getMaxOutstandingElementCount();
    this.minOutstandingElementCount = settings.getMaxOutstandingElementCount();
    this.currentOutstandingElementCount = settings.getMaxOutstandingElementCount();
    if (currentOutstandingElementCount == null) {
      outstandingElementCount = null;
    } else if (settings.getLimitExceededBehavior() == FlowController.LimitExceededBehavior.Block) {
      outstandingElementCount = new BlockingSemaphore(currentOutstandingElementCount);
    } else {
      outstandingElementCount = new NonBlockingSemaphore(currentOutstandingElementCount);
    }

    this.maxOutstandingRequestBytes = settings.getMaxOutstandingRequestBytes();
    this.minOutstandingRequestBytes = settings.getMaxOutstandingRequestBytes();
    this.currentOutstandingRequestBytes = settings.getMaxOutstandingRequestBytes();
    if (currentOutstandingRequestBytes == null) {
      outstandingByteCount = null;
    } else if (settings.getLimitExceededBehavior() == FlowController.LimitExceededBehavior.Block) {
      outstandingByteCount = new BlockingSemaphore(currentOutstandingRequestBytes);
    } else {
      outstandingByteCount = new NonBlockingSemaphore(currentOutstandingRequestBytes);
    }
  }

  public FlowController(DynamicFlowControlSettings settings) {
    this.limitExceededBehavior = settings.getLimitExceededBehavior();
    switch (settings.getLimitExceededBehavior()) {
      case ThrowException:
      case Block:
        break;
      case Ignore:
        this.maxOutstandingElementCount = null;
        this.maxOutstandingRequestBytes = null;
        this.outstandingElementCount = null;
        this.outstandingByteCount = null;
        this.minOutstandingElementCount = null;
        this.minOutstandingRequestBytes = null;
        this.currentOutstandingElementCount = null;
        this.currentOutstandingRequestBytes = null;
        return;
      default:
        throw new IllegalArgumentException(
            "Unknown LimitBehaviour: " + settings.getLimitExceededBehavior());
    }
    this.maxOutstandingElementCount = settings.getMaxOutstandingElementCount();
    this.minOutstandingElementCount = settings.getMinOutstandingElementCount();
    this.currentOutstandingElementCount = settings.getInitialOutstandingElementCount();
    if (currentOutstandingElementCount == null) {
      outstandingElementCount = null;
    } else if (settings.getLimitExceededBehavior() == FlowController.LimitExceededBehavior.Block) {
      outstandingElementCount = new BlockingSemaphore(currentOutstandingElementCount);
    } else {
      outstandingElementCount = new NonBlockingSemaphore(currentOutstandingElementCount);
    }

    this.maxOutstandingRequestBytes = settings.getMaxOutstandingRequestBytes();
    this.minOutstandingRequestBytes = settings.getMinOutstandingRequestBytes();
    this.currentOutstandingRequestBytes = settings.getInitialOutstandingRequestBytes();
    if (currentOutstandingRequestBytes == null) {
      outstandingByteCount = null;
    } else if (settings.getLimitExceededBehavior() == FlowController.LimitExceededBehavior.Block) {
      outstandingByteCount = new BlockingSemaphore(currentOutstandingRequestBytes);
    } else {
      outstandingByteCount = new NonBlockingSemaphore(currentOutstandingRequestBytes);
    }
  }

  public void reserve(long elements, long bytes) throws FlowControlException {
    Preconditions.checkArgument(elements >= 0);
    Preconditions.checkArgument(bytes >= 0);

    if (outstandingElementCount != null) {
      if (!outstandingElementCount.acquire(elements)) {
        throw new MaxOutstandingElementCountReachedException(currentOutstandingElementCount);
      }
    }

    // Will always allow to send a request even if it is larger than the flow control limit,
    // if it doesn't then it will deadlock the thread.
    if (outstandingByteCount != null) {
      long permitsToDraw, permitsOwed;
      boolean acquired;
      synchronized (this) {
        // This needs to be synchronized so currentOutstandingRequestBytes won't get updated to a
        // smaller value before calling acquire
        permitsToDraw = Math.min(bytes, currentOutstandingRequestBytes);
        permitsOwed = bytes - permitsToDraw;
        acquired = outstandingByteCount.acquire(permitsToDraw);
      }
      if (!acquired) {
        if (outstandingElementCount != null) {
          outstandingElementCount.release(elements);
        }
        throw new MaxOutstandingRequestBytesReachedException(currentOutstandingRequestBytes);
      }
      // Make a non blocking call to 'reserve' the extra bytes so it won't deadlock the thread. In
      // total flow controller still reserved the number of bytes that's asked.
      outstandingByteCount.reducePermits(permitsOwed);
    }
  }

  public void release(long elements, long bytes) {
    Preconditions.checkArgument(elements >= 0);
    Preconditions.checkArgument(bytes >= 0);

    if (outstandingElementCount != null) {
      outstandingElementCount.release(elements);
    }
    if (outstandingByteCount != null) {
      outstandingByteCount.release(bytes);
    }
  }

  @InternalApi
  public synchronized void increaseThresholds(long elementSteps, long byteSteps) {
    Preconditions.checkArgument(elementSteps >= 0);
    Preconditions.checkArgument(byteSteps >= 0);
    if (currentOutstandingElementCount != null) {
      long actualStep =
          Math.min(elementSteps, maxOutstandingElementCount - currentOutstandingElementCount);
      outstandingElementCount.release(actualStep);
      currentOutstandingElementCount += actualStep;
    }
    if (currentOutstandingRequestBytes != null) {
      long actualStep =
          Math.min(byteSteps, maxOutstandingRequestBytes - currentOutstandingRequestBytes);
      outstandingByteCount.release(actualStep);
      currentOutstandingRequestBytes += actualStep;
    }
  }

  @InternalApi
  public synchronized void decreaseThresholds(long elementSteps, long byteSteps) {
    Preconditions.checkArgument(elementSteps >= 0);
    Preconditions.checkArgument(byteSteps >= 0);
    if (currentOutstandingElementCount != null) {
      long actualStep =
          Math.min(elementSteps, currentOutstandingElementCount - minOutstandingElementCount);
      outstandingElementCount.reducePermits(actualStep);
      currentOutstandingElementCount -= actualStep;
    }
    if (currentOutstandingRequestBytes != null) {
      long actualStep =
          Math.min(byteSteps, currentOutstandingRequestBytes - minOutstandingRequestBytes);
      outstandingByteCount.reducePermits(actualStep);
      currentOutstandingRequestBytes -= actualStep;
    }
  }

  @InternalApi
  public synchronized void setThresholds(long elements, long bytes) {
    Preconditions.checkArgument(elements > 0);
    Preconditions.checkArgument(bytes > 0);
    if (outstandingElementCount != null) {
      long actualNewValue;
      if (elements < currentOutstandingElementCount) {
        actualNewValue = Math.max(elements, minOutstandingElementCount);
        outstandingElementCount.reducePermits(currentOutstandingElementCount - actualNewValue);
      } else {
        actualNewValue = Math.min(elements, maxOutstandingElementCount);
        outstandingElementCount.release(actualNewValue - currentOutstandingElementCount);
      }
      currentOutstandingElementCount = actualNewValue;
    }

    if (outstandingByteCount != null) {
      long actualNewValue;
      if (bytes < currentOutstandingRequestBytes) {
        actualNewValue = Math.max(bytes, minOutstandingRequestBytes);
        outstandingByteCount.reducePermits(currentOutstandingRequestBytes - actualNewValue);
      } else {
        actualNewValue = Math.min(bytes, maxOutstandingRequestBytes);
        outstandingByteCount.release(actualNewValue - currentOutstandingRequestBytes);
      }
      currentOutstandingRequestBytes = actualNewValue;
    }
  }

  @InternalApi
  public LimitExceededBehavior getLimitExceededBehavior() {
    return limitExceededBehavior;
  }

  @InternalApi
  @Nullable
  public Long getMaxOutstandingElementCount() {
    return maxOutstandingElementCount;
  }

  @InternalApi
  @Nullable
  public Long getMaxOutstandingRequestBytes() {
    return maxOutstandingRequestBytes;
  }

  @InternalApi
  @Nullable
  public Long getMinOutstandingElementCount() {
    return minOutstandingElementCount;
  }

  @InternalApi
  @Nullable
  public Long getMinOutstandingRequestBytes() {
    return minOutstandingRequestBytes;
  }

  @InternalApi
  @Nullable
  public Long getCurrentOutstandingElementCount() {
    return currentOutstandingElementCount;
  }

  @InternalApi
  @Nullable
  public Long getCurrentOutstandingRequestBytes() {
    return currentOutstandingRequestBytes;
  }
}
