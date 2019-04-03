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
import com.google.api.core.InternalExtensionOnly;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.common.base.Preconditions;
import java.util.Set;

/**
 * This is an extension to {@link com.google.api.gax.rpc.BatchingCallSettings}, to support for
 * {@link ElementT} and {@link ElementResultT} types.
 *
 * @param <ElementT> The request type to perform batching.
 * @param <ElementResultT> The response type of an entry object.
 * @param <RequestT> The request wrapper type which bundles {@link ElementT}.
 * @param <ResponseT> The response wrapper type which bundles {@link ElementResultT}.
 */
@BetaApi("The surface for batching is not stable yet and may change in the future.")
@InternalExtensionOnly
public final class BatchingCallSettings<ElementT, ElementResultT, RequestT, ResponseT>
    extends UnaryCallSettings<RequestT, ResponseT> {
  private final BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT>
      batchingDescriptor;
  private final BatchingSettings batchingSettings;

  private BatchingCallSettings(Builder<ElementT, ElementResultT, RequestT, ResponseT> builder) {
    super(builder);
    this.batchingDescriptor = builder.batchingDescriptor;
    this.batchingSettings = Preconditions.checkNotNull(builder.batchingSettings);
  }

  public BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> getBatchingDescriptor() {
    return batchingDescriptor;
  }

  public BatchingSettings getBatchingSettings() {
    return batchingSettings;
  }

  public static <ElementT, ElementResultT, RequestT, ResponseT>
      Builder<ElementT, ElementResultT, RequestT, ResponseT> newBuilder(
          BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> batchingDescriptor) {
    return new Builder<>(batchingDescriptor);
  }

  @Override
  public final Builder<ElementT, ElementResultT, RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<ElementT, ElementResultT, RequestT, ResponseT>
      extends UnaryCallSettings.Builder<RequestT, ResponseT> {

    private BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> batchingDescriptor;
    private BatchingSettings batchingSettings;

    private Builder(
        BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT> batchingDescriptor) {
      this.batchingDescriptor = batchingDescriptor;
    }

    private Builder(BatchingCallSettings<ElementT, ElementResultT, RequestT, ResponseT> settings) {
      super(settings);
      this.batchingDescriptor = settings.batchingDescriptor;
      this.batchingSettings = settings.batchingSettings;
    }

    public BatchingDescriptor<ElementT, ElementResultT, RequestT, ResponseT>
        getBatchingDescriptor() {
      return batchingDescriptor;
    }

    public Builder<ElementT, ElementResultT, RequestT, ResponseT> setBatchingSettings(
        BatchingSettings batchingSettings) {
      this.batchingSettings = batchingSettings;
      return this;
    }

    public BatchingSettings getBatchingSettings() {
      return batchingSettings;
    }

    @Override
    public Builder<ElementT, ElementResultT, RequestT, ResponseT> setRetryableCodes(
        Set<StatusCode.Code> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<ElementT, ElementResultT, RequestT, ResponseT> setRetryableCodes(
        StatusCode.Code... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<ElementT, ElementResultT, RequestT, ResponseT> setRetrySettings(
        RetrySettings retrySettings) {
      super.setRetrySettings(retrySettings);
      return this;
    }

    @Override
    public BatchingCallSettings<ElementT, ElementResultT, RequestT, ResponseT> build() {
      return new BatchingCallSettings<>(this);
    }
  }
}
