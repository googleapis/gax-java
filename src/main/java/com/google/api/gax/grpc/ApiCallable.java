/*
 * Copyright 2015, Google Inc.
 * All rights reserved.
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

import com.google.api.gax.core.RetrySettings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.Channel;
import io.grpc.ExperimentalApi;
import io.grpc.Status;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nullable;

/**
 * An ApiCallable is an object which represents one or more rpc calls.
 *
 * Whereas java.util.concurrent.Callable encapsulates all of the data necessary for a call,
 * ApiCallable allows incremental addition of inputs, configuration, and behavior through
 * decoration. In typical usage, the request to send to the remote service will not be bound
 * to the ApiCallable, but instead is provided at call time, which allows for an ApiCallable
 * to be saved and used indefinitely.
 *
 * The order of decoration matters. For example, if retrying is added before page streaming,
 * then RPC failures will only cause a retry of the failed RPC; if retrying is added after
 * page streaming, then a failure will cause the whole page stream to be retried.
 *
 * As an alternative to the decoration approach, an ApiCallable can be created
 * using ApiCallSettings, which allows for the inputs and configuration to be provided in
 * any order, and the final ApiCallable is built through decoration in a predefined order.
 */
public class ApiCallable<RequestT, ResponseT> {

  private final FutureCallable<RequestT, ResponseT> callable;

  @Nullable
  private final ApiCallSettings settings;

  /**
   * Create a callable object that represents a simple API method.
   * Public only for technical reasons - for advanced usage
   *
   * @param simpleCallSettings {@link com.google.api.gax.grpc.SimpleCallSettings} to configure
   * the method-level settings with.
   * @param serviceSettings{@link com.google.api.gax.grpc.ServiceApiSettings}
   * to configure the service-level settings with.
   * @return {@link com.google.api.gax.grpc.ApiCallable} callable object.
   */
  public static <RequestT, ResponseT> ApiCallable<RequestT, ResponseT> create(
      SimpleCallSettings<RequestT, ResponseT> simpleCallSettings,
      ServiceApiSettings serviceSettings) throws IOException {
     return simpleCallSettings.create(serviceSettings);
  }

  /**
   * Create a callable object that represents a page-streaming API method.
   * Public only for technical reasons - for advanced usage
   *
   * @param pageStreamingCallSettings {@link com.google.api.gax.grpc.PageStreamingCallSettings} to
   * configure the page-streaming related settings with.
   * @param serviceSettings{@link com.google.api.gax.grpc.ServiceApiSettings}
   * to configure the service-level settings with.
   * @return {@link com.google.api.gax.grpc.ApiCallable} callable object.
   */
  public static <RequestT, ResponseT, ResourceT>
      ApiCallable<RequestT, Iterable<ResourceT>> create(
          PageStreamingCallSettings<RequestT, ResponseT, ResourceT> pageStreamingCallSettings,
          ServiceApiSettings serviceSettings) throws IOException {
    return pageStreamingCallSettings.create(serviceSettings);
  }

  /**
   * Create a callable object that represents a bundling API method.
   * Public only for technical reasons - for advanced usage
   *
   * @param bundlingCallSettings {@link com.google.api.gax.grpc.BundlingSettings} to configure
   * the bundling related settings with.
   * @param serviceSettings{@link com.google.api.gax.grpc.ServiceApiSettings}
   * to configure the service-level settings with.
   * @return {@link com.google.api.gax.grpc.ApiCallable} callable object.
   */
  public static <RequestT, ResponseT> ApiCallable<RequestT, ResponseT> create(
      BundlingCallSettings<RequestT, ResponseT> bundlingCallSettings,
      ServiceApiSettings serviceSettings) throws IOException {
    return bundlingCallSettings.create(serviceSettings);
  }

  /**
   * Creates a callable object which uses the given {@link FutureCallable}.
   *
   * @param futureCallable {@link FutureCallable} to wrap
   * the bundling related settings with.
   * @return {@link com.google.api.gax.grpc.ApiCallable} callable object.
   *
   * Package-private for internal usage.
   */
  static <ReqT, RespT> ApiCallable<ReqT, RespT> create(
      FutureCallable<ReqT, RespT> futureCallable) {
    return new ApiCallable<ReqT, RespT>(futureCallable);
  }

  /**
   * Returns the {@link ApiCallSettings} that contains the configuration settings of this
   * ApiCallable.
   *
   * Package-private for internal usage.
   */
  public ApiCallSettings getSettings() {
    return settings;
  }

  /**
   * Package-private for internal use.
   */
  ApiCallable(FutureCallable<RequestT, ResponseT> callable, ApiCallSettings settings) {
    this.callable = Preconditions.checkNotNull(callable);
    this.settings = settings;
  }

  /**
   * Package-private for internal use.
   */
  ApiCallable(FutureCallable<RequestT, ResponseT> callable) {
    this(callable, null);
  }

  /**
   * Perform a call asynchronously. If the {@link io.grpc.Channel} encapsulated in the given
   * {@link com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound,
   * using {@link #bind(Channel)}.
   *
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return {@link com.google.common.util.concurrent.ListenableFuture} for the call result
   */
  public ListenableFuture<ResponseT> futureCall(CallContext<RequestT> context) {
    return callable.futureCall(context);
  }

  /**
   * Same as {@link #futureCall(CallContext)}, with null {@link io.grpc.Channel} and
   * default {@link io.grpc.CallOptions}.
   *
   * @param request request
   * @return {@link com.google.common.util.concurrent.ListenableFuture} for the call result
   */
  public ListenableFuture<ResponseT> futureCall(RequestT request) {
    return futureCall(CallContext.<RequestT>of(request));
  }

  /**
   * Perform a call synchronously. If the {@link io.grpc.Channel} encapsulated in the given
   * {@link com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound,
   * using {@link #bind(Channel)}.
   *
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return the call result
   */
  public ResponseT call(CallContext<RequestT> context) {
    return Futures.getUnchecked(futureCall(context));
  }

  /**
   * Same as {@link #call(CallContext)}, with null {@link io.grpc.Channel} and
   * default {@link io.grpc.CallOptions}.
   *
   * @param request request
   * @return the call result
   */
  public ResponseT call(RequestT request) {
    return Futures.getUnchecked(futureCall(request));
  }

  /**
   * Create a callable with a bound channel. If a call is made without specifying a channel,
   * the {@code boundChannel} is used instead.
   */
  public ApiCallable<RequestT, ResponseT> bind(Channel boundChannel) {
    return new ApiCallable<RequestT, ResponseT>(
        new ChannelBindingCallable<RequestT, ResponseT>(callable, boundChannel), settings);
  }

  /**
   * Creates a callable whose calls raise {@link ApiException}
   * instead of the usual {@link io.grpc.StatusRuntimeException}.
   * The {@link ApiException} will consider failures with any of the given status codes
   * retryable.
   */
  public ApiCallable<RequestT, ResponseT> retryableOn(ImmutableSet<Status.Code> retryableCodes) {
    return new ApiCallable<RequestT, ResponseT>(
        new ExceptionTransformingCallable<>(callable, retryableCodes), settings);
  }

  /**
   * Creates a callable which retries using exponential back-off. Back-off parameters are defined
   * by the given {@code retryParams}.
   */
  public ApiCallable<RequestT, ResponseT> retrying(
      RetrySettings retrySettings, ScheduledExecutorService executor) {
    return retrying(retrySettings, executor, DefaultNanoClock.create());
  }

  /**
   * Creates a callable which retries using exponential back-off. Back-off parameters are defined
   * by the given {@code retryParams}. Clock provides a time source used for calculating
   * retry timeouts.
   */
  @VisibleForTesting
  ApiCallable<RequestT, ResponseT> retrying(
      RetrySettings retrySettings, ScheduledExecutorService executor, NanoClock clock) {
    return new ApiCallable<RequestT, ResponseT>(
        new RetryingCallable<RequestT, ResponseT>(callable, retrySettings, executor, clock));
  }

  /**
   * Returns a callable which streams the resources obtained from a series of calls to a method
   * implementing the pagination pattern.
   */
  public <ResourceT> ApiCallable<RequestT, Iterable<ResourceT>> pageStreaming(
      PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    return new ApiCallable<RequestT, Iterable<ResourceT>>(
        new PageStreamingCallable<RequestT, ResponseT, ResourceT>(callable, pageDescriptor),
        settings);
  }

  /**
   * Returns a callable which bundles the call, meaning that multiple requests are bundled
   * together and sent at the same time.
   */
  public ApiCallable<RequestT, ResponseT> bundling(
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlerFactory<RequestT, ResponseT> bundlerFactory) {
    return new ApiCallable<RequestT, ResponseT>(
        new BundlingCallable<RequestT, ResponseT>(callable, bundlingDescriptor, bundlerFactory),
        settings);
  }
}
