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
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.concurrent.Semaphore;
import javax.annotation.Nullable;

/** Settings for {@link FlowController}. */
@AutoValue
public abstract class FlowControlSettings {
  public static FlowControlSettings getDefaultInstance() {
    return FlowControlSettings.newBuilder()
        .setMaxOutstandingRequestBytes(Optional.<Integer>absent())
        .setMaxOutstandingElementCount(Optional.<Integer>absent())
        .build();
  }

  /** Maximum number of outstanding elements to keep in memory before enforcing flow control. */
  public abstract Optional<Integer> getMaxOutstandingElementCount();

  /** Maximum number of outstanding bytes to keep in memory before enforcing flow control. */
  public abstract Optional<Integer> getMaxOutstandingRequestBytes();

  public Builder toBuilder() {
    return new AutoValue_FlowControlSettings.Builder(this);
  }

  public static Builder newBuilder() {
    return new AutoValue_FlowControlSettings.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setMaxOutstandingElementCount(Optional<Integer> value);

    public abstract Builder setMaxOutstandingRequestBytes(Optional<Integer> value);

    abstract FlowControlSettings autoBuild();

    public FlowControlSettings build() {
      FlowControlSettings settings = autoBuild();
      Preconditions.checkArgument(
          settings.getMaxOutstandingElementCount().or(1) > 0,
          "maxOutstandingElementCount limit is disabled by default, but if set it must be set to a value greater than 0.");
      Preconditions.checkArgument(
          settings.getMaxOutstandingRequestBytes().or(1) > 0,
          "maxOutstandingRequestBytes limit is disabled by default, but if set it must be set to a value greater than 0.");
      return settings;
    }
  }
}
