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

package io.gapi.gax.grpc;

import io.grpc.Channel;
import io.grpc.ExperimentalApi;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * A callable is an object which represents one or more rpc calls. Various operators on callables
 * produce new callables, representing common API programming patterns. Callables can be used to
 * directly operate against an api, or to efficiently implement wrappers for apis which add
 * additional functionality and processing.
 */
@ExperimentalApi
public class ApiCallable<RequestT, ResponseT> {
  private final FutureCallable<RequestT, ResponseT> callable;

  ApiCallable(FutureCallable<RequestT, ResponseT> callable) {
    this.callable = callable;
  }

  /**
   * Perform a call asynchronously. If the {@link io.grpc.Channel} encapsulated in the given
   * {@link io.gapi.gax.grpc.CallContext} is null, a channel must have already been bound,
   * using {@link #bind(Channel)}.
   *
   * @param context {@link io.gapi.gax.grpc.CallContext} to make the call with
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
   * {@link io.gapi.gax.grpc.CallContext} is null, a channel must have already been bound,
   * using {@link #bind(Channel)}.
   *
   * @param context {@link io.gapi.gax.grpc.CallContext} to make the call with
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
   * Perform a call asynchronously with the given {@code observer}.
   * If the {@link io.grpc.Channel} encapsulated in the given
   * {@link io.gapi.gax.grpc.CallContext} is null, a channel must have already been bound,
   * using {@link #bind(Channel)}.
   *
   * @param context {@link io.gapi.gax.grpc.CallContext} to make the call with
   * @param observer Observer to interact with the result
   */
  public void asyncCall(CallContext<RequestT> context, StreamObserver<ResponseT> observer) {
    Futures.addCallback(
        futureCall(context),
        new FutureCallback<ResponseT>() {
          @Override
          public void onFailure(Throwable t) {
            if (observer != null) {
              observer.onError(t);
            }
          }

          @Override
          public void onSuccess(ResponseT result) {
            if (observer != null) {
              observer.onNext(result);
              observer.onCompleted();
            }
          }
        });
  }

  /**
   * Same as {@link #asyncCall(RequestT, StreamObserver)}, with null {@link io.grpc.Channel} and
   * default {@link io.grpc.CallOptions}.
   *
   * @param context {@link io.gapi.gax.grpc.CallContext} to make the call with
   * @param observer Observer to interact with the result
   */
  public void asyncCall(RequestT request, StreamObserver<ResponseT> observer) {
    asyncCall(CallContext.<RequestT>of(request), observer);
  }

  /**
   * Creates a callable which can execute the described gRPC method.
   */
  public static <ReqT, RespT> ApiCallable<ReqT, RespT> create(
      MethodDescriptor<ReqT, RespT> descriptor) {
    return create(new DescriptorClientCallFactory<>(descriptor));
  }

  /**
   * Creates a callable which uses the {@link io.grpc.ClientCall}
   * generated by the given {@code factory}
   */
  public static <ReqT, RespT> ApiCallable<ReqT, RespT> create(
      ClientCallFactory<ReqT, RespT> factory) {
    return new ApiCallable<ReqT, RespT>(new DirectCallable<ReqT, RespT>(factory));
  }

  /**
   * Create a callable with a bound channel. If a call is made without specifying a channel,
   * the {@code boundChannel} is used instead.
   */
  public ApiCallable<RequestT, ResponseT> bind(Channel boundChannel) {
    return new ApiCallable<RequestT, ResponseT>(
        new ChannelBindingCallable<RequestT, ResponseT>(callable, boundChannel));
  }

  /**
   * Creates a callable which retries using exponential back-off. Back-off parameters are defined
   * by the given {@code retryParams}.
   */
  public ApiCallable<RequestT, ResponseT> retrying(RetryParams retryParams) {
    return new ApiCallable<RequestT, ResponseT>(
        new RetryingCallable<RequestT, ResponseT>(callable, retryParams));
  }

  /**
   * Same as {@link #retrying(RetryParams)} but with {@link RetryParams#DEFAULT}.
   */
  public ApiCallable<RequestT, ResponseT> retrying() {
    return retrying(RetryParams.DEFAULT);
  }

  /**
   * Returns a callable which streams the resources obtained from a series of calls to a method
   * implementing the pagination pattern.
   */
  public <ResourceT> ApiCallable<RequestT, Iterable<ResourceT>> pageStreaming(
      PageDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    return new ApiCallable<RequestT, Iterable<ResourceT>>(
        new PageStreamingCallable<RequestT, ResponseT, ResourceT>(callable, pageDescriptor));
  }

  /**
   * Returns a callable which bundles the call, meaning that multiple requests are bundled
   * together and sent at the same time.
   */
  public ApiCallable<RequestT, ResponseT> bundling(
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlerFactory<RequestT, ResponseT> bundlerFactory) {
    return new ApiCallable<RequestT, ResponseT>(
        new BundlingCallable<RequestT, ResponseT>(callable, bundlingDescriptor, bundlerFactory));
  }
}
