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
package com.google.api.gax.httpjson;

import com.google.api.client.http.HttpResponseException;
import com.google.api.core.BetaApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiCallContextEnhancer;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.TranslateExceptionParameters;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportDescriptor;
import com.google.auth.Credentials;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/** Implementation of TransportDescriptor for http/json. */
@BetaApi
public class HttpJsonTransportDescriptor extends TransportDescriptor {
  private HttpJsonTransportDescriptor() {}

  public static HttpJsonTransportDescriptor create() {
    return new HttpJsonTransportDescriptor();
  }

  @Override
  public void translateException(TranslateExceptionParameters translateExceptionParameters) {
    StatusCode statusCode;
    Throwable throwable = translateExceptionParameters.getThrowable();
    boolean canRetry;
    String message = null;
    if (throwable instanceof HttpResponseException) {
      HttpResponseException e = (HttpResponseException) throwable;
      statusCode = HttpJsonStatusCode.of(e.getStatusCode(), e.getMessage());
      canRetry = translateExceptionParameters.getRetryableCodes().contains(statusCode.getCode());
      message = e.getStatusMessage();
    } else {
      // Do not retry on unknown throwable, even when UNKNOWN is in retryableCodes
      statusCode = HttpJsonStatusCode.of(Code.UNKNOWN);
      canRetry = false;
    }

    ApiException exception =
        message == null
            ? ApiExceptionFactory.createException(throwable, statusCode, canRetry)
            : ApiExceptionFactory.createException(message, throwable, statusCode, canRetry);
    translateExceptionParameters.getResultFuture().setException(exception);
  }

  public ApiCallContext createDefaultCallContext() {
    return HttpJsonCallContext.createDefault();
  }

  public ApiCallContext getCallContextWithDefault(ApiCallContext inputContext) {
    return HttpJsonCallContext.getAsHttpJsonCallContextWithDefault(inputContext);
  }

  public ApiCallContext getCallContextWithTimeout(ApiCallContext callContext, Duration rpcTimeout) {
    HttpJsonCallContext oldContext =
        HttpJsonCallContext.getAsHttpJsonCallContextWithDefault(callContext);
    HttpJsonCallOptions oldOptions = oldContext.getCallOptions();
    HttpJsonCallOptions newOptions =
        oldOptions.withDeadlineAfter(rpcTimeout.toMillis(), TimeUnit.MILLISECONDS);
    HttpJsonCallContext nextContext = oldContext.withCallOptions(newOptions);

    if (oldOptions.getDeadline() == null) {
      return nextContext;
    }
    if (oldOptions.getDeadline().isBefore(newOptions.getDeadline())) {
      return oldContext;
    }
    return nextContext;
  }

  public ApiCallContextEnhancer getAuthCallContextEnhancer(Credentials credentials) {
    return new HttpJsonAuthCallContextEnhancer(credentials);
  }

  public ApiCallContextEnhancer getChannelCallContextEnhancer(TransportChannel channel) {
    return new HttpJsonChannelCallContextEnhancer(channel);
  }
}
