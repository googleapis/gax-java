/*
 * Copyright 2017 Google LLC
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
package com.google.api.gax.rpc;

import com.google.api.core.BetaApi;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.longrunning.OperationResponsePollAlgorithm;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.retrying.ExponentialRetryAlgorithm;
import com.google.api.gax.retrying.RetryAlgorithm;
import com.google.api.gax.retrying.RetryingExecutor;
import com.google.api.gax.retrying.ScheduledRetryingExecutor;
import com.google.api.gax.retrying.StreamingRetryAlgorithm;

/**
 * Class with utility methods to create callable objects using provided settings.
 *
 * <p>The callable objects wrap a given direct callable with features like retry and exception
 * translation.
 */
@BetaApi
public class Callables {

  private Callables() {}

  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> retrying(
      UnaryCallable<RequestT, ResponseT> innerCallable,
      UnaryCallSettings<?, ?> callSettings,
      ClientContext clientContext) {

    if (callSettings.getRetryableCodes().isEmpty()) {
      return innerCallable;
    }

    RetryAlgorithm<ResponseT> retryAlgorithm =
        new RetryAlgorithm<>(
            new ApiResultRetryAlgorithm<ResponseT>(),
            new ExponentialRetryAlgorithm(
                callSettings.getRetrySettings(), clientContext.getClock()));
    RetryingExecutor<ResponseT> retryingExecutor =
        new ScheduledRetryingExecutor<>(retryAlgorithm, clientContext.getExecutor());
    return new RetryingCallable<>(
        clientContext.getDefaultCallContext(), innerCallable, retryingExecutor);
  }

  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  public static <RequestT, ResponseT> ServerStreamingCallable<RequestT, ResponseT> retrying(
      ServerStreamingCallable<RequestT, ResponseT> innerCallable,
      ServerStreamingCallSettings<RequestT, ResponseT> callSettings,
      ClientContext clientContext) {

    if (callSettings.getRetryableCodes().isEmpty()) {
      return innerCallable;
    }

    StreamingRetryAlgorithm<Void> retryAlgorithm =
        new StreamingRetryAlgorithm<>(
            new ApiResultRetryAlgorithm<Void>(),
            new ExponentialRetryAlgorithm(
                callSettings.getRetrySettings(), clientContext.getClock()));

    ScheduledRetryingExecutor<Void> retryingExecutor =
        new ScheduledRetryingExecutor<>(retryAlgorithm, clientContext.getExecutor());

    return new RetryingServerStreamingCallable<>(
        innerCallable, retryingExecutor, callSettings.getResumptionStrategy());
  }

  @BetaApi("The surface for streaming is not stable yet and may change in the future.")
  public static <RequestT, ResponseT> ServerStreamingCallable<RequestT, ResponseT> watched(
      ServerStreamingCallable<RequestT, ResponseT> callable,
      ServerStreamingCallSettings<RequestT, ResponseT> callSettings,
      ClientContext clientContext) {

    callable = new WatchdogServerStreamingCallable<>(callable, clientContext.getStreamWatchdog());

    callable =
        callable.withDefaultCallContext(
            clientContext
                .getDefaultCallContext()
                .withStreamIdleTimeout(callSettings.getIdleTimeout()));

    return callable;
  }

  /**
   * Create a callable object that represents a batching API method. Designed for use by generated
   * code.
   *
   * @param innerCallable the callable to issue calls
   * @param batchingCallSettings {@link BatchingSettings} to configure the batching related settings
   *     with.
   * @param context {@link ClientContext} to use to connect to the service.
   * @return {@link UnaryCallable} callable object.
   */
  @BetaApi("The surface for batching is not stable yet and may change in the future.")
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> batching(
      UnaryCallable<RequestT, ResponseT> innerCallable,
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings,
      ClientContext context) {
    return batchingImpl(innerCallable, batchingCallSettings, context).unaryCallable;
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

  static <RequestT, ResponseT> BatchingCreateResult<RequestT, ResponseT> batchingImpl(
      UnaryCallable<RequestT, ResponseT> innerCallable,
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings,
      ClientContext clientContext) {
    BatcherFactory<RequestT, ResponseT> batcherFactory =
        new BatcherFactory<>(
            batchingCallSettings.getBatchingDescriptor(),
            batchingCallSettings.getBatchingSettings(),
            clientContext.getExecutor(),
            batchingCallSettings.getFlowController());
    UnaryCallable<RequestT, ResponseT> callable =
        new BatchingCallable<>(
            innerCallable, batchingCallSettings.getBatchingDescriptor(), batcherFactory);
    return new BatchingCreateResult<>(batcherFactory, callable);
  }

  /**
   * Create a paged callable object that represents a paged API method. Designed for use by
   * generated code.
   *
   * @param innerCallable the callable to issue calls
   * @param pagedCallSettings {@link PagedCallSettings} to configure the paged settings with.
   * @return {@link UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, PagedListResponseT> paged(
          UnaryCallable<RequestT, ResponseT> innerCallable,
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> pagedCallSettings) {
    return new PagedCallable<>(innerCallable, pagedCallSettings.getPagedListResponseFactory());
  }

  /**
   * Creates a callable object that represents a long-running operation. Designed for use by
   * generated code.
   *
   * @param initialCallable the callable that initiates the operation
   * @param operationCallSettings {@link OperationCallSettings} to configure the method-level
   *     settings with.
   * @param clientContext {@link ClientContext} to use to connect to the service.
   * @param longRunningClient {@link LongRunningClient} to use to poll for updates on the Operation.
   * @return {@link OperationCallable} callable object.
   */
  public static <RequestT, ResponseT, MetadataT>
      OperationCallable<RequestT, ResponseT, MetadataT> longRunningOperation(
          UnaryCallable<RequestT, OperationSnapshot> initialCallable,
          OperationCallSettings<RequestT, ResponseT, MetadataT> operationCallSettings,
          ClientContext clientContext,
          LongRunningClient longRunningClient) {
    return longRunningOperationImpl(
        initialCallable, operationCallSettings, clientContext, longRunningClient);
  }

  static <RequestT, ResponseT, MetadataT>
      OperationCallableImpl<RequestT, ResponseT, MetadataT> longRunningOperationImpl(
          UnaryCallable<RequestT, OperationSnapshot> initialCallable,
          OperationCallSettings<RequestT, ResponseT, MetadataT> operationCallSettings,
          ClientContext clientContext,
          LongRunningClient longRunningClient) {
    RetryAlgorithm<OperationSnapshot> pollingAlgorithm =
        new RetryAlgorithm<>(
            new OperationResponsePollAlgorithm(), operationCallSettings.getPollingAlgorithm());
    ScheduledRetryingExecutor<OperationSnapshot> scheduler =
        new ScheduledRetryingExecutor<>(pollingAlgorithm, clientContext.getExecutor());

    return new OperationCallableImpl<>(
        initialCallable, scheduler, longRunningClient, operationCallSettings);
  }
}
