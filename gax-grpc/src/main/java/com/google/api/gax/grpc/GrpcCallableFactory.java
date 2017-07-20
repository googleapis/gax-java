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
package com.google.api.gax.grpc;

import com.google.api.core.BetaApi;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.retrying.ExponentialRetryAlgorithm;
import com.google.api.gax.retrying.RetryAlgorithm;
import com.google.api.gax.retrying.RetryingExecutor;
import com.google.api.gax.retrying.ScheduledRetryingExecutor;
import com.google.api.gax.rpc.ApiCallContextEnhancer;
import com.google.api.gax.rpc.BatcherFactory;
import com.google.api.gax.rpc.BatchingCallSettings;
import com.google.api.gax.rpc.BatchingCallable;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.EntryPointOperationCallable;
import com.google.api.gax.rpc.EntryPointStreamingCallable;
import com.google.api.gax.rpc.EntryPointUnaryCallable;
import com.google.api.gax.rpc.OperationCallSettings;
import com.google.api.gax.rpc.OperationCallable;
import com.google.api.gax.rpc.PagedCallSettings;
import com.google.api.gax.rpc.PagedCallable;
import com.google.api.gax.rpc.SimpleCallSettings;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StreamingCallSettings;
import com.google.api.gax.rpc.StreamingCallable;
import com.google.api.gax.rpc.UnaryCallSettingsTyped;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.longrunning.stub.OperationsStub;
import com.google.protobuf.Message;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.Status.Code;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Class with utility methods to create instances of UnaryCallable with grpc-specific features. */
@BetaApi
public class GrpcCallableFactory {

  private GrpcCallableFactory() {}

  /**
   * Create a callable object that directly issues the call to the underlying API with nothing
   * wrapping it. Designed for use by generated code.
   *
   * @param methodDescriptor the gRPC method descriptor
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createDirectCallable(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    return new GrpcDirectCallable<>(methodDescriptor);
  }

  /**
   * Create a callable object that directly issues the call to the underlying API with nothing
   * wrapping it. Designed for use by generated code.
   *
   * @param methodDescriptor the gRPC method descriptor
   */
  public static <RequestT, ResponseT>
      StreamingCallable<RequestT, ResponseT> createDirectStreamingCallable(
          MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    return new GrpcDirectStreamingCallable<>(methodDescriptor);
  }

  static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createBaseCallable(
      UnaryCallable<RequestT, ResponseT> directCallable,
      UnaryCallSettingsTyped<RequestT, ResponseT> callSettings,
      ClientContext clientContext) {

    UnaryCallable<RequestT, ResponseT> callable =
        new GrpcExceptionCallable<>(
            directCallable, getGrpcStatusCodes(callSettings.getRetryableCodes()));
    RetryAlgorithm<ResponseT> retryAlgorithm =
        new RetryAlgorithm<>(
            new ApiResultRetryAlgorithm<ResponseT>(),
            new ExponentialRetryAlgorithm(
                callSettings.getRetrySettings(), clientContext.getClock()));
    RetryingExecutor<ResponseT> retryingExecutor =
        new ScheduledRetryingExecutor<>(retryAlgorithm, clientContext.getExecutor());
    return new GrpcRetryingCallable<>(callable, retryingExecutor);
  }

  /**
   * Create a callable object that represents a simple API method. Designed for use by generated
   * code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param simpleCallSettings {@link SimpleCallSettings} to configure the method-level settings
   *     with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> create(
      UnaryCallable<RequestT, ResponseT> directCallable,
      SimpleCallSettings<RequestT, ResponseT> simpleCallSettings,
      ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> unaryCallable =
        createBaseCallable(directCallable, simpleCallSettings, clientContext);
    return new EntryPointUnaryCallable<>(
        unaryCallable, GrpcCallContext.createDefault(), getCallContextEnhancers(clientContext));
  }

  /**
   * Create a paged callable object that represents a paged API method. Designed for use by
   * generated code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param pagedCallSettings {@link PagedCallSettings} to configure the paged settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, PagedListResponseT> createPagedVariant(
          UnaryCallable<RequestT, ResponseT> directCallable,
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings,
          ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> unaryCallable =
        createBaseCallable(directCallable, pagedCallSettings, clientContext);
    UnaryCallable<RequestT, PagedListResponseT> pagedCallable =
        new PagedCallable<>(unaryCallable, pagedCallSettings.getPagedListResponseFactory());
    return new EntryPointUnaryCallable<>(
        pagedCallable, GrpcCallContext.createDefault(), getCallContextEnhancers(clientContext));
  }

  /**
   * Create a callable object that represents a simple call to a paged API method. Designed for use
   * by generated code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param pagedCallSettings {@link PagedCallSettings} to configure the method-level settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT> UnaryCallable<RequestT, ResponseT> create(
      UnaryCallable<RequestT, ResponseT> directCallable,
      PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings,
      ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> unaryCallable =
        createBaseCallable(directCallable, pagedCallSettings, clientContext);
    return new EntryPointUnaryCallable<>(
        unaryCallable, GrpcCallContext.createDefault(), getCallContextEnhancers(clientContext));
  }

  /**
   * Create a callable object that represents a batching API method. Designed for use by generated
   * code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param batchingCallSettings {@link BatchingSettings} to configure the batching related settings
   *     with.
   * @param context {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> create(
      UnaryCallable<RequestT, ResponseT> directCallable,
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings,
      ClientContext context) {
    return internalCreate(directCallable, batchingCallSettings, context).unaryCallable;
  }

  /** This only exists to give tests access to batcherFactory for flushing purposes. */
  static class BatchingCreateResult<RequestT, ResponseT> {
    BatcherFactory<RequestT, ResponseT> batcherFactory;
    UnaryCallable<RequestT, ResponseT> unaryCallable;

    BatchingCreateResult(
        BatcherFactory<RequestT, ResponseT> batcherFactory,
        UnaryCallable<RequestT, ResponseT> unaryCallable) {
      this.batcherFactory = batcherFactory;
      this.unaryCallable = unaryCallable;
    }
  }

  static <RequestT, ResponseT> BatchingCreateResult<RequestT, ResponseT> internalCreate(
      UnaryCallable<RequestT, ResponseT> directCallable,
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings,
      ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> callable =
        createBaseCallable(directCallable, batchingCallSettings, clientContext);
    BatcherFactory<RequestT, ResponseT> batcherFactory =
        new BatcherFactory<>(
            batchingCallSettings.getBatchingDescriptor(),
            batchingCallSettings.getBatchingSettings(),
            clientContext.getExecutor(),
            batchingCallSettings.getFlowController());
    callable =
        new BatchingCallable<>(
            callable, batchingCallSettings.getBatchingDescriptor(), batcherFactory);
    callable =
        new EntryPointUnaryCallable<>(
            callable, GrpcCallContext.createDefault(), getCallContextEnhancers(clientContext));
    return new BatchingCreateResult<>(batcherFactory, callable);
  }

  /**
   * Creates a callable object that represents a long-running operation. Designed for use by
   * generated code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param operationCallSettings {@link OperationCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param operationsStub {@link OperationsClient} to use to poll for updates on the Operation.
   * @return {@link com.google.api.gax.rpc.OperationCallable} callable object.
   */
  public static <RequestT, ResponseT extends Message, MetadataT extends Message>
      OperationCallable<RequestT, ResponseT, MetadataT, Operation> create(
          UnaryCallable<RequestT, Operation> directCallable,
          OperationCallSettings<RequestT, ResponseT, MetadataT, Operation> operationCallSettings,
          ClientContext clientContext,
          OperationsStub operationsStub) {
    GrpcOperationCallableImpl<RequestT, ResponseT, MetadataT> callableImpl =
        createImpl(directCallable, operationCallSettings, clientContext, operationsStub);
    return new EntryPointOperationCallable<>(
        callableImpl, GrpcCallContext.createDefault(), getCallContextEnhancers(clientContext));
  }

  static <RequestT, ResponseT extends Message, MetadataT extends Message>
      GrpcOperationCallableImpl<RequestT, ResponseT, MetadataT> createImpl(
          UnaryCallable<RequestT, Operation> directCallable,
          OperationCallSettings<RequestT, ResponseT, MetadataT, Operation> operationCallSettings,
          ClientContext clientContext,
          OperationsStub operationsStub) {

    UnaryCallable<RequestT, Operation> initialCallable =
        GrpcCallableFactory.createBaseCallable(
            directCallable, operationCallSettings.getInitialCallSettings(), clientContext);

    RetryAlgorithm<Operation> pollingAlgorithm =
        new RetryAlgorithm<>(
            new OperationResponsePollAlgorithm(), operationCallSettings.getPollingAlgorithm());
    ScheduledRetryingExecutor<Operation> scheduler =
        new ScheduledRetryingExecutor<>(pollingAlgorithm, clientContext.getExecutor());

    return new GrpcOperationCallableImpl<>(
        initialCallable, clientContext, scheduler, operationsStub, operationCallSettings);
  }

  /**
   * Create a callable object that represents a streaming API method. Designed for use by generated
   * code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param streamingCallSettings {@link StreamingCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link StreamingCallable} callable object.
   */
  public static <RequestT, ResponseT> StreamingCallable<RequestT, ResponseT> create(
      StreamingCallable<RequestT, ResponseT> directCallable,
      StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
      ClientContext clientContext) {
    return new EntryPointStreamingCallable<>(
        directCallable, GrpcCallContext.createDefault(), getCallContextEnhancers(clientContext));
  }

  private static List<ApiCallContextEnhancer> getCallContextEnhancers(ClientContext clientContext) {
    List<ApiCallContextEnhancer> enhancers = new ArrayList<>();

    if (clientContext.getCredentials() != null) {
      enhancers.add(new GrpcAuthCallContextEnhancer(clientContext.getCredentials()));
    }
    if (isGrpc(clientContext)) {
      GrpcTransport transportContext = (GrpcTransport) clientContext.getTransportContext();
      enhancers.add(new GrpcChannelCallContextEnhancer(transportContext.getChannel()));
    }

    return enhancers;
  }

  private static Set<Code> getGrpcStatusCodes(Set<StatusCode> statusCodes) {
    Set<Status.Code> returnCodes = new HashSet<>();
    for (StatusCode code : statusCodes) {
      if (code instanceof GrpcStatusCode) {
        returnCodes.add(((GrpcStatusCode) code).getCode());
      }
    }
    return returnCodes;
  }

  private static boolean isGrpc(ClientContext context) {
    return context
        .getTransportContext()
        .getTransportName()
        .equals(GrpcTransport.getGrpcTransportName());
  }
}
