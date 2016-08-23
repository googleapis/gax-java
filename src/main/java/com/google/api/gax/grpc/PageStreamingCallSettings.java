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

import com.google.api.gax.core.PagedListResponse;
import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class to configure an ApiCallable for calls to an API method that supports
 * page streaming.
 */
public final class PageStreamingCallSettings<RequestT, ResponseT, ResourceT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {
  private final PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;

  /**
   * Package-private, for use by ApiCallable.
   */
  ApiCallable<RequestT, ResponseT> create(
      ManagedChannel channel, ScheduledExecutorService executor) {
    return createBaseCallable(channel, executor);
  }

  /** Package-private, for use by ApiCallable. */
  ApiCallable<RequestT, PagedListResponse<RequestT, ResponseT, ResourceT>> createPagedVariant(
      ManagedChannel channel, ScheduledExecutorService executor) {
    ApiCallable<RequestT, ResponseT> baseCallable = createBaseCallable(channel, executor);
    return baseCallable.pageStreaming(pageDescriptor);
  }

  public static <RequestT, ResponseT, ResourceT> Builder<RequestT, ResponseT, ResourceT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
      PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    return new Builder<>(grpcMethodDescriptor, pageDescriptor);
  }

  @Override
  public final Builder<RequestT, ResponseT, ResourceT> toBuilder() {
    return new Builder<>(this);
  }

  private PageStreamingCallSettings(
      ImmutableSet<Status.Code> retryableCodes,
      RetrySettings retrySettings,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor,
      PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    super(retryableCodes, retrySettings, methodDescriptor);
    this.pageDescriptor = pageDescriptor;
  }

  public static class Builder<RequestT, ResponseT, ResourceT>
      extends ApiCallSettingsTyped.Builder<RequestT, ResponseT> {
    private PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;

    public Builder(
        MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
        PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
      super(grpcMethodDescriptor);
      this.pageDescriptor = pageDescriptor;
    }

    public Builder(PageStreamingCallSettings<RequestT, ResponseT, ResourceT> settings) {
      super(settings);
      this.pageDescriptor = settings.pageDescriptor;
    }

    public PageStreamingDescriptor<RequestT, ResponseT, ResourceT> getPageDescriptor() {
      return pageDescriptor;
    }

    @Override
    public Builder<RequestT, ResponseT, ResourceT> setRetryableCodes(
        Set<Status.Code> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT, ResourceT> setRetryableCodes(Status.Code... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT, ResourceT> setRetrySettingsBuilder(
        RetrySettings.Builder retrySettingsBuilder) {
      super.setRetrySettingsBuilder(retrySettingsBuilder);
      return this;
    }

    @Override
    public PageStreamingCallSettings<RequestT, ResponseT, ResourceT> build() {
      return new PageStreamingCallSettings<>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          getMethodDescriptor(),
          pageDescriptor);
    }
  }
}
