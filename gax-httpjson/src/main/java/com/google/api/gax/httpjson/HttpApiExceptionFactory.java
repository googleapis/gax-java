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
import com.google.common.base.Strings;

/**
 * A factory class that returns the corresponding type of exception class from the given status
 * code.
 *
 * <p>For more information about the status codes returned by the underlying http exception see
 * https://github.com/googleapis/googleapis/blob/master/google/rpc/code.proto
 */
@BetaApi
public class HttpApiExceptionFactory {
  public static final String FAILED_PRECONDITION = "FAILED_PRECONDITION";
  public static final String OUT_OF_RANGE = "OUT_OF_RANGE";
  public static final String ALREADY_EXISTS = "ALREADY_EXISTS";
  public static final String DATA_LOSS = "DATA_LOSS";
  public static final String UNKNOWN = "UNKNOWN";

  @BetaApi
  public static ApiException createException(Throwable cause, int httpStatus, boolean retryable) {
    HttpJsonStatusCode statusCode = HttpJsonStatusCode.of(httpStatus);
    String causeMessage = Strings.nullToEmpty(cause.getMessage()).toUpperCase();
    if (httpStatus == 400) {
      if (causeMessage.contains(OUT_OF_RANGE)) {
        return new OutOfRangeException(cause, statusCode, retryable);
      } else if (causeMessage.contains(FAILED_PRECONDITION)) {
        return new FailedPreconditionException(cause, statusCode, retryable);
      } else {
        return new InvalidArgumentException(cause, statusCode, retryable);
      }
    } else if (httpStatus == 401) {
      return new UnauthenticatedException(cause, statusCode, retryable);
    } else if (httpStatus == 403) {
      return new PermissionDeniedException(cause, statusCode, retryable);
    } else if (httpStatus == 404) {
      return new NotFoundException(cause, statusCode, retryable);
    } else if (httpStatus == 409) {
      if (causeMessage.contains(ALREADY_EXISTS)) {
        return new AlreadyExistsException(cause, statusCode, retryable);
      } else {
        return new AbortedException(cause, statusCode, retryable);
      }
    } else if (httpStatus == 429) {
      return new ResourceExhaustedException(cause, statusCode, retryable);
    } else if (httpStatus == 499) {
      return new CancelledException(cause, statusCode, retryable);
    } else if (httpStatus == 500) {
      if (causeMessage.contains(DATA_LOSS)) {
        return new DataLossException(cause, statusCode, retryable);
      } else if (causeMessage.contains(UNKNOWN)) {
        return new UnknownException(cause, statusCode, retryable);
      } else {
        return new InternalException(cause, statusCode, retryable);
      }
    } else if (httpStatus == 503) {
      return new UnavailableException(cause, statusCode, retryable);
    } else if (httpStatus == 504) {
      return new DeadlineExceededException(cause, statusCode, retryable);
    }

    return new ApiException(cause, statusCode, retryable);
  }

  @BetaApi
  public static ApiException createException(
      String message, Throwable cause, int httpStatus, boolean retryable) {
    HttpJsonStatusCode statusCode = HttpJsonStatusCode.of(httpStatus);
    String causeMessage = Strings.nullToEmpty(cause.getMessage());
    if (httpStatus == 400) {
      if (causeMessage.contains(OUT_OF_RANGE)) {
        return new OutOfRangeException(message, cause, statusCode, retryable);
      } else if (causeMessage.contains(FAILED_PRECONDITION)) {
        return new FailedPreconditionException(message, cause, statusCode, retryable);
      } else {
        return new InvalidArgumentException(message, cause, statusCode, retryable);
      }
    } else if (httpStatus == 401) {
      return new UnauthenticatedException(message, cause, statusCode, retryable);
    } else if (httpStatus == 403) {
      return new PermissionDeniedException(message, cause, statusCode, retryable);
    } else if (httpStatus == 404) {
      return new NotFoundException(message, cause, statusCode, retryable);
    } else if (httpStatus == 409) {
      if (causeMessage.contains(ALREADY_EXISTS)) {
        return new AlreadyExistsException(message, cause, statusCode, retryable);
      } else {
        return new AbortedException(message, cause, statusCode, retryable);
      }
    } else if (httpStatus == 429) {
      return new ResourceExhaustedException(message, cause, statusCode, retryable);
    } else if (httpStatus == 499) {
      return new CancelledException(message, cause, statusCode, retryable);
    } else if (httpStatus == 500) {
      if (causeMessage.contains(DATA_LOSS)) {
        return new DataLossException(message, cause, statusCode, retryable);
      } else if (causeMessage.contains(UNKNOWN)) {
        return new UnknownException(message, cause, statusCode, retryable);
      } else {
        return new InternalException(message, cause, statusCode, retryable);
      }
    } else if (httpStatus == 503) {
      return new UnavailableException(message, cause, statusCode, retryable);
    } else if (httpStatus == 504) {
      return new DeadlineExceededException(message, cause, statusCode, retryable);
    }

    return new ApiException(message, cause, statusCode, retryable);
  }
}
