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
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.BatchingCallSettings;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.CallableFactory;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.rpc.EmptyRequestParamsExtractor;
import com.google.api.gax.rpc.LongRunningClient;
import com.google.api.gax.rpc.OperationCallSettings;
import com.google.api.gax.rpc.OperationCallable;
import com.google.api.gax.rpc.PagedCallSettings;
import com.google.api.gax.rpc.RequestParamsExtractor;
import com.google.api.gax.rpc.RequestUrlParamsEncoder;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.SimpleCallSettings;
import com.google.api.gax.rpc.StreamingCallSettings;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallSettingsTyped;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.longrunning.Operation;
import com.google.longrunning.stub.OperationsStub;

/** Class with utility methods to create grpc-based direct callables. */
@BetaApi
public class GrpcCallableFactory {
  private static CallableFactory callableFactory = CallableFactory.of();

  private GrpcCallableFactory() {}

  private static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createBaseUnaryCallable(
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
      UnaryCallSettings callSettings,
      ClientContext clientContext,
      RequestParamsExtractor<RequestT> paramsExtractor) {
    UnaryCallable<RequestT, ResponseT> callable =
        new GrpcDirectCallable<>(
            grpcCallSettings.getMethodDescriptor(),
            new RequestUrlParamsEncoder<>(paramsExtractor, false));
    callable = new GrpcExceptionCallable<>(callable, callSettings.getRetryableCodes());
    callable = callableFactory.withRetry(callable, callSettings, clientContext);
    return callable;
  }

  /**
   * Create a callable object with grpc-specific functionality. Designed for use by generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param simpleCallSettings {@link SimpleCallSettings} to configure the method-level settings
   *     with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createUnaryCallable(
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
      SimpleCallSettings<RequestT, ResponseT> simpleCallSettings,
      ClientContext clientContext) {
    return createUnaryCallable(
        grpcCallSettings,
        simpleCallSettings,
        clientContext,
        EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a callable object with grpc-specific functionality. Designed for use by generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   */
  @BetaApi
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createUnaryCallable(
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
      UnaryCallSettingsTyped<RequestT, ResponseT> simpleCallSettings,
      ClientContext clientContext,
      RequestParamsExtractor<RequestT> paramsExtractor) {
    UnaryCallable<RequestT, ResponseT> callable =
        createBaseUnaryCallable(
            grpcCallSettings, simpleCallSettings, clientContext, paramsExtractor);
    return callable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }

  /**
   * Create a callable object that represents a simple call to a paged API method. Designed for use
   * by generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param pagedCallSettings {@link PagedCallSettings} to configure the method-level settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, ResponseT> createUnpagedCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings,
          ClientContext clientContext) {
    return createUnpagedCallable(
        grpcCallSettings,
        pagedCallSettings,
        clientContext,
        EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a callable object that represents a simple call to a paged API method. Designed for use
   * by generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param pagedCallSettings {@link PagedCallSettings} to configure the method-level settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, ResponseT> createUnpagedCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings,
          ClientContext clientContext,
          RequestParamsExtractor<RequestT> paramsExtractor) {
    UnaryCallable<RequestT, ResponseT> callable =
        createBaseUnaryCallable(
            grpcCallSettings, pagedCallSettings, clientContext, paramsExtractor);
    return callable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }

  /**
   * Create a paged callable object that represents a paged API method. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param pagedCallSettings {@link PagedCallSettings} to configure the paged settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, PagedListResponseT> createPagedCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings,
          ClientContext clientContext) {
    return createPagedCallable(
        grpcCallSettings,
        pagedCallSettings,
        clientContext,
        EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a paged callable object that represents a paged API method. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param pagedCallSettings {@link PagedCallSettings} to configure the paged settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, PagedListResponseT> createPagedCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings,
          ClientContext clientContext,
          RequestParamsExtractor<RequestT> paramsExtractor) {
    UnaryCallable<RequestT, ResponseT> innerCallable =
        createBaseUnaryCallable(
            grpcCallSettings, pagedCallSettings, clientContext, paramsExtractor);
    UnaryCallable<RequestT, PagedListResponseT> pagedCallable =
        callableFactory.asPagedVariant(innerCallable, pagedCallSettings);
    return pagedCallable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }

  /**
   * Create a callable object that represents a batching API method. Designed for use by generated
   * code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param batchingCallSettings {@link BatchingCallSettings} to configure the batching related
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createBatchingCallable(
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings,
      ClientContext clientContext) {
    return createBatchingCallable(
        grpcCallSettings,
        batchingCallSettings,
        clientContext,
        EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a callable object that represents a batching API method. Designed for use by generated
   * code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param batchingCallSettings {@link BatchingCallSettings} to configure the batching related
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createBatchingCallable(
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings,
      ClientContext clientContext,
      RequestParamsExtractor<RequestT> paramsExtractor) {
    UnaryCallable<RequestT, ResponseT> callable =
        createBaseUnaryCallable(
            grpcCallSettings, batchingCallSettings, clientContext, paramsExtractor);
    callable = callableFactory.withBatching(callable, batchingCallSettings, clientContext);
    return callable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }

  /**
   * Creates a callable object that represents a long-running operation. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param operationCallSettings {@link OperationCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param operationsStub {@link OperationsStub} to use to poll for updates on the Operation.
   * @return {@link com.google.api.gax.rpc.OperationCallable} callable object.
   */
  public static <RequestT, ResponseT, MetadataT>
      OperationCallable<RequestT, ResponseT, MetadataT> createOperationCallable(
          GrpcCallSettings<RequestT, Operation> grpcCallSettings,
          OperationCallSettings<RequestT, ResponseT, MetadataT> operationCallSettings,
          ClientContext clientContext,
          OperationsStub operationsStub) {
    return createOperationCallable(
        grpcCallSettings,
        operationCallSettings,
        clientContext,
        operationsStub,
        EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Creates a callable object that represents a long-running operation. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param operationCallSettings {@link OperationCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param operationsStub {@link OperationsStub} to use to poll for updates on the Operation.
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   * @return {@link com.google.api.gax.rpc.OperationCallable} callable object.
   */
  public static <RequestT, ResponseT, MetadataT>
      OperationCallable<RequestT, ResponseT, MetadataT> createOperationCallable(
          GrpcCallSettings<RequestT, Operation> grpcCallSettings,
          OperationCallSettings<RequestT, ResponseT, MetadataT> operationCallSettings,
          ClientContext clientContext,
          OperationsStub operationsStub,
          RequestParamsExtractor<RequestT> paramsExtractor) {
    UnaryCallable<RequestT, Operation> initialGrpcCallable =
        createBaseUnaryCallable(
            grpcCallSettings,
            operationCallSettings.getInitialCallSettings(),
            clientContext,
            paramsExtractor);
    UnaryCallable<RequestT, OperationSnapshot> initialCallable =
        new GrpcOperationSnapshotCallable<>(initialGrpcCallable);
    LongRunningClient longRunningClient = new GrpcLongRunningClient(operationsStub);
    OperationCallable<RequestT, ResponseT, MetadataT> operationCallable =
        callableFactory.asLongRunningOperation(
            initialCallable, operationCallSettings, clientContext, longRunningClient);
    return operationCallable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }

  /**
   * Create a bidirectional streaming callable object with grpc-specific functionality. Designed for
   * use by generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param streamingCallSettings {@link StreamingCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link BidiStreamingCallable} callable object.
   */
  @BetaApi
  public static <RequestT, ResponseT>
      BidiStreamingCallable<RequestT, ResponseT> createBidiStreamingCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
          ClientContext clientContext) {
    BidiStreamingCallable<RequestT, ResponseT> callable =
        new GrpcDirectBidiStreamingCallable<>(grpcCallSettings.getMethodDescriptor());
    return callable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }

  /**
   * Create a server-streaming callable with grpc-specific functionality. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param streamingCallSettings {@link StreamingCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link ServerStreamingCallable} callable object.
   */
  @BetaApi
  public static <RequestT, ResponseT>
      ServerStreamingCallable<RequestT, ResponseT> createServerStreamingCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
          ClientContext clientContext) {
    return createServerStreamingCallable(
        grpcCallSettings,
        streamingCallSettings,
        clientContext,
        EmptyRequestParamsExtractor.<RequestT>of());
  }

  /**
   * Create a server-streaming callable with grpc-specific functionality. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param streamingCallSettings {@link StreamingCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param paramsExtractor request message parameters extractor, which will be used to populate
   *     routing headers
   */
  @BetaApi
  public static <RequestT, ResponseT>
      ServerStreamingCallable<RequestT, ResponseT> createServerStreamingCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
          ClientContext clientContext,
          RequestParamsExtractor<RequestT> paramsExtractor) {
    ServerStreamingCallable<RequestT, ResponseT> callable =
        new GrpcDirectServerStreamingCallable<>(
            grpcCallSettings.getMethodDescriptor(),
            new RequestUrlParamsEncoder<>(paramsExtractor, false));
    return callable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }

  /**
   * Create a client-streaming callable object with grpc-specific functionality. Designed for use by
   * generated code.
   *
   * @param grpcCallSettings the gRPC call settings
   * @param streamingCallSettings {@link StreamingCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link ClientStreamingCallable} callable object.
   */
  @BetaApi
  public static <RequestT, ResponseT>
      ClientStreamingCallable<RequestT, ResponseT> createClientStreamingCallable(
          GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
          StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
          ClientContext clientContext) {
    ClientStreamingCallable<RequestT, ResponseT> callable =
        new GrpcDirectClientStreamingCallable<>(grpcCallSettings.getMethodDescriptor());
    return callable.withDefaultCallContext(clientContext.getDefaultCallContext());
  }
}
