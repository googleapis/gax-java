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
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import org.joda.time.Duration;

/**
 * Represents the bundling settings to use for an API method that is capable of bundling.
 *
 * <p>
 * Warning: With the wrong settings, it is possible to cause long periods of dead waiting time.
 *
 * <p>
 * When bundling is turned on for an API method, a call to that method will result in the request
 * being queued up with other requests. When any of the set thresholds are reached, the queued up
 * requests are packaged together in a bundle and set to the service as a single RPC. When the
 * response comes back, it is split apart into individual responses according to the individual
 * input requests.
 *
 * <p>
 * There are several supported thresholds:
 *
 * <p>
 *
 * <ul>
 * <li><b>Delay Threshold</b>: Counting from the time that the first message is queued, once this
 * delay has passed, then send the bundle.
 * <li><b>Message Count Threshold</b>: Once this many messages are queued, send all of the messages
 * in a single call, even if the delay threshold hasn't elapsed yet.
 * <li><b>Request Byte Threshold</b>: Once the number of bytes in the bundled request reaches this
 * threshold, send all of the messages in a single call, even if neither the delay or message count
 * thresholds have been exceeded yet.
 * </ul>
 *
 * <p>
 * These thresholds are treated as triggers, not as limits. Thus, if a request is made with 2x the
 * message count threshold, it will not be split apart (unless one of the limits listed further down
 * is crossed); only one bundle will be sent. Each threshold is an independent trigger and doesn't
 * have any knowledge of the other thresholds.
 *
 * <p>
 * Two of the values above also have limits:
 *
 * <p>
 *
 * <ul>
 * <li><b>Message Count Limit</b>: The limit of the number of messages that the server will accept
 * in a single request.
 * <li><b>Request Byte Limit</b>: The limit of the byte size of a request that the server will
 * accept.
 * </ul>
 *
 * <p>
 * For these values, individual requests that surpass the limit are rejected, and the bundling logic
 * will not bundle together requests in the resulting bundle will surpass the limit. Thus, a bundle
 * can be sent that is actually under the threshold if the next request would put the combined
 * request over the limit.
 */
@AutoValue
public abstract class BundlingSettings {
  /** Get the element count threshold to use for bundling. */
  @Nullable
  public abstract Long getElementCountThreshold();

  /** Get the request byte threshold to use for bundling. */
  @Nullable
  public abstract Long getRequestByteThreshold();

  /** Get the delay threshold to use for bundling. */
  @Nullable
  public abstract Duration getDelayThreshold();

  /** Returns the Boolean object to indicate if the bundling is enabled. Default to true */
  public abstract Boolean getIsEnabled();

  /** Get a new builder. */
  public static Builder newBuilder() {
    return new AutoValue_BundlingSettings.Builder().setIsEnabled(true);
  }

  /** Get a builder with the same values as this object. */
  public Builder toBuilder() {
    return new AutoValue_BundlingSettings.Builder(this);
  }

  /**
   * See the class documentation of {@link BundlingSettings} for a description of the different
   * values that can be set.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Set the element count threshold to use for bundling. After this many elements are
     * accumulated, they will be wrapped up in a bundle and sent.
     */
    public abstract Builder setElementCountThreshold(Long elementCountThreshold);

    @Deprecated
    public Builder setElementCountThreshold(Integer elementCountThreshold) {
      return setElementCountThreshold(elementCountThreshold.longValue());
    }

    /**
     * Set the request byte threshold to use for bundling. After this many bytes are accumulated,
     * the elements will be wrapped up in a bundle and sent.
     */
    public abstract Builder setRequestByteThreshold(Long requestByteThreshold);

    @Deprecated
    public Builder setRequestByteThreshold(Integer requestByteThreshold) {
      return setRequestByteThreshold(requestByteThreshold.longValue());
    }

    /**
     * Set the delay threshold to use for bundling. After this amount of time has elapsed (counting
     * from the first element added), the elements will be wrapped up in a bundle and sent. This
     * value should not be set too high, usually on the order of milliseconds. Otherwise, calls
     * might appear to never complete.
     */
    public abstract Builder setDelayThreshold(Duration delayThreshold);

    /**
     * Set if the bundling should be enabled. If set to false, the bundling logic will be disabled
     * and the simple API call will be used. Default to true.
     */
    public abstract Builder setIsEnabled(Boolean enabled);

    abstract BundlingSettings autoBuild();

    /** Build the BundlingSettings object. */
    public BundlingSettings build() {
      BundlingSettings settings = autoBuild();
      Preconditions.checkArgument(
          settings.getElementCountThreshold() == null || settings.getElementCountThreshold() > 0,
          "elementCountThreshold must be either unset or positive");
      Preconditions.checkArgument(
          settings.getRequestByteThreshold() == null || settings.getRequestByteThreshold() > 0,
          "requestByteThreshold must be either unset or positive");
      Preconditions.checkArgument(
          settings.getDelayThreshold() == null
              || settings.getDelayThreshold().compareTo(Duration.ZERO) > 0,
          "delayThreshold must be either unset or positive");
      return settings;
    }
  }
}
