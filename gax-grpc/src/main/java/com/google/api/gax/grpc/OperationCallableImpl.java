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
package com.google.api.gax.rpc;

import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;

/**
 * OperationCallableImpl is the basic abstraction for issuing requests for long running operations.
 *
 * <p>The preferred way to modify the behavior of an {@code OperationCallableImpl} is to use the
 * decorator pattern: Creating an {@code OperationCallableImpl} that wraps another one. In this way,
 * other abstractions remain available after the modification.
 */
@BetaApi
public interface OperationCallableImpl<RequestT, ResponseT, MetadataT, OperationT> {

  /**
   * Perform a call asynchronously.
   *
   * @param request The request to send to the service.
   * @param callContext The context for the call. The concrete type must be compatible with the
   *     transport.
   * @return {@link ApiFuture} for the call result
   */
  OperationFuture<ResponseT, MetadataT, OperationT> futureCall(
      RequestT request, ApiCallContext callContext);

  /**
   * Creates a new {@link OperationFuture} to watch an operation that has been initiated previously.
   * Note: This is not type-safe at static time; the result type can only be checked once the
   * operation finishes.
   *
   * @param operationName The name of the operation to resume.
   * @param callContext The context for the call. The concrete type must be compatible with the
   *     transport.
   * @return {@link OperationFuture} for the call result.
   */
  OperationFuture<ResponseT, MetadataT, OperationT> resumeFutureCall(
      String operationName, ApiCallContext callContext);

  /**
   * Sends a cancellation request to the server for the operation with name {@code operationName}.
   *
   * @param operationName The name of the operation to cancel.
   * @return the future which completes once the operation is canceled on the server side.
   */
  ApiFuture<Void> cancel(String operationName, ApiCallContext context);
}
