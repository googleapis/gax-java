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
package com.google.api.gax.bundling;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.concurrent.Semaphore;
import javax.annotation.Nullable;

/** Provides flow control capability. */
public class FlowController {
  /** Base exception that signals a flow control state. */
  public abstract class FlowControlException extends Exception {
    private FlowControlException() {}
  }

  /**
   * Returned as a future exception when client-side flow control is enforced based on the maximum
   * number of outstanding in-memory messages.
   */
  public final class MaxOutstandingMessagesReachedException extends FlowControlException {
    private final int currentMaxMessages;

    public MaxOutstandingMessagesReachedException(int currentMaxMessages) {
      this.currentMaxMessages = currentMaxMessages;
    }

    public int getCurrentMaxBundleMessages() {
      return currentMaxMessages;
    }

    @Override
    public String toString() {
      return String.format(
          "The maximum number of bundle messages: %d have been reached.", currentMaxMessages);
    }
  }

  /**
   * Returned as a future exception when client-side flow control is enforced based on the maximum
   * number of unacknowledged in-memory bytes.
   */
  public final class MaxOutstandingBytesReachedException extends FlowControlException {
    private final int currentMaxBytes;

    public MaxOutstandingBytesReachedException(int currentMaxBytes) {
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

  @AutoValue
  public abstract static class Settings {
    public static Settings DEFAULT =
        newBuilder()
            .setMaxOutstandingBytes(Optional.<Integer>absent())
            .setMaxOutstandingMessages(Optional.<Integer>absent())
            .build();

    /** Maximum number of outstanding messages to keep in memory before enforcing flow control. */
    public abstract Optional<Integer> getMaxOutstandingMessages();

    /** Maximum number of outstanding bytes to keep in memory before enforcing flow control. */
    public abstract Optional<Integer> getMaxOutstandingBytes();

    public Builder toBuilder() {
      return new AutoValue_FlowController_Settings.Builder(this);
    }

    public static Builder newBuilder() {
      return new AutoValue_FlowController_Settings.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setMaxOutstandingMessages(Optional<Integer> value);

      public abstract Builder setMaxOutstandingBytes(Optional<Integer> value);

      abstract Settings autoBuild();

      public Settings build() {
        Settings settings = autoBuild();
        Preconditions.checkArgument(
            settings.getMaxOutstandingMessages().or(1) > 0,
            "maxOutstandingMessages limit is disabled by default, but if set it must be set to a value greater than 0.");
        Preconditions.checkArgument(
            settings.getMaxOutstandingBytes().or(1) > 0,
            "maxOutstandingBytes limit is disabled by default, but if set it must be set to a value greater than 0.");
        return settings;
      }
    }
  }

  @Nullable private final Semaphore outstandingMessageCount;
  @Nullable private final Semaphore outstandingByteCount;
  private final boolean failOnLimits;
  private final Optional<Integer> maxOutstandingMessages;
  private final Optional<Integer> maxOutstandingBytes;

  public FlowController(Settings settings, boolean failOnFlowControlLimits) {
    this.maxOutstandingMessages = settings.getMaxOutstandingMessages();
    this.maxOutstandingBytes = settings.getMaxOutstandingBytes();
    outstandingMessageCount =
        maxOutstandingMessages.isPresent() ? new Semaphore(maxOutstandingMessages.get()) : null;
    outstandingByteCount =
        maxOutstandingBytes.isPresent() ? new Semaphore(maxOutstandingBytes.get()) : null;
    this.failOnLimits = failOnFlowControlLimits;
  }

  public void reserve(int messages, int bytes) throws FlowControlException {
    Preconditions.checkArgument(messages > 0);

    if (outstandingMessageCount != null) {
      if (!failOnLimits) {
        outstandingMessageCount.acquireUninterruptibly(messages);
      } else if (!outstandingMessageCount.tryAcquire(messages)) {
        throw new MaxOutstandingMessagesReachedException(maxOutstandingMessages.get());
      }
    }

    // Will always allow to send a message even if it is larger than the flow control limit,
    // if it doesn't then it will deadlock the thread.
    if (outstandingByteCount != null) {
      int permitsToDraw = Math.min(bytes, maxOutstandingBytes.get());
      if (!failOnLimits) {
        outstandingByteCount.acquireUninterruptibly(permitsToDraw);
      } else if (!outstandingByteCount.tryAcquire(permitsToDraw)) {
        throw new MaxOutstandingBytesReachedException(maxOutstandingBytes.get());
      }
    }
  }

  public void release(int messages, int bytes) {
    Preconditions.checkArgument(messages > 0);

    if (outstandingMessageCount != null) {
      outstandingMessageCount.release(messages);
    }
    if (outstandingByteCount != null) {
      // Need to return at most as much bytes as it can be drawn.
      int permitsToReturn = Math.min(bytes, maxOutstandingBytes.get());
      outstandingByteCount.release(permitsToReturn);
    }
  }
}
