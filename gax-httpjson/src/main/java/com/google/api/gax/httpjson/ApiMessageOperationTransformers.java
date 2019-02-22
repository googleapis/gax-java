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

/**
 * Transformers from OperationSnapshot wrappers to the underlying native ApiMessage objects. Public
 * for technical reasons; intended for use by generated code.
 */
@BetaApi("The surface for use by generated code is not stable yet and may change in the future.")
public class ApiMessageOperationTransformers {
  private ApiMessageOperationTransformers() {}

  public static class ResponseTransformer<ResponseT extends ApiMessage>
      implements ApiFunction<OperationSnapshot, ResponseT> {
    private final Class<ResponseT> responseTClass;

    private ResponseTransformer(Class<ResponseT> responseTClass) {
      this.responseTClass = responseTClass;
    }

    /** Unwraps an OperationSnapshot and returns the contained method response message. */
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
      if (!operationSnapshot.getResponse().getClass().isAssignableFrom(responseTClass)) {
        throw ApiExceptionFactory.createException(
            new Throwable(
                String.format(
                    "Operation with name \"%s\" succeeded, but its response type %s cannot be cast to %s.",
                    operationSnapshot.getName(),
                    operationSnapshot.getResponse().getClass().getCanonicalName(),
                    responseTClass.getCanonicalName())),
            operationSnapshot.getErrorCode(),
            false);
      }
      return (ResponseT) operationSnapshot.getResponse();
    }

    public static <ResponseT extends ApiMessage>
        ApiMessageOperationTransformers.ResponseTransformer<ResponseT> create(
            Class<ResponseT> packedClass) {
      return new ApiMessageOperationTransformers.ResponseTransformer<>(packedClass);
    }
  }

  @SuppressWarnings("unchecked")
  public static class MetadataTransformer<MetadataT extends ApiMessage>
      implements ApiFunction<OperationSnapshot, MetadataT> {
    private final Class<MetadataT> metadataTClass;

    private MetadataTransformer(Class<MetadataT> metadataTClass) {
      this.metadataTClass = metadataTClass;
    }

    /** Unwraps an OperationSnapshot and returns the contained operation metadata message. */
    @Override
    public MetadataT apply(OperationSnapshot operationSnapshot) {
      if (!operationSnapshot.getMetadata().getClass().isAssignableFrom(metadataTClass)) {
        throw ApiExceptionFactory.createException(
            new Throwable(
                String.format(
                    "Operation with name \"%s\" succeeded, but its metadata type %s cannot be cast to %s.",
                    operationSnapshot.getName(),
                    operationSnapshot.getMetadata().getClass().getCanonicalName(),
                    metadataTClass.getCanonicalName())),
            operationSnapshot.getErrorCode(),
            false);
      }
      return (MetadataT) (operationSnapshot.getMetadata());
    }

    public static <MetadataT extends ApiMessage>
        ApiMessageOperationTransformers.MetadataTransformer<MetadataT> create(
            Class<MetadataT> packedClass) {
      return new ApiMessageOperationTransformers.MetadataTransformer<>(packedClass);
    }
  }
}
