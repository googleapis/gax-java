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

import com.google.api.core.BetaApi;
import java.util.Iterator;
import javax.annotation.Nullable;

/**
 * A StreamingCallable is an immutable object which is capable of making RPC calls to streaming API
 * methods. Not all transports support streaming.
 *
 * <p>It is considered advanced usage for a user to create a StreamingCallable themselves. This
 * class is intended to be created by a generated client class, and configured by instances of
 * StreamingCallSettings.Builder which are exposed through the client settings class.
 */
@BetaApi
public class StreamingCallable<RequestT, ResponseT>
    implements StreamingCallableImpl<RequestT, ResponseT> {

  private final StreamingCallableImpl<RequestT, ResponseT> callable;
  @Nullable private final ApiCallContextDecorator callContextDecorator;
  @Nullable private final StreamingCallSettings settings;

  public static <RequestT, ResponseT> StreamingCallable<RequestT, ResponseT> create(
      StreamingCallableImpl<RequestT, ResponseT> callable) {
    return create(callable, null);
  }

  public static <RequestT, ResponseT> StreamingCallable<RequestT, ResponseT> create(
      StreamingCallableImpl<RequestT, ResponseT> callable,
      ApiCallContextDecorator callContextDecorator) {
    return create(callable, callContextDecorator, null);
  }

  public static <RequestT, ResponseT> StreamingCallable<RequestT, ResponseT> create(
      StreamingCallableImpl<RequestT, ResponseT> callable,
      ApiCallContextDecorator callContextDecorator,
      StreamingCallSettings settings) {
    return new StreamingCallable<RequestT, ResponseT>(callable, callContextDecorator, settings);
  }

  private StreamingCallable(
      StreamingCallableImpl<RequestT, ResponseT> callable,
      ApiCallContextDecorator callContextDecorator,
      StreamingCallSettings settings) {
    this.callable = callable;
    this.callContextDecorator = callContextDecorator;
    this.settings = settings;
  }

  /**
   * Conduct a bidirectional streaming call
   *
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @return {@link ApiStreamObserver} which is used for making streaming requests.
   */
  public ApiStreamObserver<RequestT> bidiStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver) {
    return bidiStreamingCall(responseObserver, null);
  }

  /**
   * Conduct a bidirectional streaming call with the given {@link ApiCallContext}.
   *
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @param context {@link ApiCallContext} to provide context information for the RPC call.
   * @return {@link ApiStreamObserver} which is used for making streaming requests.
   */
  @Override
  public ApiStreamObserver<RequestT> bidiStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
    ApiCallContext newCallContext = context;
    if (callContextDecorator != null) {
      newCallContext = callContextDecorator.decorate(context);
    }
    return callable.bidiStreamingCall(responseObserver, newCallContext);
  }

  /**
   * Conduct a server streaming call
   *
   * @param request request
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   */
  public void serverStreamingCall(RequestT request, ApiStreamObserver<ResponseT> responseObserver) {
    serverStreamingCall(request, responseObserver, null);
  }

  /**
   * Conduct a server streaming call with the given {@link ApiCallContext}.
   *
   * @param request request
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @param context {@link ApiCallContext} to provide context information for the RPC call.
   */
  @Override
  public void serverStreamingCall(
      RequestT request, ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
    ApiCallContext newCallContext = context;
    if (callContextDecorator != null) {
      newCallContext = callContextDecorator.decorate(context);
    }
    callable.serverStreamingCall(request, responseObserver, newCallContext);
  }

  /**
   * Conduct a iteration server streaming call
   *
   * @param request request
   * @return {@link Iterator} which is used for iterating the responses.
   */
  public Iterator<ResponseT> blockingServerStreamingCall(RequestT request) {
    return blockingServerStreamingCall(request, null);
  }

  /**
   * Conduct a iteration server streaming call with the given {@link ApiCallContext}
   *
   * @param request request
   * @param context {@link ApiCallContext} to provide context information for the RPC call.
   * @return {@link Iterator} which is used for iterating the responses.
   */
  @Override
  public Iterator<ResponseT> blockingServerStreamingCall(RequestT request, ApiCallContext context) {
    ApiCallContext newCallContext = context;
    if (callContextDecorator != null) {
      newCallContext = callContextDecorator.decorate(context);
    }
    return callable.blockingServerStreamingCall(request, newCallContext);
  }

  /**
   * Conduct a client streaming call
   *
   * @param responseObserver {@link ApiStreamObserver} to receive the non-streaming response.
   * @return {@link ApiStreamObserver} which is used for making streaming requests.
   */
  public ApiStreamObserver<RequestT> clientStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver) {
    return clientStreamingCall(responseObserver, null);
  }

  /**
   * Conduct a client streaming call with the given {@link ApiCallContext}
   *
   * @param responseObserver {@link ApiStreamObserver} to receive the non-streaming response.
   * @param context {@link ApiCallContext} to provide context information for the RPC call.
   * @return {@link ApiStreamObserver} which is used for making streaming requests.
   */
  @Override
  public ApiStreamObserver<RequestT> clientStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
    ApiCallContext newCallContext = context;
    if (callContextDecorator != null) {
      newCallContext = callContextDecorator.decorate(context);
    }
    return callable.clientStreamingCall(responseObserver, newCallContext);
  }
}
