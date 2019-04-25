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
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.common.base.Preconditions;
import java.util.Set;

@BetaApi("The surface for batching is not stable yet and may change in the future.")
@InternalExtensionOnly
public final class BatcherSettings<EntryT, EntryResultT, RequestT, ResponseT>
    extends UnaryCallSettings<RequestT, ResponseT> {
  private final BatchingDescriptor<EntryT, EntryResultT, RequestT, ResponseT> batchingDescriptor;
  private final BatchingSettings batchingSettings;
  private final FlowController flowController;

  private BatcherSettings(Builder<EntryT, EntryResultT, RequestT, ResponseT> builder) {
    super(builder);
    this.batchingDescriptor = builder.batchingDescriptor;
    this.batchingSettings = Preconditions.checkNotNull(builder.batchingSettings);
    FlowController flowControllerToUse = builder.flowController;
    if (flowControllerToUse == null) {
      flowControllerToUse = new FlowController(batchingSettings.getFlowControlSettings());
    }
    this.flowController = flowControllerToUse;
  }

  public BatchingDescriptor<EntryT, EntryResultT, RequestT, ResponseT> getBatchingDescriptor() {
    return batchingDescriptor;
  }

  public BatchingSettings getBatchingSettings() {
    return batchingSettings;
  }

  public FlowController getFlowController() {
    return flowController;
  }

  public static <EntryT, EntryResultT, RequestT, ResponseT>
      Builder<EntryT, EntryResultT, RequestT, ResponseT> newBuilder(
          BatchingDescriptor<EntryT, EntryResultT, RequestT, ResponseT> batchingDescriptor) {
    return new Builder<>(batchingDescriptor);
  }

  @Override
  public final Builder<EntryT, EntryResultT, RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<EntryT, EntryResultT, RequestT, ResponseT>
      extends UnaryCallSettings.Builder<RequestT, ResponseT> {

    private BatchingDescriptor<EntryT, EntryResultT, RequestT, ResponseT> batchingDescriptor;
    private BatchingSettings batchingSettings;
    private FlowController flowController;

    public Builder(
        BatchingDescriptor<EntryT, EntryResultT, RequestT, ResponseT> batchingDescriptor) {
      this.batchingDescriptor = batchingDescriptor;
    }

    public Builder(BatcherSettings<EntryT, EntryResultT, RequestT, ResponseT> settings) {
      this.batchingDescriptor = settings.batchingDescriptor;
      this.batchingSettings = settings.batchingSettings;
      this.flowController = settings.flowController;
    }

    public BatchingDescriptor<EntryT, EntryResultT, RequestT, ResponseT> getBatchingDescriptor() {
      return batchingDescriptor;
    }

    public Builder<EntryT, EntryResultT, RequestT, ResponseT> setBatchingSettings(
        BatchingSettings batchingSettings) {
      this.batchingSettings = batchingSettings;
      return this;
    }

    public BatchingSettings getBatchingSettings() {
      return batchingSettings;
    }

    public Builder<EntryT, EntryResultT, RequestT, ResponseT> setFlowController(
        FlowController flowController) {
      this.flowController = flowController;
      return this;
    }

    public FlowController getFlowController() {
      return flowController;
    }

    @Override
    public Builder<EntryT, EntryResultT, RequestT, ResponseT> setRetryableCodes(
        Set<StatusCode.Code> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<EntryT, EntryResultT, RequestT, ResponseT> setRetryableCodes(
        StatusCode.Code... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<EntryT, EntryResultT, RequestT, ResponseT> setRetrySettings(
        RetrySettings retrySettings) {
      super.setRetrySettings(retrySettings);
      return this;
    }

    @Override
    public BatcherSettings<EntryT, EntryResultT, RequestT, ResponseT> build() {
      return new BatcherSettings<>(this);
    }
  }
}
