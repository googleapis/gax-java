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

import com.google.api.core.BetaApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiCallContextEnhancer;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.TranslateExceptionParameters;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportDescriptor;
import com.google.auth.Credentials;
import io.grpc.CallOptions;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/** Implementation of TransportDescriptor for gRPC. */
@BetaApi
public class GrpcTransportDescriptor extends TransportDescriptor {
  private GrpcTransportDescriptor() {}

  public static GrpcTransportDescriptor create() {
    return new GrpcTransportDescriptor();
  }

  @Override
  public void translateException(TranslateExceptionParameters translateExceptionParameters) {
    Status.Code statusCode;
    Throwable throwable = translateExceptionParameters.getThrowable();
    boolean canRetry;
    ApiException exceptionToThrow;
    if (throwable instanceof StatusException) {
      StatusException e = (StatusException) throwable;
      statusCode = e.getStatus().getCode();
      canRetry =
          translateExceptionParameters.getRetryableCodes().contains(GrpcStatusCode.of(statusCode));
      exceptionToThrow = GrpcApiExceptionFactory.createException(throwable, statusCode, canRetry);
    } else if (throwable instanceof StatusRuntimeException) {
      StatusRuntimeException e = (StatusRuntimeException) throwable;
      statusCode = e.getStatus().getCode();
      canRetry =
          translateExceptionParameters.getRetryableCodes().contains(GrpcStatusCode.of(statusCode));
      exceptionToThrow = GrpcApiExceptionFactory.createException(throwable, statusCode, canRetry);
    } else if (throwable instanceof CancellationException
        && translateExceptionParameters.isCancelled()) {
      // this just circled around, so ignore.
      return;
    } else if (throwable instanceof ApiException) {
      exceptionToThrow = (ApiException) throwable;
    } else {
      // Do not retry on unknown throwable, even when UNKNOWN is in retryableCodes
      statusCode = Status.Code.UNKNOWN;
      canRetry = false;
      exceptionToThrow = GrpcApiExceptionFactory.createException(throwable, statusCode, canRetry);
    }
    translateExceptionParameters.getResultFuture().setException(exceptionToThrow);
  }

  public ApiCallContext createDefaultCallContext() {
    return GrpcCallContext.createDefault();
  }

  public ApiCallContext getCallContextWithDefault(ApiCallContext inputContext) {
    return GrpcCallContext.getAsGrpcCallContextWithDefault(inputContext);
  }

  public ApiCallContext getCallContextWithTimeout(ApiCallContext callContext, Duration rpcTimeout) {
    GrpcCallContext oldContext = GrpcCallContext.getAsGrpcCallContextWithDefault(callContext);
    CallOptions oldOptions = oldContext.getCallOptions();
    CallOptions newOptions =
        oldOptions.withDeadlineAfter(rpcTimeout.toMillis(), TimeUnit.MILLISECONDS);
    GrpcCallContext nextContext = oldContext.withCallOptions(newOptions);

    if (oldOptions.getDeadline() == null) {
      return nextContext;
    }
    if (oldOptions.getDeadline().isBefore(newOptions.getDeadline())) {
      return oldContext;
    }
    return nextContext;
  }

  public ApiCallContextEnhancer getAuthCallContextEnhancer(Credentials credentials) {
    return new GrpcAuthCallContextEnhancer(credentials);
  }

  public ApiCallContextEnhancer getChannelCallContextEnhancer(TransportChannel channel) {
    return new GrpcChannelCallContextEnhancer(channel);
  }
}
