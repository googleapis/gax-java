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
import com.google.api.gax.rpc.AbortedApiException;
import com.google.api.gax.rpc.AlreadyExistsApiException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.CancelledApiException;
import com.google.api.gax.rpc.DataLossApiException;
import com.google.api.gax.rpc.DeadlineExceededApiException;
import com.google.api.gax.rpc.FailedPreconditionApiException;
import com.google.api.gax.rpc.InternalApiException;
import com.google.api.gax.rpc.InvalidArgumentApiException;
import com.google.api.gax.rpc.NotFoundApiException;
import com.google.api.gax.rpc.OutOfRangeApiException;
import com.google.api.gax.rpc.PermissionDeniedApiException;
import com.google.api.gax.rpc.ResourceExhaustedApiException;
import com.google.api.gax.rpc.UnauthenticatedApiException;
import com.google.api.gax.rpc.UnavailableApiException;
import com.google.api.gax.rpc.UnknownApiException;
import io.grpc.Status;

/**
 * A factory class that returns the corresponding type of exception class from the given status
 * code.
 *
 * <p>For more information about the status codes returned by the underlying grpc exception see
 * https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/Status.java
 */
@BetaApi
public class GrpcApiExceptionFactory {
  @BetaApi
  public static ApiException createException(
      Throwable cause, Status.Code statusCode, boolean retryable) {
    GrpcStatusCode grpcStatusCode = GrpcStatusCode.of(statusCode);
    switch (statusCode) {
      case CANCELLED:
        return new CancelledApiException(cause, grpcStatusCode, retryable);
      case NOT_FOUND:
        return new NotFoundApiException(cause, grpcStatusCode, retryable);
      case UNKNOWN:
        return new UnknownApiException(cause, grpcStatusCode, retryable);
      case INVALID_ARGUMENT:
        return new InvalidArgumentApiException(cause, grpcStatusCode, retryable);
      case DEADLINE_EXCEEDED:
        return new DeadlineExceededApiException(cause, grpcStatusCode, retryable);
      case ALREADY_EXISTS:
        return new AlreadyExistsApiException(cause, grpcStatusCode, retryable);
      case PERMISSION_DENIED:
        return new PermissionDeniedApiException(cause, grpcStatusCode, retryable);
      case RESOURCE_EXHAUSTED:
        return new ResourceExhaustedApiException(cause, grpcStatusCode, retryable);
      case FAILED_PRECONDITION:
        return new FailedPreconditionApiException(cause, grpcStatusCode, retryable);
      case ABORTED:
        return new AbortedApiException(cause, grpcStatusCode, retryable);
      case OUT_OF_RANGE:
        return new OutOfRangeApiException(cause, grpcStatusCode, retryable);
      case INTERNAL:
        return new InternalApiException(cause, grpcStatusCode, retryable);
      case UNAVAILABLE:
        return new UnavailableApiException(cause, grpcStatusCode, retryable);
      case DATA_LOSS:
        return new DataLossApiException(cause, grpcStatusCode, retryable);
      case UNAUTHENTICATED:
        return new UnauthenticatedApiException(cause, grpcStatusCode, retryable);

      default:
        return new ApiException(cause, grpcStatusCode, retryable);
    }
  }

  @BetaApi
  public static ApiException createException(
      String message, Throwable cause, Status.Code statusCode, boolean retryable) {
    GrpcStatusCode grpcStatusCode = GrpcStatusCode.of(statusCode);
    switch (statusCode) {
      case CANCELLED:
        return new CancelledApiException(message, cause, grpcStatusCode, retryable);
      case NOT_FOUND:
        return new NotFoundApiException(message, cause, grpcStatusCode, retryable);
      case UNKNOWN:
        return new UnknownApiException(message, cause, grpcStatusCode, retryable);
      case INVALID_ARGUMENT:
        return new InvalidArgumentApiException(message, cause, grpcStatusCode, retryable);
      case DEADLINE_EXCEEDED:
        return new DeadlineExceededApiException(message, cause, grpcStatusCode, retryable);
      case ALREADY_EXISTS:
        return new AlreadyExistsApiException(message, cause, grpcStatusCode, retryable);
      case PERMISSION_DENIED:
        return new PermissionDeniedApiException(message, cause, grpcStatusCode, retryable);
      case RESOURCE_EXHAUSTED:
        return new ResourceExhaustedApiException(message, cause, grpcStatusCode, retryable);
      case FAILED_PRECONDITION:
        return new FailedPreconditionApiException(message, cause, grpcStatusCode, retryable);
      case ABORTED:
        return new AbortedApiException(message, cause, grpcStatusCode, retryable);
      case OUT_OF_RANGE:
        return new OutOfRangeApiException(message, cause, grpcStatusCode, retryable);
      case INTERNAL:
        return new InternalApiException(message, cause, grpcStatusCode, retryable);
      case UNAVAILABLE:
        return new UnavailableApiException(message, cause, grpcStatusCode, retryable);
      case DATA_LOSS:
        return new DataLossApiException(message, cause, grpcStatusCode, retryable);
      case UNAUTHENTICATED:
        return new UnauthenticatedApiException(message, cause, grpcStatusCode, retryable);

      default:
        return new ApiException(cause, grpcStatusCode, retryable);
    }
  }
}
