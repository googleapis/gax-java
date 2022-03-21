/*
 * Copyright 2017 Google LLC
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
 *     * Neither the name of Google LLC nor the names of its
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

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.ErrorDetails;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.BadRequest;
import com.google.rpc.DebugInfo;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Help;
import com.google.rpc.LocalizedMessage;
import com.google.rpc.PreconditionFailure;
import com.google.rpc.QuotaFailure;
import com.google.rpc.RequestInfo;
import com.google.rpc.ResourceInfo;
import com.google.rpc.RetryInfo;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.Set;

/**
 * Core logic for transforming GRPC exceptions into {@link ApiException}s. This logic is shared
 * amongst all the call types.
 *
 * <p>Package-private for internal use.
 */
class GrpcApiExceptionFactory {

  @VisibleForTesting static final String ERROR_DETAIL_KEY = "grpc-status-details-bin";
  private final ImmutableSet<Code> retryableCodes;

  GrpcApiExceptionFactory(Set<Code> retryCodes) {
    this.retryableCodes = ImmutableSet.copyOf(retryCodes);
  }

  ApiException create(Throwable throwable) {
    if (throwable instanceof StatusException) {
      StatusException e = (StatusException) throwable;
      return create(throwable, e.getStatus().getCode(), e.getTrailers());
    } else if (throwable instanceof StatusRuntimeException) {
      StatusRuntimeException e = (StatusRuntimeException) throwable;
      return create(throwable, e.getStatus().getCode(), e.getTrailers());
    } else if (throwable instanceof ApiException) {
      return (ApiException) throwable;
    } else {
      // Do not retry on unknown throwable, even when UNKNOWN is in retryableCodes
      return ApiExceptionFactory.createException(
          throwable, GrpcStatusCode.of(Status.Code.UNKNOWN), false);
    }
  }

  private ApiException create(Throwable throwable, Status.Code statusCode, Metadata metadata) {
    boolean canRetry = retryableCodes.contains(GrpcStatusCode.grpcCodeToStatusCode(statusCode));
    GrpcStatusCode grpcStatusCode = GrpcStatusCode.of(statusCode);

    if (metadata == null) {
      return ApiExceptionFactory.createException(throwable, grpcStatusCode, canRetry);
    }

    byte[] bytes = metadata.get(Metadata.Key.of(ERROR_DETAIL_KEY, Metadata.BINARY_BYTE_MARSHALLER));
    if (bytes == null) {
      return ApiExceptionFactory.createException(throwable, grpcStatusCode, canRetry);
    }

    com.google.rpc.Status status;
    try {
      status = com.google.rpc.Status.parseFrom(bytes);
    } catch (InvalidProtocolBufferException e) {
      return ApiExceptionFactory.createException(throwable, grpcStatusCode, canRetry);
    }

    return ApiExceptionFactory.createException(
        throwable, grpcStatusCode, canRetry, buildErrorDetails(status));
  }

  private ErrorDetails buildErrorDetails(com.google.rpc.Status status) {
    ErrorDetails.Builder errorDetailsBuilder = ErrorDetails.builder();
    for (Any detail : status.getDetailsList()) {
      addErrorDetail(errorDetailsBuilder, detail);
    }
    return errorDetailsBuilder.build();
  }

  private void addErrorDetail(ErrorDetails.Builder errorDetailsBuilder, Any detail) {
    try {
      if (detail.is(ErrorInfo.class)) {
        errorDetailsBuilder.setErrorInfo(detail.unpack(ErrorInfo.class));
      } else if (detail.is(RetryInfo.class)) {
        errorDetailsBuilder.setRetryInfo(detail.unpack(RetryInfo.class));
      } else if (detail.is(DebugInfo.class)) {
        errorDetailsBuilder.setDebugInfo(detail.unpack(DebugInfo.class));
      } else if (detail.is(QuotaFailure.class)) {
        errorDetailsBuilder.setQuotaFailure(detail.unpack(QuotaFailure.class));
      } else if (detail.is(PreconditionFailure.class)) {
        errorDetailsBuilder.setPreconditionFailure(detail.unpack(PreconditionFailure.class));
      } else if (detail.is(BadRequest.class)) {
        errorDetailsBuilder.setBadRequest(detail.unpack(BadRequest.class));
      } else if (detail.is(RequestInfo.class)) {
        errorDetailsBuilder.setRequestInfo(detail.unpack(RequestInfo.class));
      } else if (detail.is(ResourceInfo.class)) {
        errorDetailsBuilder.setResourceInfo(detail.unpack(ResourceInfo.class));
      } else if (detail.is(Help.class)) {
        errorDetailsBuilder.setHelp(detail.unpack(Help.class));
      } else if (detail.is(LocalizedMessage.class)) {
        errorDetailsBuilder.setLocalizedMessage(detail.unpack(LocalizedMessage.class));
      }
    } catch (InvalidProtocolBufferException e) {
      // If unpacking one of the error detail fails, it should not block unpacking other error
      // details so that end users can still get some info back. Hence, we don't need to do
      // anything.
    }
  }
}
