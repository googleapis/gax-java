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
import com.google.api.core.BetaApi;
import com.google.api.gax.retrying.RetryAlgorithm;
import com.google.api.gax.retrying.RetryingExecutor;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.api.gax.retrying.ScheduledRetryingExecutor;
import com.google.longrunning.CancelOperationRequest;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.grpc.Status;

/**
 * An OperationCallable is an immutable object which is capable of initiating RPC calls to
 * long-running API methods and returning an OperationFuture to manage the polling of the Operation
 * and getting the response.
 */
@BetaApi
public final class OperationCallable<
    RequestT, ResponseT extends Message, MetadataT extends Message> {
  private final UnaryCallable<RequestT, Operation> initialCallable;
  private final ClientContext clientContext;
  private final RetryingExecutor<Operation> executor;
  private final OperationsClient operationsClient;
  private final ApiFunction<Operation, ResponseT> responseTransformer;
  private final ApiFunction<Operation, MetadataT> metadataTransformer;

  /** Private for internal use. */
  private OperationCallable(
      UnaryCallable<RequestT, Operation> initialCallable,
      ClientContext clientContext,
      RetryingExecutor<Operation> executor,
      OperationsClient operationsClient,
      ApiFunction<Operation, ResponseT> responseTransformer,
      ApiFunction<Operation, MetadataT> metadataTransformer) {
    this.initialCallable = checkNotNull(initialCallable);
    this.clientContext = checkNotNull(clientContext);
    this.executor = checkNotNull(executor);
    this.operationsClient = checkNotNull(operationsClient);
    this.responseTransformer = checkNotNull(responseTransformer);
    this.metadataTransformer = checkNotNull(metadataTransformer);
  }

  /**
   * Creates a callable object that represents a long-running operation. Public only for technical
   * reasons - for advanced usage
   *
   * @param settings {@link OperationCallSettings} to configure the method-level settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param operationsClient {@link OperationsClient} to use to poll for updates on the Operation.
   * @return {@link OperationCallable} callable object.
   */
  public static <RequestT, ResponseT extends Message, MetadataT extends Message>
      OperationCallable<RequestT, ResponseT, MetadataT> create(
          OperationCallSettings<RequestT, ResponseT, MetadataT> settings,
          ClientContext clientContext,
          OperationsClient operationsClient) {
    UnaryCallable<RequestT, Operation> initialCallable =
        settings.getInitialCallSettings().create(clientContext);

    RetryAlgorithm<Operation> pollingAlgorithm =
        new RetryAlgorithm<>(new OperationResponsePollAlgorithm(), settings.getPollingAlgorithm());
    ScheduledRetryingExecutor<Operation> scheduler =
        new ScheduledRetryingExecutor<>(pollingAlgorithm, clientContext.getExecutor());

    return new OperationCallable<>(
        initialCallable,
        clientContext,
        scheduler,
        operationsClient,
        new ResponseTransformer<>(new AnyTransformer<>(settings.getResponseClass())),
        new MetadataTransformer<>(new AnyTransformer<>(settings.getMetadataClass())));
  }

  /**
   * Initiates an operation asynchronously. If the {@link io.grpc.Channel} encapsulated in the given
   * {@link com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound at
   * construction time.
   *
   * @param request The request to initiate the operation.
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return {@link OperationFuture} for the call result
   */
  public OperationFuture<ResponseT, MetadataT> futureCall(RequestT request, CallContext context) {
    if (context.getChannel() == null) {
      context = context.withChannel(clientContext.getChannel());
    }
    return futureCall(initialCallable.futureCall(request, context));
  }

  OperationFuture<ResponseT, MetadataT> futureCall(ApiFuture<Operation> initialFuture) {
    RetryingCallable<RequestT, Operation> callable =
        new RetryingCallable<>(
            new OperationCheckingCallable<RequestT>(
                operationsClient.getOperationCallable(), initialFuture),
            executor);

    RetryingFuture<Operation> pollingFuture = callable.futureCall(null, null);
    return new OperationFuture<>(
        pollingFuture, initialFuture, responseTransformer, metadataTransformer);
  }

  /**
   * Same as {@link #futureCall(Object, CallContext)}, with null {@link io.grpc.Channel} and default
   * {@link io.grpc.CallOptions}.
   *
   * @param request The request to initiate the operation.
   * @return {@link OperationFuture} for the call result
   */
  public OperationFuture<ResponseT, MetadataT> futureCall(RequestT request) {
    return futureCall(request, CallContext.createDefault().withChannel(clientContext.getChannel()));
  }

  /**
   * Initiates an operation and polls for the final result. If the {@link io.grpc.Channel}
   * encapsulated in the given {@link com.google.api.gax.grpc.CallContext} is null, a channel must
   * have already been bound at construction time.
   *
   * @param request The request to initiate the operation.
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return the call result
   * @throws ApiException if there is any bad status in the response.
   * @throws RuntimeException if there is any other exception unrelated to bad status.
   */
  public ResponseT call(RequestT request, CallContext context) {
    return ApiExceptions.callAndTranslateApiException(futureCall(request, context));
  }

  /**
   * Same as {@link #call(Object, CallContext)}, with null {@link io.grpc.Channel} and default
   * {@link io.grpc.CallOptions}.
   *
   * @param request The request to initiate the operation.
   * @return the call result
   * @throws ApiException if there is any bad status in the response.
   * @throws RuntimeException if there is any other exception unrelated to bad status.
   */
  public ResponseT call(RequestT request) {
    return ApiExceptions.callAndTranslateApiException(futureCall(request));
  }

  /**
   * Creates a new {@link OperationFuture} to watch an operation that has been initiated previously.
   * Note: This is not type-safe at static time; the result type can only be checked once the
   * operation finishes.
   *
   * @param operationName The name of the operation to resume.
   * @return {@link OperationFuture} for the call result.
   */
  public OperationFuture<ResponseT, MetadataT> resumeFutureCall(String operationName) {
    ApiFuture<Operation> getOperationFuture =
        operationsClient
            .getOperationCallable()
            .futureCall(GetOperationRequest.newBuilder().setName(operationName).build());

    return futureCall(getOperationFuture);
  }

  /**
   * Sends a cancellation request to the server for the operation with name {@code operationName}.
   *
   * @param operationName The name of the operation to cancel.
   * @return the future which completes once the operation is canceled on the server side.
   */
  public ApiFuture<Empty> cancel(String operationName) {
    return operationsClient
        .cancelOperationCallable()
        .futureCall(CancelOperationRequest.newBuilder().setName(operationName).build());
  }

  private static class ResponseTransformer<ResponseT extends Message>
      implements ApiFunction<Operation, ResponseT> {
    private final AnyTransformer<ResponseT> transformer;

    public ResponseTransformer(AnyTransformer<ResponseT> transformer) {
      this.transformer = transformer;
    }

    @Override
    public ResponseT apply(Operation input) {
      Status status = Status.fromCodeValue(input.getError().getCode());
      if (!status.equals(Status.OK)) {
        throw new ApiException(
            "Operation with name \"" + input.getName() + "\" failed with status = " + status,
            null,
            status.getCode(),
            false);
      }
      try {
        return transformer.apply(input.getResponse());
      } catch (RuntimeException e) {
        throw new ApiException(
            "Operation with name \""
                + input.getName()
                + "\" succeeded, but encountered a problem unpacking it.",
            e,
            status.getCode(),
            false);
      }
    }
  }

  private static class MetadataTransformer<MetadataT extends Message>
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
        throw new ApiException(
            "Polling operation with name \""
                + input.getName()
                + "\" succeeded, but encountered a problem unpacking it.",
            e,
            Status.fromCodeValue(input.getError().getCode()).getCode(),
            false);
      }
    }
  }

  private static class AnyTransformer<PackedT extends Message>
      implements ApiFunction<Any, PackedT> {
    private final Class<PackedT> packedClass;

    private AnyTransformer(Class<PackedT> packedClass) {
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
