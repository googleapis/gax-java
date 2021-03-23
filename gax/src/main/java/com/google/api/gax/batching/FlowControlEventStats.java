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
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Record the statistics of flow control events. Currently this class only captures the last flow
 * control event. But it could be expanded to record more information in the future.
 *
 * <p>If {@link FlowController.LimitExceededBehavior} is {@link LimitExceededBehavior#Block} and
 * {@link FlowController#reserve(long, long)} takes longer than expected, record the flow control
 * event with the throttled time. For example:
 *
 * <pre>{@code
 * Stopwatch stopwatch = Stopwatch.createStarted();
 * flowController.reserve(10, 10);
 * long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
 * // If reserve takes longer than 1 millisecond, mark it as a flow control event
 * if (elapsed > 1) {
 *    flowControlEventStats.recordFlowControlEvent(FlowControlEvent.createReserveDelayed(elapsed));
 * }
 * }</pre>
 *
 * If {@link FlowController.LimitExceededBehavior} is {@link LimitExceededBehavior#ThrowException}
 * and {@link FlowController#reserve(long, long)} throws a {@link FlowControlException}, record the
 * flow control event with the exception. For example:
 *
 * <pre>{@code
 * try {
 *   flowController.reserve(10, 10);
 * } catch (FlowControlException e) {
 *   flowControlEventStats.recordFlowControlEvent(FlowControlEvent.createReserveDenied(exception));
 * }
 * }</pre>
 */
@InternalApi("For google-cloud-java client use only")
public class FlowControlEventStats {

  private volatile FlowControlEvent lastFlowControlEvent;

  // We only need the last event to check if there was throttling in the past X minutes, so this
  // doesn't need to be super accurate.
  void recordFlowControlEvent(FlowControlEvent event) {
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
  @AutoValue
  public abstract static class FlowControlEvent implements Comparable<FlowControlEvent> {
    static FlowControlEvent createReserveDelayed(long throttledTimeInMs) {
      return createReserveDelayed(System.currentTimeMillis(), throttledTimeInMs);
    }

    static FlowControlEvent createReserveDenied(FlowControlException exception) {
      return createReserveDenied(System.currentTimeMillis(), exception);
    }

    /** Package-private for use in testing. */
    @VisibleForTesting
    static FlowControlEvent createReserveDelayed(long timestampMs, long throttledTimeInMs) {
      Preconditions.checkArgument(timestampMs > 0, "timestamp must be greater than 0");
      Preconditions.checkArgument(throttledTimeInMs > 0, "throttled time must be greater than 0");
      return new AutoValue_FlowControlEventStats_FlowControlEvent(
          timestampMs, throttledTimeInMs, null);
    }

    /** Package-private for use in testing. */
    @VisibleForTesting
    static FlowControlEvent createReserveDenied(long timestampMs, FlowControlException exception) {
      Preconditions.checkArgument(timestampMs > 0, "timestamp must be greater than 0");
      Preconditions.checkArgument(
          exception != null, "FlowControlException can't be null when reserve is denied");
      return new AutoValue_FlowControlEventStats_FlowControlEvent(timestampMs, null, exception);
    }

    public abstract long getTimestampMs();

    @Nullable
    public abstract Long getThrottledTimeInMs();

    @Nullable
    public abstract FlowControlException getException();

    @Nullable
    public Long getThrottledTime(TimeUnit timeUnit) {
      return getThrottledTimeInMs() == null
          ? null
          : timeUnit.convert(getThrottledTimeInMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(FlowControlEvent o) {
      return Long.compare(this.getTimestampMs(), o.getTimestampMs());
    }
  }
}
