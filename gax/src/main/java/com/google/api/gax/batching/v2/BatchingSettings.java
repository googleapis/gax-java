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
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import org.threeten.bp.Duration;

/**
 * Represents the batching settings to use for an API method that is capable of batching.
 *
 * <p>Each batching client must define their own set of default values for these thresholds, which
 * would be the safest behavior for their jobs.
 *
 * <p>The default instance of this settings does not have any default values. Users are expected to
 * configure batching thresholds explicitly: the element count, the request bytes count or the delay
 * threshold. Thresholds can be disabled(meaning immediate result of each input elements) by setting
 * its value to 0.
 *
 * <p>Warning: With the incorrect settings, it is possible to cause long periods of dead waiting
 * time.
 *
 * <p>When batching is configured for an API method, a call to that method will result in the
 * request being queued up with other requests. When any of the set thresholds are reached, the
 * queued up requests are packaged together in a batch and set to the service as a single RPC. When
 * the response comes back, it is split apart into individual responses according to the individual
 * input requests.
 *
 * <p>There are several supported thresholds:
 *
 * <ul>
 *   <li><b>Delay Threshold</b>: Counting from the time when the first message is queued, once this
 *       duration has passed, then send the batch.
 *   <li><b>Element Count Threshold</b>: Once this many elements are queued, send all of the
 *       elements in a single call, even if neither the delay nor the request byte threshold has not
 *       been exceeded yet.
 *   <li><b>Request Byte Threshold</b>: Once the number of bytes in the batched request reaches this
 *       threshold, send all of the elements in a single call, even if neither the delay nor the
 *       element count threshold has not been exceeded yet.
 * </ul>
 *
 * <p>These thresholds are treated as triggers, not as limits. Each threshold is an independent
 * trigger and doesn't have any knowledge of the other thresholds.
 */
@BetaApi("The surface for batching is not stable yet and may change in the future.")
@AutoValue
public abstract class BatchingSettings {

  /** Get the element count threshold to use for batching. */
  public abstract int getElementCountThreshold();

  /** Get the request byte threshold to use for batching. */
  public abstract long getRequestByteThreshold();

  /** Get the delay threshold to use for batching. */
  public abstract Duration getDelayThreshold();

  /** Get a new builder. */
  public static Builder newBuilder() {
    return new AutoValue_BatchingSettings.Builder();
  }

  /** Get a builder with the same values as this object. */
  public abstract Builder toBuilder();

  /**
   * See the class documentation of {@link BatchingSettings} for a description of the different
   * values that can be set.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Set the element count threshold to use for batching. After this many elements are
     * accumulated, they will be wrapped up in a batch and sent.
     */
    public abstract Builder setElementCountThreshold(int elementCountThreshold);

    /**
     * Set the request byte threshold to use for batching. After this many bytes are accumulated,
     * the elements will be wrapped up in a batch and sent.
     */
    public abstract Builder setRequestByteThreshold(long requestByteThreshold);

    /**
     * Set the delay threshold to use for batching. After this amount of time has elapsed (counting
     * from the first element added), the elements will be wrapped up in a batch and sent. This
     * value should not be set too high, usually on the order of milliseconds. Otherwise, calls
     * might appear to never complete.
     */
    public abstract Builder setDelayThreshold(Duration delayThreshold);

    abstract BatchingSettings autoBuild();

    /** Build the BatchingSettings object. */
    public BatchingSettings build() {
      BatchingSettings settings = autoBuild();
      Preconditions.checkState(
          settings.getElementCountThreshold() >= 0, "elementCountThreshold cannot be negative");
      Preconditions.checkState(
          settings.getRequestByteThreshold() >= 0, "requestByteThreshold cannot be negative");
      Preconditions.checkState(
          settings.getDelayThreshold().compareTo(Duration.ZERO) > 0,
          "delayThreshold must be positive");
      return settings;
    }
  }
}
