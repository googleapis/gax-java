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
    public ResponseT apply(OperationSnapshot operationSnapshot) {
      if (!operationSnapshot.getErrorCode().getCode().equals(Code.OK)) {
        // We potentially need to handle 2xx codes that are also successful.
        throw ApiExceptionFactory.createException(
            String.format(
                "Operation with name \"%s\" failed with status = %s and message = %s",
                operationSnapshot.getName(),
                operationSnapshot.getErrorCode(),
                operationSnapshot.getErrorMessage()),
            null,
            operationSnapshot.getErrorCode(),
            false);
      }
      return transformEntityFromOperationSnapshot(
          operationSnapshot, responseTClass, operationSnapshot.getResponse(), "response");
    }

    public static <ResponseT extends ApiMessage>
        ApiMessageOperationTransformers.ResponseTransformer<ResponseT> create(
            Class<ResponseT> packedClass) {
      return new ApiMessageOperationTransformers.ResponseTransformer<>(packedClass);
    }
  }

  public static class MetadataTransformer<MetadataT extends ApiMessage>
      implements ApiFunction<OperationSnapshot, MetadataT> {
    private final Class<MetadataT> metadataTClass;

    private MetadataTransformer(Class<MetadataT> metadataTClass) {
      this.metadataTClass = metadataTClass;
    }

    /** Unwraps an OperationSnapshot and returns the contained operation metadata message. */
    @Override
    public MetadataT apply(OperationSnapshot operationSnapshot) {
      return transformEntityFromOperationSnapshot(
          operationSnapshot, metadataTClass, operationSnapshot.getMetadata(), "metadata");
    }

    public static <MetadataT extends ApiMessage>
        ApiMessageOperationTransformers.MetadataTransformer<MetadataT> create(
            Class<MetadataT> packedClass) {
      return new ApiMessageOperationTransformers.MetadataTransformer<>(packedClass);
    }
  }

  private static <T extends ApiMessage> T transformEntityFromOperationSnapshot(
      OperationSnapshot operationSnapshot,
      Class<T> clazz,
      Object operationEntity,
      String entityName) {
    if (!clazz.isAssignableFrom(operationEntity.getClass())) {
      throw ApiExceptionFactory.createException(
          new Throwable(
              String.format(
                  "Operation with name \"%s\" succeeded, but its %s type %s cannot be cast to %s.",
                  operationSnapshot.getName(),
                  entityName,
                  operationEntity.getClass().getCanonicalName(),
                  clazz.getCanonicalName())),
          operationSnapshot.getErrorCode(),
          false);
    }
    @SuppressWarnings("unchecked")
    T typedEntity = (T) operationEntity;
    return typedEntity;
  }
}
