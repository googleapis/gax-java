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
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.CancelledException;
import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.api.gax.rpc.UnavailableException;
import com.google.api.gax.rpc.UnknownException;

/**
 * A factory class that returns the corresponding type of exception class from the given status
 * code.
 *
 * <p>For more information about the status codes returned by the underlying grpc exception see
 * https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/Status.java
 */
@InternalApi("for testing")
public class FakeApiExceptionFactory {
  public static ApiException createException(
      Throwable cause, FakeStatusCode.Code statusCode, boolean retryable) {
    FakeStatusCode fakeStatusCode = FakeStatusCode.of(statusCode);
    switch (statusCode) {
      case CANCELLED:
        return new CancelledException(cause, fakeStatusCode, retryable);
        //      case NOT_FOUND:
        //        return new NotFoundException(cause, fakeStatusCode, retryable);
      case UNKNOWN:
        return new UnknownException(cause, fakeStatusCode, retryable);
        //      case INVALID_ARGUMENT:
        //        return new InvalidArgumentException(cause, fakeStatusCode, retryable);
      case DEADLINE_EXCEEDED:
        return new DeadlineExceededException(cause, fakeStatusCode, retryable);
      case ALREADY_EXISTS:
        return new AlreadyExistsException(cause, fakeStatusCode, retryable);
        //      case PERMISSION_DENIED:
        //        return new PermissionDeniedException(cause, fakeStatusCode, retryable);
        //      case RESOURCE_EXHAUSTED:
        //        return new ResourceExhaustedException(cause, fakeStatusCode, retryable);
      case FAILED_PRECONDITION:
        return new FailedPreconditionException(cause, fakeStatusCode, retryable);
        //      case ABORTED:
        //        return new AbortedException(cause, fakeStatusCode, retryable);
        //      case OUT_OF_RANGE:
        //        return new OutOfRangeException(cause, fakeStatusCode, retryable);
        //      case INTERNAL:
        //        return new InternalException(cause, fakeStatusCode, retryable);
      case UNAVAILABLE:
        return new UnavailableException(cause, fakeStatusCode, retryable);
        //      case DATA_LOSS:
        //        return new DataLossException(cause, fakeStatusCode, retryable);
        //      case UNAUTHENTICATED:
        //        return new UnauthenticatedException(cause, fakeStatusCode, retryable);

      default:
        return new ApiException(cause, fakeStatusCode, retryable);
    }
  }

  public static ApiException createException(
      String message, Throwable cause, FakeStatusCode.Code statusCode, boolean retryable) {
    FakeStatusCode fakeStatusCode = FakeStatusCode.of(statusCode);
    switch (statusCode) {
      case CANCELLED:
        return new CancelledException(message, cause, fakeStatusCode, retryable);
        //      case NOT_FOUND:
        //        return new NotFoundException(message, cause, fakeStatusCode, retryable);
      case UNKNOWN:
        return new UnknownException(message, cause, fakeStatusCode, retryable);
        //      case INVALID_ARGUMENT:
        //        return new InvalidArgumentException(message, cause, fakeStatusCode, retryable);
      case DEADLINE_EXCEEDED:
        return new DeadlineExceededException(message, cause, fakeStatusCode, retryable);
      case ALREADY_EXISTS:
        return new AlreadyExistsException(message, cause, fakeStatusCode, retryable);
        //      case PERMISSION_DENIED:
        //        return new PermissionDeniedException(message, cause, fakeStatusCode, retryable);
        //      case RESOURCE_EXHAUSTED:
        //        return new ResourceExhaustedException(message, cause, fakeStatusCode, retryable);
      case FAILED_PRECONDITION:
        return new FailedPreconditionException(message, cause, fakeStatusCode, retryable);
        //      case ABORTED:
        //        return new AbortedException(message, cause, fakeStatusCode, retryable);
        //      case OUT_OF_RANGE:
        //        return new OutOfRangeException(message, cause, fakeStatusCode, retryable);
        //      case INTERNAL:
        //        return new InternalException(message, cause, fakeStatusCode, retryable);
      case UNAVAILABLE:
        return new UnavailableException(message, cause, fakeStatusCode, retryable);
        //      case DATA_LOSS:
        //        return new DataLossException(message, cause, fakeStatusCode, retryable);
        //      case UNAUTHENTICATED:
        //        return new UnauthenticatedException(message, cause, fakeStatusCode, retryable);

      default:
        return new ApiException(cause, fakeStatusCode, retryable);
    }
  }
}
