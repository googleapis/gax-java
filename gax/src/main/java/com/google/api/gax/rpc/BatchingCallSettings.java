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
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.retrying.RetrySettings;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * A settings class to configure a {@link UnaryCallable} for calls to an API method that supports
 * batching. The settings are provided using an instance of {@link BatchingSettings}.
 */
@BetaApi
public final class BatchingCallSettings<RequestT, ResponseT>
    extends UnaryCallSettingsTyped<RequestT, ResponseT> {
  private final BatchingDescriptor<RequestT, ResponseT> batchingDescriptor;
  private final BatchingSettings batchingSettings;
  private final FlowController flowController;

  public BatchingDescriptor<RequestT, ResponseT> getBatchingDescriptor() {
    return batchingDescriptor;
  }

  public BatchingSettings getBatchingSettings() {
    return batchingSettings;
  }

  public FlowController getFlowController() {
    return flowController;
  }

  private BatchingCallSettings(
      ImmutableSet<StatusCode> retryableCodes,
      RetrySettings retrySettings,
      BatchingDescriptor<RequestT, ResponseT> batchingDescriptor,
      BatchingSettings batchingSettings,
      FlowController flowController) {
    super(retryableCodes, retrySettings);
    this.batchingDescriptor = batchingDescriptor;
    this.batchingSettings = batchingSettings;
    this.flowController = flowController;
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      BatchingDescriptor<RequestT, ResponseT> batchingDescriptor) {
    return new Builder<>(batchingDescriptor);
  }

  @Override
  public final Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<RequestT, ResponseT>
      extends UnaryCallSettingsTyped.Builder<RequestT, ResponseT> {

    private BatchingDescriptor<RequestT, ResponseT> batchingDescriptor;
    private BatchingSettings batchingSettings;
    private FlowController flowController;

    public Builder(BatchingDescriptor<RequestT, ResponseT> batchingDescriptor) {
      this.batchingDescriptor = batchingDescriptor;
    }

    public Builder(BatchingCallSettings<RequestT, ResponseT> settings) {
      super(settings);
      this.batchingDescriptor = settings.batchingDescriptor;
      this.batchingSettings = settings.batchingSettings;
      // TODO decide if a copy should be made
      this.flowController = settings.flowController;
    }

    public BatchingDescriptor<RequestT, ResponseT> getBatchingDescriptor() {
      return batchingDescriptor;
    }

    public Builder<RequestT, ResponseT> setBatchingSettings(BatchingSettings batchingSettings) {
      this.batchingSettings = batchingSettings;
      return this;
    }

    public BatchingSettings getBatchingSettings() {
      return batchingSettings;
    }

    public Builder<RequestT, ResponseT> setFlowController(FlowController flowController) {
      this.flowController = flowController;
      return this;
    }

    public FlowController getFlowController() {
      return flowController;
    }

    @Override
    public Builder<RequestT, ResponseT> setRetryableCodes(Set<StatusCode> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT> setRetryableCodes(StatusCode... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT> setRetrySettings(RetrySettings retrySettings) {
      super.setRetrySettings(retrySettings);
      return this;
    }

    @Override
    public BatchingCallSettings<RequestT, ResponseT> build() {
      Preconditions.checkNotNull(batchingSettings);

      FlowController flowControllerToUse = flowController;
      if (flowControllerToUse == null) {
        flowControllerToUse = new FlowController(batchingSettings.getFlowControlSettings());
      }

      return new BatchingCallSettings<>(
          ImmutableSet.copyOf(getRetryableCodes()),
          getRetrySettings(),
          batchingDescriptor,
          batchingSettings,
          flowControllerToUse);
    }
  }
}
