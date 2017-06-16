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
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * A settings class to configure a {@link UnaryCallable} for calls to an API method that supports
 * page streaming.
 */
@BetaApi
public final class PagedCallSettings<RequestT, ResponseT, PagedListResponseT>
    extends UnaryCallSettingsTyped<RequestT, ResponseT> {
  private final PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
      pagedListResponseFactory;

  public PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
      getPagedListResponseFactory() {
    return pagedListResponseFactory;
  }

  public static <RequestT, ResponseT, PagedListResponseT>
      Builder<RequestT, ResponseT, PagedListResponseT> newBuilder(
          PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
              pagedListResponseFactory) {
    return new Builder<>(pagedListResponseFactory);
  }

  @Override
  public final Builder<RequestT, ResponseT, PagedListResponseT> toBuilder() {
    return new Builder<>(this);
  }

  private PagedCallSettings(
      ImmutableSet<FailureCode> retryableCodes,
      RetrySettings retrySettings,
      PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT> pagedListResponseFactory) {
    super(retryableCodes, retrySettings);
    this.pagedListResponseFactory = pagedListResponseFactory;
  }

  public static class Builder<RequestT, ResponseT, PagedListResponseT>
      extends UnaryCallSettingsTyped.Builder<RequestT, ResponseT> {
    private PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
        pagedListResponseFactory;

    public Builder(
        PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT>
            pagedListResponseFactory) {
      this.pagedListResponseFactory = pagedListResponseFactory;
    }

    public Builder(PagedCallSettings<RequestT, ResponseT, PagedListResponseT> settings) {
      super(settings);
      this.pagedListResponseFactory = settings.pagedListResponseFactory;
    }

    @Override
    public Builder<RequestT, ResponseT, PagedListResponseT> setRetryableCodes(
        Set<FailureCode> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT, PagedListResponseT> setRetryableCodes(
        FailureCode... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT, PagedListResponseT> setRetrySettings(
        RetrySettings retrySettings) {
      super.setRetrySettings(retrySettings);
      return this;
    }

    @Override
    public PagedCallSettings<RequestT, ResponseT, PagedListResponseT> build() {
      return new PagedCallSettings<>(
          ImmutableSet.<FailureCode>copyOf(getRetryableCodes()),
          getRetrySettings(),
          pagedListResponseFactory);
    }
  }
}
