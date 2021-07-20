/*
 * Copyright 2021 Google LLC
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

import com.google.api.core.BetaApi;
import com.google.api.gax.retrying.ClientStreamResumptionStrategy;
import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;

/** A settings class to configure a {@link ClientStreamingCallable}. */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public final class ClientStreamingCallSettings<RequestT, ResponseT>
    extends StreamingCallSettings<RequestT, ResponseT> {

  @Nullable final ClientStreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;

  private ClientStreamingCallSettings(Builder<RequestT, ResponseT> builder) {
    this.resumptionStrategy = builder.resumptionStrategy;
  }

  /**
   * See the class documentation of {@link ClientStreamingCallSettings} and {@link
   * ClientStreamResumptionStrategy} for a description of what the ClientStreamResumptionStrategy
   * does.
   */
  public ClientStreamResumptionStrategy<RequestT, ResponseT> getResumptionStrategy() {
    return resumptionStrategy;
  }

  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<>();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("resumptionStrategy", resumptionStrategy)
        .toString();
  }

  public static class Builder<RequestT, ResponseT>
      extends StreamingCallSettings.Builder<RequestT, ResponseT> {
    private ClientStreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;

    /** Initialize the builder with default settings */
    private Builder() {}

    private Builder(ClientStreamingCallSettings<RequestT, ResponseT> settings) {
      super(settings);
      this.resumptionStrategy = settings.resumptionStrategy;
    }

    /**
     * See the class documentation of {@link ClientStreamingCallSettings} for a description of what
     * ClientStreamResumptionStrategy does.
     */
    public Builder<RequestT, ResponseT> setResumptionStrategy(
        ClientStreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy) {
      this.resumptionStrategy = resumptionStrategy;
      return this;
    }

    public ClientStreamResumptionStrategy<RequestT, ResponseT> getResumptionStrategy() {
      return resumptionStrategy;
    }

    @Override
    public ClientStreamingCallSettings<RequestT, ResponseT> build() {
      return new ClientStreamingCallSettings<>(this);
    }
  }
}
