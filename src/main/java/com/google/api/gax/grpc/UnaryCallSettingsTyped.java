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

import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class with generic typing configure a UnaryCallable.
 *
 * <p>This class can be used as the base class that other concrete call settings classes inherit
 * from. We need this intermediate class to add generic typing, because UnaryCallSettings is
 * not parameterized for its request and response types.
 *
 * <p>This class is package-private; use the concrete settings classes instead of this class from
 * outside of the package.
 */
abstract class UnaryCallSettingsTyped<RequestT, ResponseT> extends UnaryCallSettings {

  private final MethodDescriptor<RequestT, ResponseT> methodDescriptor;

  public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
    return methodDescriptor;
  }

  @Override
  public abstract Builder<RequestT, ResponseT> toBuilder();

  protected UnaryCallSettingsTyped(
      ImmutableSet<Status.Code> retryableCodes,
      RetrySettings retrySettings,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    super(retryableCodes, retrySettings);
    this.methodDescriptor = methodDescriptor;
  }

  protected UnaryCallable<RequestT, ResponseT> createBaseCallable(
      ManagedChannel channel, ScheduledExecutorService executor) {
    ClientCallFactory<RequestT, ResponseT> clientCallFactory =
        new DescriptorClientCallFactory<>(methodDescriptor);
    UnaryCallable<RequestT, ResponseT> callable =
        new UnaryCallable<>(new DirectCallable<>(clientCallFactory), channel, this);
    if (getRetryableCodes() != null) {
      callable = callable.retryableOn(ImmutableSet.copyOf(getRetryableCodes()));
    }
    if (getRetrySettings() != null) {
      callable = callable.retrying(getRetrySettings(), executor);
    }
    return callable;
  }

  public abstract static class Builder<RequestT, ResponseT> extends UnaryCallSettings.Builder {
    private MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor;

    protected Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      this.grpcMethodDescriptor = grpcMethodDescriptor;
    }

    protected Builder(UnaryCallSettingsTyped<RequestT, ResponseT> settings) {
      super(settings);
      this.grpcMethodDescriptor = settings.getMethodDescriptor();
    }

    public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
      return grpcMethodDescriptor;
    }

    @Override
    public abstract UnaryCallSettingsTyped<RequestT, ResponseT> build();
  }
}
