/*
 * Copyright 2016, Google Inc.
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

import com.google.api.gax.core.PagedListResponse;
import com.google.api.gax.core.RetrySettings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/**
 * An ApiCallable is an immutable object which is capable of making RPC calls to API methods.
 *
 * <p>Whereas java.util.concurrent.Callable encapsulates all of the data necessary for a call,
 * ApiCallable allows incremental addition of inputs, configuration, and behavior through
 * decoration. In typical usage, the request to send to the remote service will not be bound
 * to the ApiCallable, but instead is provided at call time, which allows for an ApiCallable
 * to be saved and used indefinitely.
 *
 * <p>The order of decoration matters. For example, if retrying is added before page streaming,
 * then RPC failures will only cause a retry of the failed RPC; if retrying is added after
 * page streaming, then a failure will cause the whole page stream to be retried.
 *
 * <p>As an alternative to creating an ApiCallable through decoration, all of the decorative
 * behavior of an ApiCallable can be specified by using ApiCallSettings. This allows for the
 * inputs and configuration to be provided in any order, and the final ApiCallable is built
 * through decoration in a predefined order.
 *
 * <p>It is considered advanced usage for a user to create an ApiCallable themselves. This class
 * is intended to be created by a generated service API wrapper class, and configured by
 * instances of ApiCallSettings.Builder which are exposed through the API wrapper class's
 * settings class.
 *
 * <p>There are two styles of calls that can be made through an ApiCallable: synchronous and
 * asynchronous.
 *
 * <p>Synchronous example:
 *
 * <pre>{@code
 * RequestType request = RequestType.newBuilder().build();
 * ApiCallable<RequestType, ResponseType> apiCallable = api.doSomethingCallable();
 * ResponseType response = apiCallable.call();
 * }</pre>
 *
 * <p>Asynchronous example:
 *
 * <pre>{@code
 * RequestType request = RequestType.newBuilder().build();
 * ApiCallable<RequestType, ResponseType> apiCallable = api.doSomethingCallable();
 * ListenableFuture<ResponseType> resultFuture = apiCallable.futureCall();
 * // do other work
 * // ...
 * ResponseType response = resultFuture.get();
 * }</pre>
 */
public final class ApiCallable<RequestT, ResponseT> {

  interface Scheduler {
    ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit);
  }

  static class DelegatingScheduler implements Scheduler {
    private final ScheduledExecutorService executor;

    DelegatingScheduler(ScheduledExecutorService executor) {
      this.executor = executor;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
      return executor.schedule(runnable, delay, unit);
    }
  }

  private final FutureCallable<RequestT, ResponseT> callable;

  @Nullable private final ApiCallSettings settings;

  /**
   * Create a callable object that represents a simple API method. Public only for technical reasons
   * - for advanced usage
   *
   * @param simpleCallSettings {@link com.google.api.gax.grpc.SimpleCallSettings} to configure the
   * method-level settings with.
   * @param channel {@link ManagedChannel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use when connecting to the service.
   * @return {@link com.google.api.gax.grpc.ApiCallable} callable object.
   */
  public static <RequestT, ResponseT> ApiCallable<RequestT, ResponseT> create(
      SimpleCallSettings<RequestT, ResponseT> simpleCallSettings,
      ManagedChannel channel,
      ScheduledExecutorService executor) {
    return simpleCallSettings.create(channel, executor);
  }

  /**
   * Create a paged callable object that represents a page-streaming API method. Public only for
   * technical reasons - for advanced usage
   *
   * @param pageStreamingCallSettings {@link com.google.api.gax.grpc.PageStreamingCallSettings} to
   *     configure the page-streaming related settings with.
   * @param channel {@link ManagedChannel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use to when connecting to the service.
   * @return {@link com.google.api.gax.grpc.ApiCallable} callable object.
   */
  public static <RequestT, ResponseT, ResourceT>
      ApiCallable<RequestT, PagedListResponse<RequestT, ResponseT, ResourceT>> createPagedVariant(
          PageStreamingCallSettings<RequestT, ResponseT, ResourceT> pageStreamingCallSettings,
          ManagedChannel channel,
          ScheduledExecutorService executor) {
    return pageStreamingCallSettings.createPagedVariant(channel, executor);
  }

  /**
   * Create a base callable object that represents a page-streaming API method. Public only for
   * technical reasons - for advanced usage
   *
   * @param pageStreamingCallSettings {@link com.google.api.gax.grpc.PageStreamingCallSettings} to
   * configure the page-streaming related settings with.
   * @param channel {@link ManagedChannel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use to when connecting to the service.
   * @return {@link com.google.api.gax.grpc.ApiCallable} callable object.
   */
  public static <RequestT, ResponseT, ResourceT> ApiCallable<RequestT, ResponseT> create(
      PageStreamingCallSettings<RequestT, ResponseT, ResourceT> pageStreamingCallSettings,
      ManagedChannel channel,
      ScheduledExecutorService executor) {
    return pageStreamingCallSettings.create(channel, executor);
  }

  /**
   * Create a callable object that represents a bundling API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param bundlingCallSettings {@link com.google.api.gax.grpc.BundlingSettings} to configure the
   * bundling related settings with.
   * @param channel {@link ManagedChannel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use to when connecting to the service.
   * @return {@link com.google.api.gax.grpc.ApiCallable} callable object.
   */
  public static <RequestT, ResponseT> ApiCallable<RequestT, ResponseT> create(
      BundlingCallSettings<RequestT, ResponseT> bundlingCallSettings,
      ManagedChannel channel,
      ScheduledExecutorService executor) {
    return bundlingCallSettings.create(channel, executor);
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
  static <ReqT, RespT> ApiCallable<ReqT, RespT> create(FutureCallable<ReqT, RespT> futureCallable) {
    return new ApiCallable<>(futureCallable);
  }

  /**
   * Returns the {@link ApiCallSettings} that contains the configuration settings of this
   * ApiCallable.
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
   * @throws ApiException if there is any bad status in the response.
   * @throws UncheckedExecutionException if there is any other exception unrelated to bad status.
   */
  public ResponseT call(CallContext<RequestT> context) {
    try {
      return Futures.getUnchecked(futureCall(context));
    } catch (UncheckedExecutionException exception) {
      Throwables.propagateIfInstanceOf(exception.getCause(), ApiException.class);
      if (exception.getCause() instanceof StatusRuntimeException) {
        StatusRuntimeException statusException = (StatusRuntimeException) exception.getCause();
        throw new ApiException(statusException, statusException.getStatus().getCode(), false);
      }
      throw exception;
    }
  }

  /**
   * Same as {@link #call(CallContext)}, with null {@link io.grpc.Channel} and
   * default {@link io.grpc.CallOptions}.
   *
   * @param request request
   * @return the call result
   * @throws ApiException if there is any bad status in the response.
   * @throws UncheckedExecutionException if there is any other exception unrelated to bad status.
   */
  public ResponseT call(RequestT request) {
    try {
      return Futures.getUnchecked(futureCall(request));
    } catch (UncheckedExecutionException exception) {
      Throwables.propagateIfInstanceOf(exception.getCause(), ApiException.class);
      if (exception.getCause() instanceof StatusRuntimeException) {
        StatusRuntimeException statusException = (StatusRuntimeException) exception.getCause();
        throw new ApiException(statusException, statusException.getStatus().getCode(), false);
      }
      throw exception;
    }
  }

  /**
   * Create a callable with a bound channel. If a call is made without specifying a channel, the
   * {@code boundChannel} is used instead.
   *
   * <p>Package-private for internal use.
   */
  ApiCallable<RequestT, ResponseT> bind(Channel boundChannel) {
    return new ApiCallable<>(new ChannelBindingCallable<>(callable, boundChannel), settings);
  }

  /**
   * Creates a callable whose calls raise {@link ApiException} instead of the usual {@link
   * io.grpc.StatusRuntimeException}. The {@link ApiException} will consider failures with any of
   * the given status codes retryable.
   *
   * <p>This decoration must be added to an ApiCallable before the "retrying" decoration which will
   * retry these codes.
   *
   * <p>Package-private for internal use.
   */
  ApiCallable<RequestT, ResponseT> retryableOn(ImmutableSet<Status.Code> retryableCodes) {
    return new ApiCallable<>(
        new ExceptionTransformingCallable<>(callable, retryableCodes), settings);
  }

  /**
   * Creates a callable which retries using exponential back-off. Back-off parameters are defined by
   * the given {@code retrySettings}.
   *
   * <p>This decoration will only retry if the ApiCallable has already been decorated with
   * "retryableOn" so that it throws an ApiException for the right codes.
   *
   * <p>Package-private for internal use.
   */
  ApiCallable<RequestT, ResponseT> retrying(
      RetrySettings retrySettings, ScheduledExecutorService executor) {
    return retrying(retrySettings, new DelegatingScheduler(executor), DefaultNanoClock.create());
  }

  /**
   * Creates a callable which retries using exponential back-off. Back-off parameters are defined by
   * the given {@code retrySettings}. Clock provides a time source used for calculating retry
   * timeouts.
   *
   * <p>Package-private for internal use.
   */
  @VisibleForTesting
  ApiCallable<RequestT, ResponseT> retrying(
      RetrySettings retrySettings, Scheduler executor, NanoClock clock) {
    return new ApiCallable<>(new RetryingCallable<>(callable, retrySettings, executor, clock));
  }

  /**
   * Returns a callable which streams the resources obtained from a series of calls to a method
   * implementing the page streaming pattern.
   *
   * <p>Package-private for internal use.
   */
  <ResourceT>
      ApiCallable<RequestT, PagedListResponse<RequestT, ResponseT, ResourceT>> pageStreaming(
          PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    return new ApiCallable<>(new PageStreamingCallable<>(callable, pageDescriptor), settings);
  }

  /**
   * Returns a callable which bundles the call, meaning that multiple requests are bundled together
   * and sent at the same time.
   *
   * <p>Package-private for internal use.
   */
  ApiCallable<RequestT, ResponseT> bundling(
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlerFactory<RequestT, ResponseT> bundlerFactory) {
    return new ApiCallable<>(
        new BundlingCallable<>(callable, bundlingDescriptor, bundlerFactory), settings);
  }
}
