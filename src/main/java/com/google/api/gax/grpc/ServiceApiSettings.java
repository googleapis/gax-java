/*
 * Copyright 2015, Google Inc.
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

import com.google.auth.Credentials;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.Status;

import javax.annotation.Nullable;

// TODO(pongad): Don't close the channel if the user gives one to us
/**
 * A settings class to configure a service api class.
 *
 * A note on channels: whichever service API class that this instance of ServiceApiSettings
 * is passed to will call shutdown() on the channel provided by {@link getChannel}.
 * Setting a channel is intended for use by unit tests to override the channel,
 * and should not be used in production.
 */
@AutoValue
public abstract class ServiceApiSettings<MethodId> {
  /**
   * Status codes that are considered to be retryable by the given methods
   */
  public abstract ImmutableMap<MethodId, ImmutableSet<Status.Code>> getRetryableCodes();

  /**
   * Credentials to use in order to call the service.
   * The default is to acquire credentials using GoogleCredentials.getApplicationDefault().
   * These credentials are not used if the channel is set.
   */
  @Nullable
  public abstract Credentials getCredentials();

  /**
   * The path used to reach the service. This value will not be used if the channel is set.
   */
  @Nullable
  public abstract String getServiceAddress();

  /**
   * The port used to reach the service. This value will not be used if the channel is set.
   */
  public abstract int getPort();

  /**
   * The channel used to send requests to the service.
   * See class documentation on channels.
   */
  @Nullable
  public abstract ManagedChannel getChannel();

  public static <MethodId> Builder<MethodId> builder() {
    return new AutoValue_ServiceApiSettings.Builder()
        .setRetryableCodes(ImmutableMap.<MethodId, ImmutableSet<Status.Code>>of())
        .setPort(0);
  }

  public Builder<MethodId> toBuilder() {
    return new AutoValue_ServiceApiSettings.Builder(this);
  }

  @AutoValue.Builder
  public abstract static class Builder<MethodId> {
    public abstract Builder<MethodId> setRetryableCodes(
        ImmutableMap<MethodId, ImmutableSet<Status.Code>> codes);

    public abstract Builder<MethodId> setCredentials(Credentials credentials);

    public abstract Builder<MethodId> setServiceAddress(String serviceAddress);

    public abstract Builder<MethodId> setPort(int port);

    public abstract Builder<MethodId> setChannel(ManagedChannel channel);

    public abstract ServiceApiSettings<MethodId> build();
  }
}
