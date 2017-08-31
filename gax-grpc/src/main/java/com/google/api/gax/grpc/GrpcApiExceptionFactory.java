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
import com.google.api.gax.rpc.AbortedException;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.CancelledException;
import com.google.api.gax.rpc.DataLossException;
import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.api.gax.rpc.InternalException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.OutOfRangeException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.ResourceExhaustedException;
import com.google.api.gax.rpc.UnauthenticatedException;
import com.google.api.gax.rpc.UnavailableException;
import com.google.api.gax.rpc.UnknownException;
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
        return new CancelledException(cause, grpcStatusCode, retryable);
      case NOT_FOUND:
        return new NotFoundException(cause, grpcStatusCode, retryable);
      case UNKNOWN:
        return new UnknownException(cause, grpcStatusCode, retryable);
      case INVALID_ARGUMENT:
        return new InvalidArgumentException(cause, grpcStatusCode, retryable);
      case DEADLINE_EXCEEDED:
        return new DeadlineExceededException(cause, grpcStatusCode, retryable);
      case ALREADY_EXISTS:
        return new AlreadyExistsException(cause, grpcStatusCode, retryable);
      case PERMISSION_DENIED:
        return new PermissionDeniedException(cause, grpcStatusCode, retryable);
      case RESOURCE_EXHAUSTED:
        return new ResourceExhaustedException(cause, grpcStatusCode, retryable);
      case FAILED_PRECONDITION:
        return new FailedPreconditionException(cause, grpcStatusCode, retryable);
      case ABORTED:
        return new AbortedException(cause, grpcStatusCode, retryable);
      case OUT_OF_RANGE:
        return new OutOfRangeException(cause, grpcStatusCode, retryable);
      case INTERNAL:
        return new InternalException(cause, grpcStatusCode, retryable);
      case UNAVAILABLE:
        return new UnavailableException(cause, grpcStatusCode, retryable);
      case DATA_LOSS:
        return new DataLossException(cause, grpcStatusCode, retryable);
      case UNAUTHENTICATED:
        return new UnauthenticatedException(cause, grpcStatusCode, retryable);

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
        return new CancelledException(message, cause, grpcStatusCode, retryable);
      case NOT_FOUND:
        return new NotFoundException(message, cause, grpcStatusCode, retryable);
      case UNKNOWN:
        return new UnknownException(message, cause, grpcStatusCode, retryable);
      case INVALID_ARGUMENT:
        return new InvalidArgumentException(message, cause, grpcStatusCode, retryable);
      case DEADLINE_EXCEEDED:
        return new DeadlineExceededException(message, cause, grpcStatusCode, retryable);
      case ALREADY_EXISTS:
        return new AlreadyExistsException(message, cause, grpcStatusCode, retryable);
      case PERMISSION_DENIED:
        return new PermissionDeniedException(message, cause, grpcStatusCode, retryable);
      case RESOURCE_EXHAUSTED:
        return new ResourceExhaustedException(message, cause, grpcStatusCode, retryable);
      case FAILED_PRECONDITION:
        return new FailedPreconditionException(message, cause, grpcStatusCode, retryable);
      case ABORTED:
        return new AbortedException(message, cause, grpcStatusCode, retryable);
      case OUT_OF_RANGE:
        return new OutOfRangeException(message, cause, grpcStatusCode, retryable);
      case INTERNAL:
        return new InternalException(message, cause, grpcStatusCode, retryable);
      case UNAVAILABLE:
        return new UnavailableException(message, cause, grpcStatusCode, retryable);
      case DATA_LOSS:
        return new DataLossException(message, cause, grpcStatusCode, retryable);
      case UNAUTHENTICATED:
        return new UnauthenticatedException(message, cause, grpcStatusCode, retryable);

      default:
        return new ApiException(cause, grpcStatusCode, retryable);
    }
  }
}
