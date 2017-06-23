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
package com.google.api.gax.rpc;

import com.google.api.core.BetaApi;
import com.google.api.gax.retrying.RetrySettings;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;
import org.threeten.bp.Duration;

/**
 * A base settings class to configure a UnaryCallable. An instance of UnaryCallSettings is not
 * sufficient on its own to construct a UnaryCallable; a concrete derived type is necessary.
 *
 * <p>This base class includes settings that are applicable to all calls, which currently is just
 * retry settings.
 *
 * <p>Retry configuration is composed of two parts: the retryable codes, and the retry settings. The
 * retryable codes indicate which codes cause a retry to occur, and the retry settings configure the
 * retry logic when the retry needs to happen. To turn off retries, set the retryable codes needs to
 * be set to the empty set.
 *
 * <p>UnaryCallSettings contains a concrete builder class, {@link Builder}. This builder class
 * cannot be used to create an instance of UnaryCallSettings, because UnaryCallSettings is an
 * abstract class.
 */
@BetaApi
public abstract class UnaryCallSettings {

  private final ImmutableSet<StatusCode> retryableCodes;
  private final RetrySettings retrySettings;

  /**
   * See the class documentation of {@link UnaryCallSettings} for a description of what retryable
   * codes do.
   */
  public final Set<StatusCode> getRetryableCodes() {
    return retryableCodes;
  }

  /**
   * See the class documentation of {@link UnaryCallSettings} for a description of what retry
   * settings do.
   */
  public final RetrySettings getRetrySettings() {
    return retrySettings;
  }

  public static Builder newUnaryCallSettingsBuilder() {
    return new Builder();
  }

  public abstract Builder toBuilder();

  protected UnaryCallSettings(Set<StatusCode> retryableCodes, RetrySettings retrySettings) {
    this.retryableCodes = ImmutableSet.copyOf(retryableCodes);
    this.retrySettings = retrySettings;
  }

  /**
   * A base builder class for {@link UnaryCallSettings}. This class cannot be used to create an
   * instance of the abstract base class UnaryCallSettings. See the class documentation of {@link
   * UnaryCallSettings} for a description of the different values that can be set, and for a
   * description of when this builder may be used. Builders for concrete derived classes can be used
   * to create instances of those classes.
   */
  public static class Builder {

    private Set<StatusCode> retryableCodes;
    private RetrySettings retrySettings;

    protected Builder() {
      retryableCodes = Sets.newHashSet();
      retrySettings = RetrySettings.newBuilder().build();
    }

    protected Builder(UnaryCallSettings unaryCallSettings) {
      setRetryableCodes(unaryCallSettings.retryableCodes);
      setRetrySettings(unaryCallSettings.getRetrySettings());
    }

    /**
     * See the class documentation of {@link UnaryCallSettings} for a description of what retryable
     * codes do.
     */
    public Builder setRetryableCodes(Set<StatusCode> retryableCodes) {
      this.retryableCodes = Sets.newHashSet(retryableCodes);
      return this;
    }

    /**
     * See the class documentation of {@link UnaryCallSettings} for a description of what retryable
     * codes do.
     */
    public Builder setRetryableCodes(StatusCode... codes) {
      this.setRetryableCodes(Sets.newHashSet(codes));
      return this;
    }

    public Builder setRetrySettings(RetrySettings retrySettings) {
      this.retrySettings = Preconditions.checkNotNull(retrySettings);
      return this;
    }

    /** Disables retries and sets the RPC timeout. */
    public Builder setSimpleTimeoutNoRetries(Duration timeout) {
      setRetryableCodes();
      setRetrySettings(
          RetrySettings.newBuilder()
              .setTotalTimeout(timeout)
              .setInitialRetryDelay(Duration.ZERO)
              .setRetryDelayMultiplier(1)
              .setMaxRetryDelay(Duration.ZERO)
              .setInitialRpcTimeout(timeout)
              .setRpcTimeoutMultiplier(1)
              .setMaxRpcTimeout(timeout)
              .setMaxAttempts(1)
              .build());
      return this;
    }

    /**
     * See the class documentation of {@link UnaryCallSettings} for a description of what retryable
     * codes do.
     */
    public Set<StatusCode> getRetryableCodes() {
      return this.retryableCodes;
    }

    public RetrySettings getRetrySettings() {
      return this.retrySettings;
    }

    /**
     * Builds an instance of the containing class. This operation is unsupported on the abstract
     * base class UnaryCallSettings, but is valid on concrete derived classes.
     */
    public UnaryCallSettings build() {
      throw new UnsupportedOperationException(
          "Cannot build an instance of abstract class UnaryCallSettings.");
    }
  }
}
