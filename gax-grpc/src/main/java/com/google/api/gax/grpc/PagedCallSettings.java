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

import com.google.api.core.BetaApi;
import com.google.api.gax.retrying.RetrySettings;
import com.google.common.collect.ImmutableSet;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class to configure a UnaryCallable for calls to an API method that supports page
 * streaming.
 */
@BetaApi
public final class PagedCallSettings<RequestT, ResponseT, PagedListResponseT>
    extends UnaryCallSettingsTyped<RequestT, ResponseT> {
  private final PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
      pagedListResponseFactory;

  /** Package-private, for use by UnaryCallable. */
  UnaryCallable<RequestT, ResponseT> create(Channel channel, ScheduledExecutorService executor) {
    return createBaseCallable(channel, executor);
  }

  /** Package-private, for use by UnaryCallable. */
  UnaryCallable<RequestT, PagedListResponseT> createPagedVariant(
      Channel channel, ScheduledExecutorService executor) {
    UnaryCallable<RequestT, ResponseT> baseCallable = createBaseCallable(channel, executor);
    return baseCallable.paged(pagedListResponseFactory);
  }

  public static <RequestT, ResponseT, PagedListResponseT>
      Builder<RequestT, ResponseT, PagedListResponseT> newBuilder(
          MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
          PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
              pagedListResponseFactory) {
    return new Builder<>(grpcMethodDescriptor, pagedListResponseFactory);
  }

  @Override
  public final Builder<RequestT, ResponseT, PagedListResponseT> toBuilder() {
    return new Builder<>(this);
  }

  private PagedCallSettings(
      ImmutableSet<Status.Code> retryableCodes,
      RetrySettings retrySettings,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor,
      PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT> pagedListResponseFactory) {
    super(retryableCodes, retrySettings, methodDescriptor);
    this.pagedListResponseFactory = pagedListResponseFactory;
  }

  public static class Builder<RequestT, ResponseT, PagedListResponseT>
      extends UnaryCallSettingsTyped.Builder<RequestT, ResponseT> {
    private PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
        pagedListResponseFactory;

    public Builder(
        MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
        PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
            pagedListResponseFactory) {
      super(grpcMethodDescriptor);
      this.pagedListResponseFactory = pagedListResponseFactory;
    }

    public Builder(PagedCallSettings<RequestT, ResponseT, PagedListResponseT> settings) {
      super(settings);
      this.pagedListResponseFactory = settings.pagedListResponseFactory;
    }

    @Override
    public Builder<RequestT, ResponseT, PagedListResponseT> setRetryableCodes(
        Set<Status.Code> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT, PagedListResponseT> setRetryableCodes(
        Status.Code... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT, PagedListResponseT> setRetrySettingsBuilder(
        RetrySettings.Builder retrySettingsBuilder) {
      super.setRetrySettingsBuilder(retrySettingsBuilder);
      return this;
    }

    @Override
    public PagedCallSettings<RequestT, ResponseT, PagedListResponseT> build() {
      return new PagedCallSettings<>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          getMethodDescriptor(),
          pagedListResponseFactory);
    }
  }
}
