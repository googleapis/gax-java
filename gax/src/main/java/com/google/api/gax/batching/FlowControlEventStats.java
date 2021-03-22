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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/** Record the statistics of flow control events. */
@InternalApi("For google-cloud-java client use only")
public class FlowControlEventStats {

  // Currently we're only interested in the most recent flow control event, but this class can be
  // expanded to record other stats.
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
   * A flow control event. Record throttled time if {@link LimitExceededBehavior} is {@link
   * LimitExceededBehavior#Block}, or the exception if the behavior is {@link
   * LimitExceededBehavior#ThrowException}.
   */
  public static final class FlowControlEvent implements Comparable<FlowControlEvent> {
    private long timestampMs;
    private Long throttledTimeInMs;
    private FlowControlException exception;

    public static FlowControlEvent create(long throttledTimeInMs) {
      return create(System.currentTimeMillis(), throttledTimeInMs);
    }

    public static FlowControlEvent create(FlowControlException exception) {
      return create(System.currentTimeMillis(), exception);
    }

    /** Package-private for use in testing. */
    @VisibleForTesting
    static FlowControlEvent create(long timestampMs, long throttledTimeInMs) {
      return new FlowControlEvent(timestampMs, throttledTimeInMs, null);
    }

    /** Package-private for use in testing. */
    @VisibleForTesting
    static FlowControlEvent create(long timestampMs, FlowControlException exception) {
      return new FlowControlEvent(timestampMs, null, exception);
    }

    private FlowControlEvent(
        long timestampMs,
        @Nullable Long throttledTimeInMs,
        @Nullable FlowControlException exception) {
      Preconditions.checkArgument(
          throttledTimeInMs != null || exception != null,
          "a flow control event needs to have throttledTime or FlowControlException");
      this.timestampMs = timestampMs;
      this.throttledTimeInMs = throttledTimeInMs;
      this.exception = exception;
    }

    public long getTimestampMs() {
      return timestampMs;
    }

    @Nullable
    public Long getThrottledTime(TimeUnit timeUnit) {
      return throttledTimeInMs == null
          ? null
          : timeUnit.convert(throttledTimeInMs, TimeUnit.MILLISECONDS);
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
