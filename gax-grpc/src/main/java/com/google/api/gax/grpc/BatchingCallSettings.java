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

import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.retrying.RetrySettings;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class to configure a UnaryCallable for calls to an API method that supports batching.
 * The settings are provided using an instance of {@link BatchingSettings}.
 */
public final class BatchingCallSettings<RequestT, ResponseT>
    extends UnaryCallSettingsTyped<RequestT, ResponseT> {
  private final BatchingDescriptor<RequestT, ResponseT> batchingDescriptor;
  private final BatchingSettings batchingSettings;
  private BatcherFactory<RequestT, ResponseT> batcherFactory;

  /** Package-private, for use by UnaryCallable. */
  UnaryCallable<RequestT, ResponseT> create(Channel channel, ScheduledExecutorService executor) {
    UnaryCallable<RequestT, ResponseT> baseCallable = createBaseCallable(channel, executor);
    batcherFactory = new BatcherFactory<>(batchingDescriptor, batchingSettings, executor);
    return baseCallable.batching(batchingDescriptor, batcherFactory);
  }

  public BatcherFactory<RequestT, ResponseT> getBatcherFactory() {
    return batcherFactory;
  }

  private BatchingCallSettings(
      ImmutableSet<Status.Code> retryableCodes,
      RetrySettings retrySettings,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor,
      BatchingDescriptor<RequestT, ResponseT> batchingDescriptor,
      BatchingSettings batchingSettings) {
    super(retryableCodes, retrySettings, methodDescriptor);
    this.batchingDescriptor = batchingDescriptor;
    this.batchingSettings = batchingSettings;
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
      BatchingDescriptor<RequestT, ResponseT> batchingDescriptor) {
    return new Builder<>(grpcMethodDescriptor, batchingDescriptor);
  }

  @Override
  public final Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<RequestT, ResponseT>
      extends UnaryCallSettingsTyped.Builder<RequestT, ResponseT> {

    private BatchingDescriptor<RequestT, ResponseT> batchingDescriptor;
    private BatchingSettings.Builder batchingSettingsBuilder;

    public Builder(
        MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
        BatchingDescriptor<RequestT, ResponseT> batchingDescriptor) {
      super(grpcMethodDescriptor);
      this.batchingDescriptor = batchingDescriptor;
      this.batchingSettingsBuilder = BatchingSettings.newBuilder();
    }

    public Builder(BatchingCallSettings<RequestT, ResponseT> settings) {
      super(settings);
      this.batchingDescriptor = settings.batchingDescriptor;
      this.batchingSettingsBuilder = settings.batchingSettings.toBuilder();
    }

    public BatchingDescriptor<RequestT, ResponseT> getBatchingDescriptor() {
      return batchingDescriptor;
    }

    public Builder<RequestT, ResponseT> setBatchingSettingsBuilder(
        BatchingSettings.Builder batchingSettingsBuilder) {
      this.batchingSettingsBuilder = Preconditions.checkNotNull(batchingSettingsBuilder);
      return this;
    }

    public BatchingSettings.Builder getBatchingSettingsBuilder() {
      return this.batchingSettingsBuilder;
    }

    @Override
    public Builder<RequestT, ResponseT> setRetryableCodes(Set<Status.Code> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT> setRetryableCodes(Status.Code... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT> setRetrySettingsBuilder(
        RetrySettings.Builder retrySettingsBuilder) {
      super.setRetrySettingsBuilder(retrySettingsBuilder);
      return this;
    }

    @Override
    public BatchingCallSettings<RequestT, ResponseT> build() {
      return new BatchingCallSettings<>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          getMethodDescriptor(),
          batchingDescriptor,
          batchingSettingsBuilder.build());
    }
  }
}
