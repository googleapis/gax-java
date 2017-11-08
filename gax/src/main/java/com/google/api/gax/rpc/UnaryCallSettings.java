/*
 * Copyright 2016, Google LLC All rights reserved.
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
package com.google.api.gax.rpc;

import com.google.api.core.InternalExtensionOnly;
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
 * <p>UnaryCallSettings contains a concrete builder class, {@link UnaryCallSettings.Builder}. This
 * builder class cannot be used to create an instance of UnaryCallSettings, because
 * UnaryCallSettings is an abstract class.
 */
@InternalExtensionOnly
public class UnaryCallSettings<RequestT, ResponseT> {

  private final ImmutableSet<StatusCode.Code> retryableCodes;
  private final RetrySettings retrySettings;

  /**
   * See the class documentation of {@link UnaryCallSettings} for a description of what retryable
   * codes do.
   */
  public final Set<StatusCode.Code> getRetryableCodes() {
    return retryableCodes;
  }

  /**
   * See the class documentation of {@link UnaryCallSettings} for a description of what retry
   * settings do.
   */
  public final RetrySettings getRetrySettings() {
    return retrySettings;
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newUnaryCallSettingsBuilder() {
    return new Builder<>();
  }

  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  protected UnaryCallSettings(Builder<RequestT, ResponseT> builder) {
    this.retryableCodes = ImmutableSet.copyOf(builder.retryableCodes);
    this.retrySettings = builder.retrySettings;
  }

  /**
   * A base builder class for {@link UnaryCallSettings}. This class cannot be used to create an
   * instance of the abstract base class UnaryCallSettings. See the class documentation of {@link
   * UnaryCallSettings} for a description of the different values that can be set, and for a
   * description of when this builder may be used. Builders for concrete derived classes can be used
   * to create instances of those classes.
   */
  public static class Builder<RequestT, ResponseT> {

    private Set<StatusCode.Code> retryableCodes;
    private RetrySettings retrySettings;

    protected Builder() {
      retryableCodes = Sets.newHashSet();
      retrySettings = RetrySettings.newBuilder().build();
    }

    protected Builder(UnaryCallSettings<RequestT, ResponseT> unaryCallSettings) {
      setRetryableCodes(unaryCallSettings.retryableCodes);
      setRetrySettings(unaryCallSettings.getRetrySettings());
    }

    /**
     * See the class documentation of {@link UnaryCallSettings} for a description of what retryable
     * codes do.
     */
    public UnaryCallSettings.Builder<RequestT, ResponseT> setRetryableCodes(
        Set<StatusCode.Code> retryableCodes) {
      this.retryableCodes = Sets.newHashSet(retryableCodes);
      return this;
    }

    /**
     * See the class documentation of {@link UnaryCallSettings} for a description of what retryable
     * codes do.
     */
    public UnaryCallSettings.Builder<RequestT, ResponseT> setRetryableCodes(
        StatusCode.Code... codes) {
      this.setRetryableCodes(Sets.newHashSet(codes));
      return this;
    }

    public UnaryCallSettings.Builder<RequestT, ResponseT> setRetrySettings(
        RetrySettings retrySettings) {
      this.retrySettings = Preconditions.checkNotNull(retrySettings);
      return this;
    }

    /** Disables retries and sets the RPC timeout. */
    public UnaryCallSettings.Builder<RequestT, ResponseT> setSimpleTimeoutNoRetries(
        Duration timeout) {
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
    public Set<StatusCode.Code> getRetryableCodes() {
      return this.retryableCodes;
    }

    public RetrySettings getRetrySettings() {
      return this.retrySettings;
    }

    /**
     * Builds an instance of the containing class. This operation is unsupported on the abstract
     * base class UnaryCallSettings, but is valid on concrete derived classes.
     */
    public UnaryCallSettings<RequestT, ResponseT> build() {
      return new UnaryCallSettings<>(this);
    }
  }
}
