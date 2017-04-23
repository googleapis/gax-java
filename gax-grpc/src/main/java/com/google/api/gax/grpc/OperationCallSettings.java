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

import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.protobuf.Message;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import java.util.concurrent.ScheduledExecutorService;
import org.joda.time.Duration;

/**
 * A settings class to configure an OperationCallable for calls to a long-running API method (i.e.
 * that returns the {@link Operation} type.)
 */
public final class OperationCallSettings<RequestT, ResponseT extends Message> {

  private final SimpleCallSettings<RequestT, Operation> initialCallSettings;
  private final Class<ResponseT> responseClass;
  private final Duration pollingInterval;

  public final SimpleCallSettings<RequestT, Operation> getInitialCallSettings() {
    return initialCallSettings;
  }

  public final Duration getPollingInterval() {
    return pollingInterval;
  }

  // package-private for internal use.
  OperationCallable<RequestT, ResponseT> createOperationCallable(
      Channel channel, ScheduledExecutorService executor, OperationsClient operationsClient) {
    UnaryCallable<RequestT, Operation> initialCallable =
        initialCallSettings.create(channel, executor);
    OperationCallable<RequestT, ResponseT> operationCallable =
        new OperationCallable<>(
            initialCallable, channel, executor, operationsClient, responseClass, this);
    return operationCallable;
  }

  private OperationCallSettings(
      SimpleCallSettings<RequestT, Operation> initialCallSettings,
      Class<ResponseT> responseClass,
      Duration pollingInterval) {
    this.initialCallSettings = initialCallSettings;
    this.responseClass = responseClass;
    this.pollingInterval = pollingInterval;
  }

  /** Create a new builder which can construct an instance of OperationCallSettings. */
  public static <RequestT, ResponseT extends Message> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, Operation> grpcMethodDescriptor, Class<ResponseT> responseClass) {
    return new Builder<>(grpcMethodDescriptor, responseClass);
  }

  public final Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<RequestT, ResponseT extends Message> {
    private SimpleCallSettings.Builder<RequestT, Operation> initialCallSettings;
    private Class<ResponseT> responseClass;
    private Duration pollingInterval = OperationFuture.DEFAULT_POLLING_INTERVAL;

    public Builder(
        MethodDescriptor<RequestT, Operation> grpcMethodDescriptor,
        Class<ResponseT> responseClass) {
      this.initialCallSettings = SimpleCallSettings.newBuilder(grpcMethodDescriptor);
      this.responseClass = responseClass;
    }

    public Builder(OperationCallSettings<RequestT, ResponseT> settings) {
      this.initialCallSettings = settings.initialCallSettings.toBuilder();
      this.responseClass = settings.responseClass;
    }

    /** Set the polling interval of the operation. */
    public Builder setPollingInterval(Duration pollingInterval) {
      this.pollingInterval = pollingInterval;
      return this;
    }

    /** Get the polling interval of the operation. */
    public Duration getPollingInterval() {
      return pollingInterval;
    }

    /** Set the call settings which are used on the call to initiate the operation. */
    public Builder setInitialCallSettings(
        SimpleCallSettings.Builder<RequestT, Operation> initialCallSettings) {
      this.initialCallSettings = initialCallSettings;
      return this;
    }

    /** Get the call settings which are used on the call to initiate the operation. */
    public SimpleCallSettings.Builder<RequestT, Operation> getInitialCallSettings() {
      return initialCallSettings;
    }

    public OperationCallSettings<RequestT, ResponseT> build() {
      return new OperationCallSettings<>(
          initialCallSettings.build(), responseClass, pollingInterval);
    }
  }
}
