/*
 * Copyright 2017, Google Inc. All rights reserved.
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
import com.google.common.base.Preconditions;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.auth.MoreCallCredentials;

/**
 * {@code AuthClientCallFactory} creates new {@link io.grpc.ClientCall}s, defaulting to the provided
 * {@link Credentials}.
 */
class AuthClientCallFactory<RequestT, ResponseT> implements ClientCallFactory<RequestT, ResponseT> {
  private final ClientCallFactory<RequestT, ResponseT> inner;
  private final CallCredentials credentials;

  AuthClientCallFactory(ClientCallFactory<RequestT, ResponseT> inner, Credentials credentials) {
    this.inner = Preconditions.checkNotNull(inner);
    this.credentials = MoreCallCredentials.from(Preconditions.checkNotNull(credentials));
  }

  @Override
  public ClientCall<RequestT, ResponseT> newCall(Channel channel, CallOptions callOptions) {
    Preconditions.checkNotNull(channel);
    Preconditions.checkNotNull(callOptions);

    if (callOptions.getCredentials() == null) {
      callOptions = callOptions.withCallCredentials(credentials);
    }

    return inner.newCall(channel, callOptions);
  }
}
