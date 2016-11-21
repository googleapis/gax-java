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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.protobuf.Message;
import io.grpc.Channel;
import java.util.concurrent.ScheduledExecutorService;

/**
 * An OperationCallable is an immutable object which is capable of initiating RPC calls to
 * long-running API methods and returning an OperationFuture to manage the polling of the
 * Operation and getting the response.
 */
public final class OperationCallable<RequestT, ResponseT extends Message> {
  private final UnaryCallable<RequestT, Operation> initialCallable;
  private final Channel channel;
  private final ScheduledExecutorService executor;
  private final OperationsClient operationsClient;
  private final Class<ResponseT> responseClass;
  private final OperationCallSettings settings;

  /** Package-private for internal use. */
  OperationCallable(
      UnaryCallable<RequestT, Operation> initialCallable,
      Channel channel,
      ScheduledExecutorService executor,
      OperationsClient operationsClient,
      Class<ResponseT> responseClass,
      OperationCallSettings settings) {
    this.initialCallable = Preconditions.checkNotNull(initialCallable);
    this.channel = channel;
    this.executor = executor;
    this.operationsClient = operationsClient;
    this.responseClass = responseClass;
    this.settings = settings;
  }

  /**
   * Create an OperationCallable with a bound channel. If a call is made without specifying a
   * channel, the {@code boundChannel} is used instead.
   *
   * <p>Package-private for internal use.
   */
  OperationCallable<RequestT, ResponseT> bind(Channel boundChannel) {
    return new OperationCallable<>(
        initialCallable, boundChannel, executor, operationsClient, responseClass, settings);
  }

  /**
   * Creates a callable object that represents a long-running operation. Public only for technical
   * reasons - for advanced usage
   *
   * @param operationCallSettings {@link com.google.api.gax.grpc.OperationCallSettings} to configure
   *     the method-level settings with.
   * @param channel {@link Channel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use to schedule polling work.
   * @param operationsClient {@link OperationsClient} to use to poll for updates on the Operation.
   * @return {@link com.google.api.gax.grpc.OperationCallable} callable object.
   */
  public static <RequestT, ResponseT extends Message> OperationCallable<RequestT, ResponseT> create(
      OperationCallSettings<RequestT, ResponseT> operationCallSettings,
      Channel channel,
      ScheduledExecutorService executor,
      OperationsClient operationsClient) {
    return operationCallSettings.createOperationCallable(channel, executor, operationsClient);
  }

  /**
   * Initiates an operation asynchronously. If the {@link io.grpc.Channel} encapsulated in the given
   * {@link com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound
   * either at construction time or using {@link #bind(Channel)}.
   *
   * @param request The request to initiate the operation.
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return {@link OperationFuture} for the call result
   */
  public OperationFuture<ResponseT> futureCall(RequestT request, CallContext context) {
    if (context.getChannel() == null) {
      context = context.withChannel(channel);
    }
    ListenableFuture<Operation> initialCallFuture = initialCallable.futureCall(request, context);
    OperationFuture<ResponseT> operationFuture =
        OperationFuture.create(operationsClient, initialCallFuture, executor, responseClass);
    return operationFuture;
  }

  /**
   * Same as {@link #futureCall(Object, CallContext)}, with null {@link io.grpc.Channel} and default
   * {@link io.grpc.CallOptions}.
   *
   * @param request The request to initiate the operation.
   * @return {@link com.google.common.util.concurrent.ListenableFuture} for the call result
   */
  public OperationFuture<ResponseT> futureCall(RequestT request) {
    return futureCall(request, CallContext.createDefault().withChannel(channel));
  }

  /**
   * Initiates an operation and polls for the final result.
   * If the {@link io.grpc.Channel} encapsulated in the given {@link
   * com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound, using
   * {@link #bind(Channel)}.
   *
   * @param request The request to initiate the operation.
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return the call result
   * @throws ApiException if there is any bad status in the response.
   * @throws UncheckedExecutionException if there is any other exception unrelated to bad status.
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
   * @throws UncheckedExecutionException if there is any other exception unrelated to bad status.
   */
  public ResponseT call(RequestT request) {
    return ApiExceptions.callAndTranslateApiException(futureCall(request));
  }

  /**
   * Creates a new {@link OperationFuture} to watch an operation that has been initiated
   * previously. Note: This is not type-safe at static time; the result type can only be checked
   * once the operation finishes.
   * @param operationName
   * @return
   */
  public OperationFuture<ResponseT> resumeFutureCall(String operationName) {
    ListenableFuture<Operation> getOperationFuture =
        operationsClient
            .getOperationCallable()
            .futureCall(GetOperationRequest.newBuilder().setName(operationName).build());
    OperationFuture<ResponseT> operationFuture =
        OperationFuture.create(operationsClient, getOperationFuture, executor, responseClass);
    return operationFuture;
  }
}
