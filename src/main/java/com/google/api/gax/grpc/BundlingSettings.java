/*
 * Copyright 2015, Google Inc.
 * All rights reserved.
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

import com.google.api.gax.bundling.BundlingThreshold;
import com.google.api.gax.bundling.ExternalThreshold;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

import org.joda.time.Duration;

/**
 * Class which represents the bundling settings to use for an API method that
 * is capable of bundling.
 */
@AutoValue
public abstract class BundlingSettings {
  /**
   * Get the element count threshold to use for bundling.
   */
  @Nullable
  public abstract Integer getElementCountThreshold();

  /**
   * Get the element count limit to use for bundling.
   */
  @Nullable
  public abstract Integer getElementCountLimit();

  /**
   * Get the request byte threshold to use for bundling.
   */
  @Nullable
  public abstract Integer getRequestByteThreshold();

  /**
   * Get the request byte limit to use for bundling.
   */
  @Nullable
  public abstract Integer getRequestByteLimit();

  /**
   * Get the delay threshold to use for bundling.
   */
  public abstract Duration getDelayThreshold();

  /**
   * Get the blocking call count threshold to use for bundling.
   */
  @Nullable
  public abstract Integer getBlockingCallCountThreshold();

  /**
   * Get a new builder.
   */
  public static Builder newBuilder() {
    return new AutoValue_BundlingSettings.Builder();
  }

  /**
   * Get a builder with the same values as this object.
   */
  public Builder toBuilder() {
    return new AutoValue_BundlingSettings.Builder(this);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Set the element count threshold to use for bundling. After this many elements
     * are accumulated, they will be wrapped up in a bundle and sent.
     */
    public abstract Builder setElementCountThreshold(Integer elementCountThreshold);

    /**
     * Set the element count limit to use for bundling. Any individual requests with
     * more than this many elements will be rejected outright, and bundles will not
     * be formed with more than this many elements.
     */
    public abstract Builder setElementCountLimit(Integer elementCountLimit);

    /**
     * Set the request byte threshold to use for bundling. After this many bytes
     * are accumulated, the elements will be wrapped up in a bundle and sent.
     */
    public abstract Builder setRequestByteThreshold(Integer requestByteThreshold);

    /**
     * Set the request byte limit to use for bundling. Any individual requests with
     * more than this many bytes will be rejected outright, and bundles will not
     * be formed with more than this many bytes.
     */
    public abstract Builder setRequestByteLimit(Integer requestByteLimit);

    /**
     * Set the delay threshold to use for bundling. After this amount of time has
     * elapsed (counting from the first element added), the elements will be wrapped
     * up in a bundle and sent.
     */
    public abstract Builder setDelayThreshold(Duration delayThreshold);

    /**
     * Set the blocking call count threshold for bundling. After this many blocking
     * calls are made, the elements will be wrapped up in a bundle and sent. This
     * defaults to 1. Do not set this to a number higher than the number of threads
     * that are capable of blocking on the bundler, or else your application will
     * suffer dead time while it waits for the delay threshold to trip.
     */
    public abstract Builder setBlockingCallCountThreshold(Integer blockingCallCountThreshold);

    abstract BundlingSettings autoBuild();

    /**
     * Build the BundlingSettings object.
     */
    public BundlingSettings build() {
      return autoBuild();
    }
  }

}
