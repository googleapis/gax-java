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

import com.google.api.core.InternalApi;
import com.google.api.gax.batching.FlowController.LimitExceededBehavior;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;

/** Settings for dynamic flow control */
@AutoValue
@InternalApi
public abstract class DynamicFlowControlSettings {

  @Nullable
  public abstract Long getInitialOutstandingElementCount();

  @Nullable
  public abstract Long getInitialOutstandingRequestBytes();

  @Nullable
  public abstract Long getMaxOutstandingElementCount();

  @Nullable
  public abstract Long getMaxOutstandingRequestBytes();

  @Nullable
  public abstract Long getMinOutstandingElementCount();

  @Nullable
  public abstract Long getMinOutstandingRequestBytes();

  public abstract LimitExceededBehavior getLimitExceededBehavior();

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new AutoValue_DynamicFlowControlSettings.Builder()
        .setLimitExceededBehavior(LimitExceededBehavior.Block);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setInitialOutstandingElementCount(Long value);

    public abstract Builder setInitialOutstandingRequestBytes(Long value);

    public abstract Builder setMaxOutstandingElementCount(Long value);

    public abstract Builder setMaxOutstandingRequestBytes(Long value);

    public abstract Builder setMinOutstandingElementCount(Long value);

    public abstract Builder setMinOutstandingRequestBytes(Long value);

    public abstract Builder setLimitExceededBehavior(LimitExceededBehavior value);

    abstract DynamicFlowControlSettings autoBuild();

    public DynamicFlowControlSettings build() {
      DynamicFlowControlSettings settings = autoBuild();
      Preconditions.checkArgument(
          (settings.getInitialOutstandingElementCount() != null
                  && settings.getMinOutstandingElementCount() != null
                  && settings.getMaxOutstandingElementCount() != null)
              || (settings.getInitialOutstandingElementCount() == null
                  && settings.getMinOutstandingElementCount() == null
                  && settings.getMaxOutstandingElementCount() == null),
          "Throttling on element count is disabled by default, to enable this setting, all the initial, min and max thresholds must be set");
      Preconditions.checkArgument(
          (settings.getInitialOutstandingRequestBytes() != null
                  && settings.getMinOutstandingRequestBytes() != null
                  && settings.getMaxOutstandingRequestBytes() != null)
              || (settings.getInitialOutstandingRequestBytes() == null
                  && settings.getMinOutstandingRequestBytes() == null
                  && settings.getMaxOutstandingRequestBytes() == null),
          "Throttling on number of bytes is disabled by default, to enable this setting, all the initial, min and max thresholds must be set");
      if (settings.getInitialOutstandingElementCount() != null) {
        Preconditions.checkArgument(
            settings.getMinOutstandingElementCount() > 0
                && settings.getInitialOutstandingElementCount()
                    <= settings.getMaxOutstandingElementCount()
                && settings.getInitialOutstandingElementCount()
                    >= settings.getMinOutstandingElementCount(),
            "If throttling on element count is set, the thresholds must be greater than 0, and min <= initial <= max");
      }
      if (settings.getInitialOutstandingRequestBytes() != null) {
        Preconditions.checkArgument(
            settings.getMinOutstandingRequestBytes() > 0
                && settings.getInitialOutstandingRequestBytes()
                    <= settings.getMaxOutstandingRequestBytes()
                && settings.getInitialOutstandingRequestBytes()
                    >= settings.getMinOutstandingRequestBytes(),
            "If throttling on number of bytes is set, the thresholds must be greater than 0, and min <= initial <= max");
      }
      return settings;
    }
  }
}
