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
import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class to configure an ApiCallable for calls to a simple API method (i.e. that
 * doesn't support things like page streaming or bundling.)
 */
public final class SimpleCallSettings<RequestT, ResponseT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {

  /**
   * Package-private, for use by ApiCallable.
   */
  ApiCallable<RequestT, ResponseT> create(
      ManagedChannel channel, ScheduledExecutorService executor) {
    return createBaseCallable(channel, executor);
  }

  private SimpleCallSettings(
      ImmutableSet<Status.Code> retryableCodes,
      RetrySettings retrySettings,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    super(retryableCodes, retrySettings, methodDescriptor);
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
    return new Builder<>(grpcMethodDescriptor);
  }

  @Override
  public final Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<RequestT, ResponseT>
      extends ApiCallSettingsTyped.Builder<RequestT, ResponseT> {

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      super(grpcMethodDescriptor);
    }

    public Builder(SimpleCallSettings<RequestT, ResponseT> settings) {
      super(settings);
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
    public SimpleCallSettings<RequestT, ResponseT> build() {
      return new SimpleCallSettings<>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          getMethodDescriptor());
    }
  }
}
