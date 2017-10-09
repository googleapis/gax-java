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
package com.google.api.gax.rpc;

import com.google.api.core.BetaApi;
import com.google.auth.Credentials;
import org.threeten.bp.Duration;

/**
 * A TransportDescriptor contains transport-specific logic necessary to build callables and issue
 * calls.
 */
@BetaApi
public abstract class TransportDescriptor {

  public void translateException(TranslateExceptionParameters build) {
    throw new UnsupportedOperationException(
        "TransportDescriptor.translateException not implemented");
  }

  public ApiCallContext createDefaultCallContext() {
    throw new UnsupportedOperationException(
        "TransportDescriptor.createDefaultCallContext not implemented");
  }

  public ApiCallContext getCallContextWithDefault(ApiCallContext inputContext) {
    throw new UnsupportedOperationException(
        "TransportDescriptor.getCallContextWithDefault not implemented");
  }

  public ApiCallContext getCallContextWithTimeout(ApiCallContext callContext, Duration rpcTimeout) {
    throw new UnsupportedOperationException(
        "TransportDescriptor.getCallContextWithTimeout not implemented");
  }

  public ApiCallContextEnhancer getAuthCallContextEnhancer(Credentials credentials) {
    throw new UnsupportedOperationException(
        "TransportDescriptor.getAuthCallContextEnhancer not implemented");
  }

  public ApiCallContextEnhancer getChannelCallContextEnhancer(TransportChannel channel) {
    throw new UnsupportedOperationException(
        "TransportDescriptor.getChannelCallContextEnhancer not implemented");
  }
}
