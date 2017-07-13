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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.retrying.RetryingExecutor;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.OperationCallSettings;
import com.google.api.gax.rpc.OperationCallable;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.longrunning.CancelOperationRequest;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.Operation;
import com.google.longrunning.stub.OperationsStub;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.grpc.Status;

/**
 * A GrpcOperationCallableImpl is an immutable object which is capable of initiating RPC calls to
 * long-running API methods and returning a {@link GrpcOperationFuture} to manage the polling of the
 * Operation and getting the response.
 *
 * <p>Package-private for internal use.
 */
class GrpcOperationCallableImpl<RequestT, ResponseT extends Message, MetadataT extends Message>
    extends OperationCallable<RequestT, ResponseT, MetadataT, Operation> {

  private final UnaryCallable<RequestT, Operation> initialCallable;
  private final ClientContext clientContext;
  private final RetryingExecutor<Operation> executor;
  private final OperationsStub operationsStub;
  private final ApiFunction<Operation, ResponseT> responseTransformer;
  private final ApiFunction<Operation, MetadataT> metadataTransformer;

  GrpcOperationCallableImpl(
      UnaryCallable<RequestT, Operation> initialCallable,
      ClientContext clientContext,
      RetryingExecutor<Operation> executor,
      OperationsStub operationsStub,
      OperationCallSettings<RequestT, ResponseT, MetadataT, Operation> operationCallSettings) {
    this.initialCallable = checkNotNull(initialCallable);
    this.clientContext = checkNotNull(clientContext);
    this.executor = checkNotNull(executor);
    this.operationsStub = checkNotNull(operationsStub);
    this.responseTransformer =
        new ResponseTransformer<>(new AnyTransformer<>(operationCallSettings.getResponseClass()));
    this.metadataTransformer =
        new MetadataTransformer<>(new AnyTransformer<>(operationCallSettings.getMetadataClass()));
  }

  /**
   * Initiates an operation asynchronously. If the {@link io.grpc.Channel} encapsulated in the given
   * {@link ApiCallContext} is null, a channel must have already been bound at construction time.
   *
   * @param request The request to initiate the operation.
   * @param context {@link ApiCallContext} to make the call with
   * @return {@link GrpcOperationFuture} for the call result
   */
  @Override
  public GrpcOperationFuture<ResponseT, MetadataT> futureCall(
      RequestT request, ApiCallContext context) {
    return futureCall(initialCallable.futureCall(request, context));
  }

  GrpcOperationFuture<ResponseT, MetadataT> futureCall(ApiFuture<Operation> initialFuture) {
    GrpcRetryingCallable<RequestT, Operation> callable =
        new GrpcRetryingCallable<>(
            new OperationCheckingCallable<RequestT>(
                operationsStub.getOperationCallable(), initialFuture),
            executor);

    RetryingFuture<Operation> pollingFuture = callable.futureCall(null, null);
    return new GrpcOperationFuture<>(
        pollingFuture, initialFuture, responseTransformer, metadataTransformer);
  }

  /**
   * Creates a new {@link GrpcOperationFuture} to watch an operation that has been initiated
   * previously. Note: This is not type-safe at static time; the result type can only be checked
   * once the operation finishes.
   *
   * @param operationName The name of the operation to resume.
   * @param context {@link ApiCallContext} to make the call with
   * @return {@link GrpcOperationFuture} for the call result.
   */
  @Override
  public GrpcOperationFuture<ResponseT, MetadataT> resumeFutureCall(
      String operationName, ApiCallContext context) {
    GetOperationRequest request = GetOperationRequest.newBuilder().setName(operationName).build();
    ApiFuture<Operation> getOperationFuture =
        operationsStub.getOperationCallable().futureCall(request, context);

    return futureCall(getOperationFuture);
  }

  /**
   * Sends a cancellation request to the server for the operation with name {@code operationName}.
   *
   * @param operationName The name of the operation to cancel.
   * @param context {@link ApiCallContext} to make the call with
   * @return the future which completes once the operation is canceled on the server side.
   */
  @Override
  public ApiFuture<Void> cancel(String operationName, ApiCallContext context) {
    CancelOperationRequest cancelRequest =
        CancelOperationRequest.newBuilder().setName(operationName).build();
    return ApiFutures.transform(
        operationsStub.cancelOperationCallable().futureCall(cancelRequest, context),
        new ApiFunction<Empty, Void>() {
          @Override
          public Void apply(Empty empty) {
            return null;
          }
        });
  }

  static class ResponseTransformer<ResponseT extends Message>
      implements ApiFunction<Operation, ResponseT> {
    private final AnyTransformer<ResponseT> transformer;

    public ResponseTransformer(AnyTransformer<ResponseT> transformer) {
      this.transformer = transformer;
    }

    @Override
    public ResponseT apply(Operation input) {
      Status status = Status.fromCodeValue(input.getError().getCode());
      if (!status.equals(Status.OK)) {
        throw new GrpcApiException(
            "Operation with name \"" + input.getName() + "\" failed with status = " + status,
            null,
            status.getCode(),
            false);
      }
      try {
        return transformer.apply(input.getResponse());
      } catch (RuntimeException e) {
        throw new GrpcApiException(
            "Operation with name \""
                + input.getName()
                + "\" succeeded, but encountered a problem unpacking it.",
            e,
            status.getCode(),
            false);
      }
    }
  }

  static class MetadataTransformer<MetadataT extends Message>
      implements ApiFunction<Operation, MetadataT> {
    private final AnyTransformer<MetadataT> transformer;

    public MetadataTransformer(AnyTransformer<MetadataT> transformer) {
      this.transformer = transformer;
    }

    @Override
    public MetadataT apply(Operation input) {
      try {
        return transformer.apply(input.hasMetadata() ? input.getMetadata() : null);
      } catch (RuntimeException e) {
        throw new GrpcApiException(
            "Polling operation with name \""
                + input.getName()
                + "\" succeeded, but encountered a problem unpacking it.",
            e,
            Status.fromCodeValue(input.getError().getCode()).getCode(),
            false);
      }
    }
  }

  static class AnyTransformer<PackedT extends Message> implements ApiFunction<Any, PackedT> {
    private final Class<PackedT> packedClass;

    public AnyTransformer(Class<PackedT> packedClass) {
      this.packedClass = packedClass;
    }

    @Override
    public PackedT apply(Any input) {
      try {
        return input == null || packedClass == null ? null : input.unpack(packedClass);
      } catch (InvalidProtocolBufferException | ClassCastException e) {
        throw new IllegalStateException(
            "Failed to unpack object from 'any' field. Expected "
                + packedClass.getName()
                + ", found "
                + input.getTypeUrl());
      }
    }
  }
}
