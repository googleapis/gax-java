/*
 * Copyright 2016, Google Inc. All rights reserved.
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

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.grpc.StatusRuntimeException;

/**
 * A utility class for working with ApiException.
 */
public class ApiExceptions {

  /**
   * Invokes get on the given future, and if it throws an exception, then processes it in the
   * following way:
   *
   * <p>
   * 1. If it is an UncheckedExecutionException, then:<br>
   * a. If the exception cause is an ApiException, it is rethrown.<br>
   * b. If the exception cause is a StatusRuntimeException, it is wrapped in an ApiException and
   * thrown.<br>
   * 2. Otherwise, if it is any other RuntimeException, it propagates.
   */
  public static <ResponseT> ResponseT callAndTranslateApiException(RpcFuture<ResponseT> future) {
    try {
      return Futures.getUnchecked(future);
    } catch (UncheckedExecutionException exception) {
      Throwables.propagateIfInstanceOf(exception.getCause(), ApiException.class);
      if (exception.getCause() instanceof StatusRuntimeException) {
        StatusRuntimeException statusException = (StatusRuntimeException) exception.getCause();
        throw new ApiException(statusException, statusException.getStatus().getCode(), false);
      }
      throw exception;
    }
  }
}
