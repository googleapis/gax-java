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

import com.google.api.core.BetaApi;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.longrunning.OperationResponsePollAlgorithm;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.retrying.ExponentialRetryAlgorithm;
import com.google.api.gax.retrying.RetryAlgorithm;
import com.google.api.gax.retrying.RetryingExecutor;
import com.google.api.gax.retrying.ScheduledRetryingExecutor;
import java.util.ArrayList;
import java.util.List;

/**
 * Class with utility methods to create callable objects using provided settings.
 *
 * <p>The callable objects wrap a given direct callable with features like retry and exception
 * translation.
 */
@BetaApi
public class CallableFactory {

  private final TransportDescriptor transportDescriptor;

  protected CallableFactory(TransportDescriptor transportDescriptor) {
    this.transportDescriptor = transportDescriptor;
  }

  public static CallableFactory create(TransportDescriptor transportDescriptor) {
    return new CallableFactory(transportDescriptor);
  }

  private <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createBaseCallable(
      UnaryCallable<RequestT, ResponseT> directCallable,
      UnaryCallSettingsTyped<RequestT, ResponseT> callSettings,
      ClientContext clientContext) {

    UnaryCallable<RequestT, ResponseT> callable =
        new ExceptionCallable<>(
            transportDescriptor, directCallable, callSettings.getRetryableCodes());
    RetryAlgorithm<ResponseT> retryAlgorithm =
        new RetryAlgorithm<>(
            new ApiResultRetryAlgorithm<ResponseT>(),
            new ExponentialRetryAlgorithm(
                callSettings.getRetrySettings(), clientContext.getClock()));
    RetryingExecutor<ResponseT> retryingExecutor =
        new ScheduledRetryingExecutor<>(retryAlgorithm, clientContext.getExecutor());
    return new RetryingCallable<>(transportDescriptor, callable, retryingExecutor);
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
  public <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> create(
      UnaryCallable<RequestT, ResponseT> directCallable,
      SimpleCallSettings<RequestT, ResponseT> simpleCallSettings,
      ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> unaryCallable =
        createBaseCallable(directCallable, simpleCallSettings, clientContext);
    return new EntryPointUnaryCallable<>(
        unaryCallable,
        transportDescriptor.createDefaultCallContext(),
        getCallContextEnhancers(clientContext));
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
  public <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, PagedListResponseT> createPagedVariant(
          UnaryCallable<RequestT, ResponseT> directCallable,
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings,
          ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> unaryCallable =
        createBaseCallable(directCallable, pagedCallSettings, clientContext);
    UnaryCallable<RequestT, PagedListResponseT> pagedCallable =
        new PagedCallable<>(unaryCallable, pagedCallSettings.getPagedListResponseFactory());
    return new EntryPointUnaryCallable<>(
        pagedCallable,
        transportDescriptor.createDefaultCallContext(),
        getCallContextEnhancers(clientContext));
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
  public <RequestT, ResponseT, PagedListResponseT> UnaryCallable<RequestT, ResponseT> create(
      UnaryCallable<RequestT, ResponseT> directCallable,
      PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings,
      ClientContext clientContext) {
    UnaryCallable<RequestT, ResponseT> unaryCallable =
        createBaseCallable(directCallable, pagedCallSettings, clientContext);
    return new EntryPointUnaryCallable<>(
        unaryCallable,
        transportDescriptor.createDefaultCallContext(),
        getCallContextEnhancers(clientContext));
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
  public <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> create(
      UnaryCallable<RequestT, ResponseT> directCallable,
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings,
      ClientContext context) {
    return internalCreate(directCallable, batchingCallSettings, context).unaryCallable;
  }

  /** This only exists to give tests access to batcherFactory for flushing purposes. */
  static class BatchingCreateResult<RequestT, ResponseT> {
    private final BatcherFactory<RequestT, ResponseT> batcherFactory;
    private final UnaryCallable<RequestT, ResponseT> unaryCallable;

    private BatchingCreateResult(
        BatcherFactory<RequestT, ResponseT> batcherFactory,
        UnaryCallable<RequestT, ResponseT> unaryCallable) {
      this.batcherFactory = batcherFactory;
      this.unaryCallable = unaryCallable;
    }

    public BatcherFactory<RequestT, ResponseT> getBatcherFactory() {
      return batcherFactory;
    }

    public UnaryCallable<RequestT, ResponseT> getUnaryCallable() {
      return unaryCallable;
    }
  }

  <RequestT, ResponseT> BatchingCreateResult<RequestT, ResponseT> internalCreate(
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
            callable,
            transportDescriptor.createDefaultCallContext(),
            getCallContextEnhancers(clientContext));
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
   * @param longRunningClient {@link LongRunningClient} to use to poll for updates on the Operation.
   * @return {@link OperationCallable} callable object.
   */
  public <RequestT, ResponseT, MetadataT> OperationCallable<RequestT, ResponseT, MetadataT> create(
      UnaryCallable<RequestT, OperationSnapshot> directCallable,
      OperationCallSettings<RequestT, ResponseT, MetadataT> operationCallSettings,
      ClientContext clientContext,
      LongRunningClient longRunningClient) {
    OperationCallable<RequestT, ResponseT, MetadataT> callableImpl =
        createImpl(directCallable, operationCallSettings, clientContext, longRunningClient);
    return new EntryPointOperationCallable<>(
        callableImpl,
        transportDescriptor.createDefaultCallContext(),
        getCallContextEnhancers(clientContext));
  }

  <RequestT, ResponseT, MetadataT> OperationCallableImpl<RequestT, ResponseT, MetadataT> createImpl(
      UnaryCallable<RequestT, OperationSnapshot> directCallable,
      OperationCallSettings<RequestT, ResponseT, MetadataT> operationCallSettings,
      ClientContext clientContext,
      LongRunningClient longRunningClient) {

    UnaryCallable<RequestT, OperationSnapshot> initialCallable =
        createBaseCallable(
            directCallable, operationCallSettings.getInitialCallSettings(), clientContext);

    RetryAlgorithm<OperationSnapshot> pollingAlgorithm =
        new RetryAlgorithm<>(
            new OperationResponsePollAlgorithm(), operationCallSettings.getPollingAlgorithm());
    ScheduledRetryingExecutor<OperationSnapshot> scheduler =
        new ScheduledRetryingExecutor<>(pollingAlgorithm, clientContext.getExecutor());

    return new OperationCallableImpl<>(
        transportDescriptor, initialCallable, scheduler, longRunningClient, operationCallSettings);
  }

  /**
   * Create a callable object that represents a bidirectional streaming API method. Designed for use
   * by generated code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param streamingCallSettings {@link StreamingCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link BidiStreamingCallable} callable object.
   */
  public <RequestT, ResponseT> BidiStreamingCallable<RequestT, ResponseT> create(
      BidiStreamingCallable<RequestT, ResponseT> directCallable,
      StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
      ClientContext clientContext) {
    return new EntryPointBidiStreamingCallable<>(
        directCallable,
        transportDescriptor.createDefaultCallContext(),
        getCallContextEnhancers(clientContext));
  }

  /**
   * Create a callable object that represents a server streaming API method. Designed for use by
   * generated code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param streamingCallSettings {@link StreamingCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link ServerStreamingCallable} callable object.
   */
  public <RequestT, ResponseT> ServerStreamingCallable<RequestT, ResponseT> create(
      ServerStreamingCallable<RequestT, ResponseT> directCallable,
      StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
      ClientContext clientContext) {
    return new EntryPointServerStreamingCallable<>(
        directCallable,
        transportDescriptor.createDefaultCallContext(),
        getCallContextEnhancers(clientContext));
  }

  /**
   * Create a callable object that represents a client streaming API method. Designed for use by
   * generated code.
   *
   * @param directCallable the callable that directly issues the call to the underlying API
   * @param streamingCallSettings {@link StreamingCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @return {@link ClientStreamingCallable} callable object.
   */
  public <RequestT, ResponseT> ClientStreamingCallable<RequestT, ResponseT> create(
      ClientStreamingCallable<RequestT, ResponseT> directCallable,
      StreamingCallSettings<RequestT, ResponseT> streamingCallSettings,
      ClientContext clientContext) {
    return new EntryPointClientStreamingCallable<>(
        directCallable,
        transportDescriptor.createDefaultCallContext(),
        getCallContextEnhancers(clientContext));
  }

  private List<ApiCallContextEnhancer> getCallContextEnhancers(ClientContext clientContext) {
    List<ApiCallContextEnhancer> enhancers = new ArrayList<>();

    if (clientContext.getCredentials() != null) {
      enhancers.add(transportDescriptor.getAuthCallContextEnhancer(clientContext.getCredentials()));
    }

    TransportChannel transportChannel = clientContext.getTransportChannel();
    if (transportChannel != null) {
      enhancers.add(transportDescriptor.getChannelCallContextEnhancer(transportChannel));
    }

    return enhancers;
  }
}
