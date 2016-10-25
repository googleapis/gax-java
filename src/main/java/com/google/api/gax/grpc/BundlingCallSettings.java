/*
 * Copyright 2016, Google Inc.
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

import com.google.api.gax.core.RetrySettings;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class to configure a UnaryCallable for calls to an API method that supports
 * bundling. The settings are provided using an instance of {@link BundlingSettings}.
 */
public final class BundlingCallSettings<RequestT, ResponseT>
    extends UnaryCallSettingsTyped<RequestT, ResponseT> {
  private final BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
  private final BundlingSettings bundlingSettings;
  private BundlerFactory<RequestT, ResponseT> bundlerFactory;

  /**
   * Package-private, for use by UnaryCallable.
   */
  UnaryCallable<RequestT, ResponseT> create(
      ManagedChannel channel, ScheduledExecutorService executor) {
    UnaryCallable<RequestT, ResponseT> baseCallable = createBaseCallable(channel, executor);
    bundlerFactory = new BundlerFactory<>(bundlingDescriptor, bundlingSettings);
    return baseCallable.bundling(bundlingDescriptor, bundlerFactory);
  }

  public BundlerFactory<RequestT, ResponseT> getBundlerFactory() {
    return bundlerFactory;
  }

  private BundlingCallSettings(
      ImmutableSet<Status.Code> retryableCodes,
      RetrySettings retrySettings,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor,
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlingSettings bundlingSettings) {
    super(retryableCodes, retrySettings, methodDescriptor);
    this.bundlingDescriptor = bundlingDescriptor;
    this.bundlingSettings = bundlingSettings;
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor) {
    return new Builder<>(grpcMethodDescriptor, bundlingDescriptor);
  }

  @Override
  public final Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<RequestT, ResponseT>
      extends UnaryCallSettingsTyped.Builder<RequestT, ResponseT> {

    private BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
    private BundlingSettings.Builder bundlingSettingsBuilder;

    public Builder(
        MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
        BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor) {
      super(grpcMethodDescriptor);
      this.bundlingDescriptor = bundlingDescriptor;
      this.bundlingSettingsBuilder = BundlingSettings.newBuilder();
    }

    public Builder(BundlingCallSettings<RequestT, ResponseT> settings) {
      super(settings);
      this.bundlingDescriptor = settings.bundlingDescriptor;
      this.bundlingSettingsBuilder = settings.bundlingSettings.toBuilder();
    }

    public BundlingDescriptor<RequestT, ResponseT> getBundlingDescriptor() {
      return bundlingDescriptor;
    }

    public Builder<RequestT, ResponseT> setBundlingSettingsBuilder(
        BundlingSettings.Builder bundlingSettingsBuilder) {
      this.bundlingSettingsBuilder = Preconditions.checkNotNull(bundlingSettingsBuilder);
      return this;
    }

    public BundlingSettings.Builder getBundlingSettingsBuilder() {
      return this.bundlingSettingsBuilder;
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
    public BundlingCallSettings<RequestT, ResponseT> build() {
      return new BundlingCallSettings<>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          getMethodDescriptor(),
          bundlingDescriptor,
          bundlingSettingsBuilder.build());
    }
  }
}
