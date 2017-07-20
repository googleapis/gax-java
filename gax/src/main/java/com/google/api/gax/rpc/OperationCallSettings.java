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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.BetaApi;
import com.google.api.gax.retrying.TimedRetryAlgorithm;
import javax.annotation.Nullable;

/**
 * A settings class to configure an {@link OperationCallable} for calls to initiate, resume, and
 * cancel a long-running operation.
 */
@BetaApi
public final class OperationCallSettings<RequestT, ResponseT, MetadataT, OperationT> {
  private final SimpleCallSettings<RequestT, OperationT> initialCallSettings;
  private final TimedRetryAlgorithm pollingAlgorithm;
  private final Class<ResponseT> responseClass;
  private final Class<MetadataT> metadataClass;

  public final SimpleCallSettings<RequestT, OperationT> getInitialCallSettings() {
    return initialCallSettings;
  }

  public final TimedRetryAlgorithm getPollingAlgorithm() {
    return pollingAlgorithm;
  }

  public Class<ResponseT> getResponseClass() {
    return responseClass;
  }

  @Nullable
  public Class<MetadataT> getMetadataClass() {
    return metadataClass;
  }

  private OperationCallSettings(
      SimpleCallSettings<RequestT, OperationT> initialCallSettings,
      TimedRetryAlgorithm pollingAlgorithm,
      Class<ResponseT> responseClass,
      Class<MetadataT> metadataClass) {
    this.initialCallSettings = checkNotNull(initialCallSettings);
    this.pollingAlgorithm = checkNotNull(pollingAlgorithm);
    this.responseClass = checkNotNull(responseClass);
    this.metadataClass = metadataClass;
  }

  /** Create a new builder which can construct an instance of OperationCallSettings. */
  public static <RequestT, ResponseT, MetadataT, OperationT>
      Builder<RequestT, ResponseT, MetadataT, OperationT> newBuilder() {
    return new Builder<>();
  }

  public final Builder<RequestT, ResponseT, MetadataT, OperationT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<RequestT, ResponseT, MetadataT, OperationT> {
    private SimpleCallSettings<RequestT, OperationT> initialCallSettings;
    private TimedRetryAlgorithm pollingAlgorithm;
    private Class<ResponseT> responseClass;
    private Class<MetadataT> metadataClass;

    public Builder() {}

    public Builder(OperationCallSettings<RequestT, ResponseT, MetadataT, OperationT> settings) {
      this.initialCallSettings = settings.initialCallSettings.toBuilder().build();
      this.pollingAlgorithm = settings.pollingAlgorithm;
      this.responseClass = settings.responseClass;
      this.metadataClass = settings.metadataClass;
    }

    /** Set the polling algorithm of the operation. */
    public Builder<RequestT, ResponseT, MetadataT, OperationT> setPollingAlgorithm(
        TimedRetryAlgorithm pollingAlgorithm) {
      this.pollingAlgorithm = pollingAlgorithm;
      return this;
    }

    /** Get the polling algorithm of the operation. */
    public TimedRetryAlgorithm getPollingAlgorithm() {
      return pollingAlgorithm;
    }

    /** Set the call settings which are used on the call to initiate the operation. */
    public Builder<RequestT, ResponseT, MetadataT, OperationT> setInitialCallSettings(
        SimpleCallSettings<RequestT, OperationT> initialCallSettings) {
      this.initialCallSettings = initialCallSettings;
      return this;
    }

    /** Get the call settings which are used on the call to initiate the operation. */
    public SimpleCallSettings<RequestT, OperationT> getInitialCallSettings() {
      return initialCallSettings;
    }

    public Class<ResponseT> getResponseClass() {
      return responseClass;
    }

    public Builder<RequestT, ResponseT, MetadataT, OperationT> setResponseClass(
        Class<ResponseT> responseClass) {
      this.responseClass = responseClass;
      return this;
    }

    public Class<MetadataT> getMetadataClass() {
      return metadataClass;
    }

    public Builder<RequestT, ResponseT, MetadataT, OperationT> setMetadataClass(
        Class<MetadataT> metadataClass) {
      this.metadataClass = metadataClass;
      return this;
    }

    public OperationCallSettings<RequestT, ResponseT, MetadataT, OperationT> build() {
      return new OperationCallSettings<>(
          initialCallSettings, pollingAlgorithm, responseClass, metadataClass);
    }
  }
}
