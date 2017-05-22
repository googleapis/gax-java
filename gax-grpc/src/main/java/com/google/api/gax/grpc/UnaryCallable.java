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

import com.google.api.core.ApiClock;
import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.core.NanoClock;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.retrying.RetrySettings;
import com.google.auth.Credentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import io.grpc.Channel;
import io.grpc.Status;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;

/**
 * A UnaryCallable is an immutable object which is capable of making RPC calls to non-streaming API
 * methods.
 *
 * <p>Whereas java.util.concurrent.Callable encapsulates all of the data necessary for a call,
 * UnaryCallable allows incremental addition of inputs, configuration, and behavior through
 * decoration. In typical usage, the request to send to the remote service will not be bound to the
 * UnaryCallable, but instead is provided at call time, which allows for a UnaryCallable to be saved
 * and used indefinitely.
 *
 * <p>The order of decoration matters. For example, if RetryingCallable is added before
 * PagedCallable, then RPC failures will only cause a retry of the failed RPC; if RetryingCallable
 * is added after PagedCallable, then a failure will cause the whole page stream to be retried.
 *
 * <p>As an alternative to creating a UnaryCallable through decoration, all of the decorative
 * behavior of a UnaryCallable can be specified by using UnaryCallSettings. This allows for the
 * inputs and configuration to be provided in any order, and the final UnaryCallable is built
 * through decoration in a predefined order.
 *
 * <p>It is considered advanced usage for a user to create a UnaryCallable themselves. This class is
 * intended to be created by a generated service API wrapper class, and configured by instances of
 * UnaryCallSettings.Builder which are exposed through the API wrapper class's settings class.
 *
 * <p>There are two styles of calls that can be made through a UnaryCallable: synchronous and
 * asynchronous.
 *
 * <p>Synchronous example:
 *
 * <pre>{@code
 * RequestType request = RequestType.newBuilder().build();
 * UnaryCallable<RequestType, ResponseType> unaryCallable = api.doSomethingCallable();
 * ResponseType response = unaryCallable.call();
 * }</pre>
 *
 * <p>Asynchronous example:
 *
 * <pre>{@code
 * RequestType request = RequestType.newBuilder().build();
 * UnaryCallable<RequestType, ResponseType> unaryCallable = api.doSomethingCallable();
 * ApiFuture<ResponseType> resultFuture = unaryCallable.futureCall();
 * // do other work
 * // ...
 * ResponseType response = resultFuture.get();
 * }</pre>
 */
@BetaApi
public final class UnaryCallable<RequestT, ResponseT> {

  private final FutureCallable<RequestT, ResponseT> callable;
  private final Channel channel;
  @Nullable private final UnaryCallSettings settings;

  /**
   * Create a callable object that represents a simple API method. Public only for technical reasons
   * - for advanced usage
   *
   * @param simpleCallSettings {@link com.google.api.gax.grpc.SimpleCallSettings} to configure the
   *     method-level settings with.
   * @param context {@link ClientContext} to use to connect to the service.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> create(
      SimpleCallSettings<RequestT, ResponseT> simpleCallSettings, ClientContext context) {
    return simpleCallSettings.create(context);
  }

  /**
   * Create a callable object that represents a simple API method. Public only for technical reasons
   * - for advanced usage
   *
   * @param simpleCallSettings {@link com.google.api.gax.grpc.SimpleCallSettings} to configure the
   *     method-level settings with.
   * @param channel {@link Channel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use when connecting to the service.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   */
  @Deprecated
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> create(
      SimpleCallSettings<RequestT, ResponseT> simpleCallSettings,
      Channel channel,
      ScheduledExecutorService executor) {
    return simpleCallSettings.create(
        ClientContext.newBuilder().setChannel(channel).setExecutor(executor).build());
  }

  /**
   * Create a paged callable object that represents a paged API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param PagedCallSettings {@link com.google.api.gax.grpc.PagedCallSettings} to configure the
   *     paged settings with.
   * @param context {@link ClientContext} to use to connect to the service.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, PagedListResponseT> createPagedVariant(
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> PagedCallSettings,
          ClientContext context) {
    return PagedCallSettings.createPagedVariant(context);
  }

  /**
   * Create a paged callable object that represents a paged API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param PagedCallSettings {@link com.google.api.gax.grpc.PagedCallSettings} to configure the
   *     paged settings with.
   * @param channel {@link Channel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use to when connecting to the service.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   */
  @Deprecated
  public static <RequestT, ResponseT, PagedListResponseT>
      UnaryCallable<RequestT, PagedListResponseT> createPagedVariant(
          PagedCallSettings<RequestT, ResponseT, PagedListResponseT> PagedCallSettings,
          Channel channel,
          ScheduledExecutorService executor) {
    return PagedCallSettings.createPagedVariant(
        ClientContext.newBuilder().setChannel(channel).setExecutor(executor).build());
  }

  /**
   * Create a base callable object that represents a paged API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param PagedCallSettings {@link com.google.api.gax.grpc.PagedCallSettings} to configure the
   *     paged settings with.
   * @param context {@link ClientContext} to use to connect to the service.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT, PagedListResponseT> UnaryCallable<RequestT, ResponseT> create(
      PagedCallSettings<RequestT, ResponseT, PagedListResponseT> PagedCallSettings,
      ClientContext context) {
    return PagedCallSettings.create(context);
  }

  /**
   * Create a base callable object that represents a paged API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param PagedCallSettings {@link com.google.api.gax.grpc.PagedCallSettings} to configure the
   *     paged settings with.
   * @param channel {@link Channel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use to when connecting to the service.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   */
  @Deprecated
  public static <RequestT, ResponseT, PagedListResponseT> UnaryCallable<RequestT, ResponseT> create(
      PagedCallSettings<RequestT, ResponseT, PagedListResponseT> PagedCallSettings,
      Channel channel,
      ScheduledExecutorService executor) {
    return PagedCallSettings.create(
        ClientContext.newBuilder().setChannel(channel).setExecutor(executor).build());
  }

  /**
   * Create a callable object that represents a batching API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param batchingCallSettings {@link BatchingSettings} to configure the batching related settings
   *     with.
   * @param context {@link ClientContext} to use to connect to the service.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   */
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> create(
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings, ClientContext context) {
    return batchingCallSettings.create(context);
  }

  /**
   * Create a callable object that represents a batching API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param batchingCallSettings {@link BatchingSettings} to configure the batching related settings
   *     with.
   * @param channel {@link Channel} to use to connect to the service.
   * @param executor {@link ScheduledExecutorService} to use to when connecting to the service.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   */
  @Deprecated
  public static <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> create(
      BatchingCallSettings<RequestT, ResponseT> batchingCallSettings,
      Channel channel,
      ScheduledExecutorService executor) {
    return batchingCallSettings.create(
        ClientContext.newBuilder().setChannel(channel).setExecutor(executor).build());
  }

  /**
   * Creates a callable object which uses the given {@link FutureCallable}.
   *
   * @param futureCallable {@link FutureCallable} to wrap the batching related settings with.
   * @return {@link com.google.api.gax.grpc.UnaryCallable} callable object.
   *     <p>Package-private for internal usage.
   */
  static <ReqT, RespT> UnaryCallable<ReqT, RespT> create(
      FutureCallable<ReqT, RespT> futureCallable) {
    return new UnaryCallable<>(futureCallable);
  }

  /**
   * Returns the {@link UnaryCallSettings} that contains the configuration settings of this
   * UnaryCallable.
   */
  public UnaryCallSettings getSettings() {
    return settings;
  }

  /** Package-private for internal use. */
  UnaryCallable(
      FutureCallable<RequestT, ResponseT> callable, Channel channel, UnaryCallSettings settings) {
    this.callable = Preconditions.checkNotNull(callable);
    this.channel = channel;
    this.settings = settings;
  }

  /** Package-private for internal use. */
  UnaryCallable(FutureCallable<RequestT, ResponseT> callable) {
    this(callable, null, null);
  }

  /**
   * Perform a call asynchronously. If the {@link io.grpc.Channel} encapsulated in the given {@link
   * com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound either at
   * construction time or using {@link #bind(Channel)}.
   *
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return {@link ApiFuture} for the call result
   */
  public ApiFuture<ResponseT> futureCall(RequestT request, CallContext context) {
    if (context.getChannel() == null) {
      context = context.withChannel(channel);
    }
    return callable.futureCall(request, context);
  }

  /**
   * Same as {@link #futureCall(Object, CallContext)}, with null {@link io.grpc.Channel} and default
   * {@link io.grpc.CallOptions}.
   *
   * @param request request
   * @return {@link ApiFuture} for the call result
   */
  public ApiFuture<ResponseT> futureCall(RequestT request) {
    return futureCall(request, CallContext.createDefault().withChannel(channel));
  }

  /**
   * Perform a call synchronously. If the {@link io.grpc.Channel} encapsulated in the given {@link
   * com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound either at
   * construction time or using {@link #bind(Channel)}.
   *
   * @param request The request to send to the service.
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
   * @param request The request to send to the service.
   * @return the call result
   * @throws ApiException if there is any bad status in the response.
   * @throws RuntimeException if there is any other exception unrelated to bad status.
   */
  public ResponseT call(RequestT request) {
    return ApiExceptions.callAndTranslateApiException(futureCall(request));
  }

  /**
   * Create a callable with a bound channel. If a call is made without specifying a channel, the
   * {@code boundChannel} is used instead.
   *
   * <p>Package-private for internal use.
   */
  UnaryCallable<RequestT, ResponseT> bind(Channel boundChannel) {
    return new UnaryCallable<>(callable, boundChannel, settings);
  }

  /**
   * Creates a callable whose calls raise {@link ApiException} instead of the usual {@link
   * io.grpc.StatusRuntimeException}. The {@link ApiException} will consider failures with any of
   * the given status codes retryable.
   *
   * <p>This decoration must be added to a UnaryCallable before the "retrying" decoration which will
   * retry these codes.
   *
   * <p>Package-private for internal use.
   */
  UnaryCallable<RequestT, ResponseT> retryableOn(ImmutableSet<Status.Code> retryableCodes) {
    return new UnaryCallable<>(
        new ExceptionTransformingCallable<>(callable, retryableCodes), channel, settings);
  }

  /**
   * Creates a callable which retries using exponential back-off. Back-off parameters are defined by
   * the given {@code retrySettings}.
   *
   * <p>This decoration will only retry if the UnaryCallable has already been decorated with
   * "retryableOn" so that it throws an ApiException for the right codes.
   *
   * <p>Package-private for internal use.
   */
  UnaryCallable<RequestT, ResponseT> retrying(
      RetrySettings retrySettings, ScheduledExecutorService executor) {
    return retrying(retrySettings, executor, NanoClock.getDefaultClock());
  }

  /**
   * Creates a callable which retries using exponential back-off. Back-off parameters are defined by
   * the given {@code retrySettings}. Clock provides a time source used for calculating retry
   * timeouts.
   *
   * <p>Package-private for internal use.
   */
  @VisibleForTesting
  UnaryCallable<RequestT, ResponseT> retrying(
      RetrySettings retrySettings, ScheduledExecutorService executor, ApiClock clock) {
    return new UnaryCallable<>(
        new RetryingCallable<>(callable, retrySettings, executor, clock), channel, settings);
  }

  /**
   * Returns a callable which streams the resources obtained from a series of calls to a method
   * implementing the paged pattern.
   *
   * <p>Package-private for internal use.
   */
  <PagedListResponseT> UnaryCallable<RequestT, PagedListResponseT> paged(
      PagedListResponseFactory<RequestT, ResponseT, PagedListResponseT> pagedListResponseFactory) {
    return new UnaryCallable<>(
        new PagedCallable<>(callable, pagedListResponseFactory), channel, settings);
  }

  /**
   * Returns a callable which batches the call, meaning that multiple requests are batched together
   * and sent at the same time.
   *
   * <p>Package-private for internal use.
   */
  UnaryCallable<RequestT, ResponseT> batching(
      BatchingDescriptor<RequestT, ResponseT> batchingDescriptor,
      BatcherFactory<RequestT, ResponseT> batcherFactory) {
    return new UnaryCallable<>(
        new BatchingCallable<>(callable, batchingDescriptor, batcherFactory), channel, settings);
  }

  UnaryCallable<RequestT, ResponseT> withAuth(Credentials credentials) {
    if (credentials == null) {
      return this;
    }
    return new UnaryCallable<>(new AuthCallable<>(callable, credentials), channel, settings);
  }
}
