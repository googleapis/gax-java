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
import com.google.api.gax.rpc.ApiCallContextEnhancer;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.TranslateExceptionParameters;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportDescriptor;
import com.google.auth.Credentials;
import com.google.common.base.Preconditions;
import java.util.concurrent.CancellationException;
import org.threeten.bp.Duration;

@InternalApi("for testing")
public class FakeTransportDescriptor extends TransportDescriptor {

  public static FakeTransportDescriptor create() {
    return new FakeTransportDescriptor();
  }

  private FakeTransportDescriptor() {}

  @Override
  public void translateException(TranslateExceptionParameters translateExceptionParameters) {
    Throwable throwable = translateExceptionParameters.getThrowable();
    if (throwable instanceof FakeStatusException) {
      FakeStatusException e = (FakeStatusException) throwable;
      boolean canRetry =
          translateExceptionParameters.getRetryableCodes().contains(e.getStatusCode().getCode());
      translateExceptionParameters
          .getResultFuture()
          .setException(
              ApiExceptionFactory.createException(throwable, e.getStatusCode(), canRetry));
    } else if (throwable instanceof CancellationException
        && translateExceptionParameters.isCancelled()) {
      // this just circled around, so ignore.
    } else if (throwable instanceof ApiException) {
      translateExceptionParameters.getResultFuture().setException(throwable);

    } else {
      // Do not retry on unknown throwable, even when UNKNOWN is in retryableCodes
      boolean canRetry = false;
      translateExceptionParameters
          .getResultFuture()
          .setException(
              ApiExceptionFactory.createException(
                  throwable, FakeStatusCode.of(FakeStatusCode.Code.UNKNOWN), canRetry));
    }
  }

  @Override
  public ApiCallContext createDefaultCallContext() {
    return FakeApiCallContext.of();
  }

  @Override
  public ApiCallContext getCallContextWithDefault(ApiCallContext inputContext) {
    if (inputContext == null) {
      return createDefaultCallContext();
    } else {
      return inputContext;
    }
  }

  @Override
  public ApiCallContext getCallContextWithTimeout(ApiCallContext callContext, Duration rpcTimeout) {
    return callContext;
  }

  @Override
  public ApiCallContextEnhancer getAuthCallContextEnhancer(final Credentials credentials) {
    return new ApiCallContextEnhancer() {
      @Override
      public ApiCallContext enhance(ApiCallContext inputContext) {
        FakeApiCallContext fakeApiCallContext =
            FakeApiCallContext.getAsFakeApiCallContextWithDefault(inputContext);
        if (fakeApiCallContext.getCredentials() == null) {
          return fakeApiCallContext.withCredentials(credentials);
        } else {
          return fakeApiCallContext;
        }
      }
    };
  }

  @Override
  public ApiCallContextEnhancer getChannelCallContextEnhancer(TransportChannel inputChannel) {
    Preconditions.checkNotNull(inputChannel);
    if (!(inputChannel instanceof FakeTransportChannel)) {
      throw new IllegalArgumentException(
          "Expected FakeTransportChannel, got " + inputChannel.getClass().getName());
    }
    FakeTransportChannel transportChannel = (FakeTransportChannel) inputChannel;
    final FakeChannel channel = transportChannel.getChannel();
    return new ApiCallContextEnhancer() {
      @Override
      public ApiCallContext enhance(ApiCallContext inputContext) {
        FakeApiCallContext fakeApiCallContext =
            FakeApiCallContext.getAsFakeApiCallContextWithDefault(inputContext);
        if (fakeApiCallContext.getChannel() == null) {
          return fakeApiCallContext.withChannel(channel);
        } else {
          return fakeApiCallContext;
        }
      }
    };
  }
}
