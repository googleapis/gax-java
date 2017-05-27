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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.BetaApi;
import com.google.api.gax.retrying.TimedRetryAlgorithm;
import com.google.longrunning.Operation;
import com.google.protobuf.Message;

/**
 * A settings class to configure an {@link OperationCallable} for calls to a long-running API method
 * (i.e. that returns the {@link Operation} type.)
 */
@BetaApi
public final class OperationCallSettings<RequestT, ResponseT extends Message> {
  private final SimpleCallSettings<RequestT, Operation> initialCallSettings;
  private final TimedRetryAlgorithm pollingAlgorithm;
  private final Class<ResponseT> responseClass;

  public final SimpleCallSettings<RequestT, Operation> getInitialCallSettings() {
    return initialCallSettings;
  }

  public final TimedRetryAlgorithm getPollingAlgorithm() {
    return pollingAlgorithm;
  }

  public Class<ResponseT> getResponseClass() {
    return responseClass;
  }

  private OperationCallSettings(
      SimpleCallSettings<RequestT, Operation> initialCallSettings,
      TimedRetryAlgorithm pollingAlgorithm,
      Class<ResponseT> responseClass) {
    this.initialCallSettings = checkNotNull(initialCallSettings);
    this.pollingAlgorithm = checkNotNull(pollingAlgorithm);
    this.responseClass = checkNotNull(responseClass);
  }

  /** Create a new builder which can construct an instance of OperationCallSettings. */
  public static <RequestT, ResponseT extends Message> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<>();
  }

  public final Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<RequestT, ResponseT extends Message> {
    private SimpleCallSettings<RequestT, Operation> initialCallSettings;
    private Class<ResponseT> responseClass;
    private TimedRetryAlgorithm pollingAlgorithm;

    public Builder() {}

    public Builder(OperationCallSettings<RequestT, ResponseT> settings) {
      this.initialCallSettings = settings.initialCallSettings.toBuilder().build();
      this.responseClass = settings.responseClass;
      this.pollingAlgorithm = settings.pollingAlgorithm;
    }

    /** Set the polling algorithm of the operation. */
    public Builder setPollingAlgorithm(TimedRetryAlgorithm pollingAlgorithm) {
      this.pollingAlgorithm = pollingAlgorithm;
      return this;
    }

    /** Get the polling algorithm of the operation. */
    public TimedRetryAlgorithm getPollingAlgorithm() {
      return pollingAlgorithm;
    }

    /** Set the call settings which are used on the call to initiate the operation. */
    public Builder<RequestT, ResponseT> setInitialCallSettings(
        SimpleCallSettings<RequestT, Operation> initialCallSettings) {
      this.initialCallSettings = initialCallSettings;
      return this;
    }

    /** Get the call settings which are used on the call to initiate the operation. */
    public SimpleCallSettings<RequestT, Operation> getInitialCallSettings() {
      return initialCallSettings;
    }

    public Class<ResponseT> getResponseClass() {
      return responseClass;
    }

    public Builder<RequestT, ResponseT> setResponseClass(Class<ResponseT> responseClass) {
      this.responseClass = responseClass;
      return this;
    }

    public OperationCallSettings<RequestT, ResponseT> build() {
      return new OperationCallSettings<>(initialCallSettings, pollingAlgorithm, responseClass);
    }
  }
}
