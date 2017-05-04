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
package com.google.api.gax.rpc;

import com.google.api.core.ApiClock;
import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.core.NanoClock;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.grpc.ApiException;
import com.google.api.gax.grpc.ApiExceptions;
import com.google.api.gax.grpc.BatcherFactory;
import com.google.api.gax.grpc.BatchingCallSettings;
import com.google.api.gax.grpc.BatchingCallable;
import com.google.api.gax.grpc.BatchingDescriptor;
import com.google.api.gax.grpc.CallContext;
import com.google.api.gax.grpc.ExceptionTransformingCallable;
import com.google.api.gax.grpc.FutureCallable;
import com.google.api.gax.grpc.PagedCallSettings;
import com.google.api.gax.grpc.PagedCallable;
import com.google.api.gax.grpc.PagedListResponseFactory;
import com.google.api.gax.grpc.RetryingCallable;
import com.google.api.gax.grpc.SimpleCallSettings;
import com.google.api.gax.grpc.UnaryCallSettings;
import com.google.api.gax.retrying.RetrySettings;
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
public abstract class UnaryCallable<RequestT, ResponseT> {

  protected UnaryCallable() {}

  /**
   * Perform a call asynchronously.
   *
   * @return {@link ApiFuture} for the call result
   */
  public abstract ApiFuture<ResponseT> futureCall(RequestT request);

  /**
   * Perform a call synchronously.
   *
   * @param request The request to send to the service.
   * @return the call result
   * @throws ApiException if there is any bad status in the response.
   * @throws RuntimeException if there is any other exception unrelated to bad status.
   */
  public abstract ResponseT call(RequestT request);
}
