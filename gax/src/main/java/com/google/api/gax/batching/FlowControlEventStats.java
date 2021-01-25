/*
 * Copyright 2021 Google LLC
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

import static com.google.api.gax.batching.FlowController.LimitExceededBehavior;

import com.google.api.core.InternalApi;
import com.google.api.gax.batching.FlowController.FlowControlException;
import com.google.common.base.Preconditions;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/** Record the statistics of flow control events. */
@InternalApi
public class FlowControlEventStats {

  // Currently we're only interested in the most recent flow control event
  private FlowControlEvent lastFlowControlEvent;

  public synchronized void recordFlowControlEvent(FlowControlEvent event) {
    if (lastFlowControlEvent == null || event.compareTo(lastFlowControlEvent) > 0) {
      lastFlowControlEvent = event;
    }
  }

  public FlowControlEvent getLastFlowControlEvent() {
    return lastFlowControlEvent;
  }

  /**
   * A flow control event. Depend on {@link LimitExceededBehavior}, record throttled time if the
   * behavior is {@link LimitExceededBehavior#Block}, or the exception if the behavior is {@link
   * LimitExceededBehavior#ThrowException}.
   */
  public static final class FlowControlEvent implements Comparable<FlowControlEvent> {
    private long timestampMs;
    private Long throttledTimeInNanos;
    private FlowControlException exception;

    public static FlowControlEvent create(long throttledTimeInNanos) {
      return create(System.currentTimeMillis(), throttledTimeInNanos);
    }

    public static FlowControlEvent create(FlowControlException exception) {
      return create(System.currentTimeMillis(), exception);
    }

    public static FlowControlEvent create(long timestampMs, long throttledTimeInNanos) {
      return new FlowControlEvent(timestampMs, throttledTimeInNanos, null);
    }

    public static FlowControlEvent create(long timestampMs, FlowControlException exception) {
      return new FlowControlEvent(timestampMs, null, exception);
    }

    private FlowControlEvent(
        long timestampMs,
        @Nullable Long throttledTimeInNanos,
        @Nullable FlowControlException exception) {
      Preconditions.checkArgument(
          throttledTimeInNanos != null || exception != null,
          "a flow control event needs to have throttledTime or FlowControlException");
      this.timestampMs = timestampMs;
      this.throttledTimeInNanos = throttledTimeInNanos;
      this.exception = exception;
    }

    public long getTimestampMs() {
      return timestampMs;
    }

    @Nullable
    public Long getThrottledTime(TimeUnit timeUnit) {
      return timeUnit.convert(throttledTimeInNanos, TimeUnit.NANOSECONDS);
    }

    @Nullable
    public FlowControlException getException() {
      return exception;
    }

    @Override
    public int compareTo(FlowControlEvent o) {
      return Long.compare(this.timestampMs, o.timestampMs);
    }
  }
}
