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
package com.google.api.gax.rpc.testing;

import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.auth.Credentials;
import org.threeten.bp.Duration;

@InternalApi("for testing")
public class FakeApiCallContext implements ApiCallContext {
  private final Credentials credentials;
  private final FakeChannel channel;

  private FakeApiCallContext(Credentials credentials, FakeChannel channel) {
    this.credentials = credentials;
    this.channel = channel;
  }

  public static FakeApiCallContext of() {
    return new FakeApiCallContext(null, null);
  }

  public static FakeApiCallContext getAsFakeApiCallContextWithDefault(ApiCallContext inputContext) {
    FakeApiCallContext fakeCallContext;
    if (inputContext == null) {
      fakeCallContext = FakeApiCallContext.of();
    } else {
      if (!(inputContext instanceof FakeApiCallContext)) {
        throw new IllegalArgumentException(
            "context must be an instance of FakeApiCallContext, but found "
                + inputContext.getClass().getName());
      }
      fakeCallContext = (FakeApiCallContext) inputContext;
    }
    return fakeCallContext;
  }

  @Override
  public ApiCallContext withTimeout(Duration rpcTimeout) {
    return this;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public FakeChannel getChannel() {
    return channel;
  }

  public FakeApiCallContext withCredentials(Credentials credentials) {
    return new FakeApiCallContext(credentials, this.channel);
  }

  public FakeApiCallContext withChannel(FakeChannel channel) {
    return new FakeApiCallContext(this.credentials, channel);
  }
}
