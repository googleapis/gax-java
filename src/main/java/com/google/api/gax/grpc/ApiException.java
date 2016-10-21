/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

import com.google.common.base.Preconditions;

import io.grpc.Status;

/**
 * Represents an exception thrown during an RPC call.
 *
 * <p>
 * It stores information useful for functionalities in {@link UnaryApiCallable} and
 * {@link StreamingApiCallable}. For more information about the status codes returned by the
 * underlying grpc exception see
 * https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/Status.java
 */
public class ApiException extends RuntimeException {
  private static final long serialVersionUID = -725668425459379694L;

  private final Status.Code statusCode;
  private final boolean retryable;

  ApiException(Throwable cause, Status.Code statusCode, boolean retryable) {
    super(cause);
    this.statusCode = Preconditions.checkNotNull(statusCode);
    this.retryable = retryable;
  }

  ApiException(String message, Throwable cause, Status.Code statusCode, boolean retryable) {
    super(message, cause);
    this.statusCode = Preconditions.checkNotNull(statusCode);
    this.retryable = retryable;
  }

  /**
   * Returns whether the failed request can be retried.
   */
  public boolean isRetryable() {
    return retryable;
  }

  /**
   * Returns the status code of the underlying grpc exception. In cases where the underlying
   * exception is not of type StatusException or StatusRuntimeException, the status code will be
   * Status.Code.UNKNOWN. For more information about status codes see
   * https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/Status.java
   */
  public Status.Code getStatusCode() {
    return statusCode;
  }
}
