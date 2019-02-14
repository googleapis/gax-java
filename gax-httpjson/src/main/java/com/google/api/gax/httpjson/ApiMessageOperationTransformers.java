/*
 * Copyright 2019 Google LLC
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
package com.google.api.gax.httpjson;

import com.google.api.core.ApiFunction;
import com.google.api.core.BetaApi;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.StatusCode.Code;

/** Public for technical reasons; intended for use by generated code. */
@BetaApi("The surface for use by generated code is not stable yet and may change in the future.")
public class ApiMessageOperationTransformers<ResponseT extends ApiMessage>
    implements ApiFunction<OperationSnapshot, ResponseT> {
  private final Class<ResponseT> packedClass;

  private ApiMessageOperationTransformers(Class<ResponseT> packedClass) {
    this.packedClass = packedClass;
  }

  public static <ResponseT extends ApiMessage> ApiMessageOperationTransformers<ResponseT> create(
      Class<ResponseT> apiMessageClass) {
    return new ApiMessageOperationTransformers<>(apiMessageClass);
  }

  @Override
  @SuppressWarnings("unchecked")
  public ResponseT apply(OperationSnapshot operationSnapshot) {
    if (!operationSnapshot.getErrorCode().getCode().equals(Code.OK)) {
      throw ApiExceptionFactory.createException(
          "Operation with name \""
              + operationSnapshot.getName()
              + "\" failed with status = "
              + operationSnapshot.getErrorCode()
              + " and message = "
              + operationSnapshot.getErrorMessage(),
          null,
          operationSnapshot.getErrorCode(),
          false);
    }
    try {
      return (ResponseT) operationSnapshot.getResponse();
    } catch (RuntimeException e) {
      throw ApiExceptionFactory.createException(
          "Operation with name \""
              + operationSnapshot.getName()
              + "\" succeeded, but encountered a problem unpacking it.",
          e,
          operationSnapshot.getErrorCode(),
          false);
    }
  }
}
