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

import com.google.api.core.BetaApi;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.grpc.Channel;
import java.util.Iterator;
import javax.annotation.Nullable;

/**
 * A StreamingCallable is an immutable object which is capable of making RPC calls to streaming API
 * methods.
 *
 * <p>It is considered advanced usage for a user to create a StreamingCallable themselves. This
 * class is intended to be created by a generated service API wrapper class, and configured by
 * instances of StreamingCallSettings.Builder which are exposed through the API wrapper class's
 * settings class.
 */
@BetaApi
public class StreamingCallable<RequestT, ResponseT> {
  private final DirectStreamingCallable<RequestT, ResponseT> callable;
  private final Channel channel;
  @Nullable private final StreamingCallSettings settings;

  /** Package-private */
  StreamingCallable(
      DirectStreamingCallable<RequestT, ResponseT> callable,
      Channel channel,
      StreamingCallSettings settings) {
    this.callable = callable;
    this.channel = channel;
    this.settings = settings;
  }

  /**
   * Bind the StreamingCallable with the given channel.
   *
   * @param boundChannel {@link io.grpc.Channel} to bind the callable with.
   */
  public StreamingCallable<RequestT, ResponseT> bind(Channel boundChannel) {
    return new StreamingCallable<>(callable, boundChannel, settings);
  }

  /**
   * Create a callable object that represents a streaming API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param streamingCallSettings {@link com.google.api.gax.grpc.StreamingCallSettings} to configure
   *     the method-level settings with.
   * @param channel {@link Channel} to use to connect to the service.
   * @return {@link com.google.api.gax.grpc.StreamingCallable} callable object.
   */
  @Deprecated
  public static <RequestT, ResponseT> StreamingCallable<RequestT, ResponseT> create(
      StreamingCallSettings<RequestT, ResponseT> streamingCallSettings, Channel channel) {
    return streamingCallSettings.createStreamingCallable(channel);
  }

  /**
   * Create a callable object that represents a streaming API method. Public only for technical
   * reasons - for advanced usage
   *
   * @param streamingCallSettings {@link com.google.api.gax.grpc.StreamingCallSettings} to configure
   *     the method-level settings with.
   * @param context {@link ClientContext} to use to connect to the service.
   * @return {@link com.google.api.gax.grpc.StreamingCallable} callable object.
   */
  public static <RequestT, ResponseT> StreamingCallable<RequestT, ResponseT> create(
      StreamingCallSettings<RequestT, ResponseT> streamingCallSettings, ClientContext context) {
    return streamingCallSettings.createStreamingCallable(context);
  }

  /**
   * Conduct a bidirectional streaming call
   *
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @return {@link ApiStreamObserver} which is used for making streaming requests.
   */
  public ApiStreamObserver<RequestT> bidiStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver) {
    Preconditions.checkNotNull(channel);
    return callable.bidiStreamingCall(
        responseObserver, CallContext.createDefault().withChannel(channel));
  }

  /**
   * Conduct a bidirectional streaming call with the given {@link
   * com.google.api.gax.grpc.CallContext}.
   *
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @param context {@link CallContext} to provide context information of the gRPC call. The
   *     existing channel will be overridden by the channel contained in the context (if any).
   * @return {@link ApiStreamObserver} which is used for making streaming requests.
   */
  public ApiStreamObserver<RequestT> bidiStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
    if (context.getChannel() == null) {
      context = context.withChannel(channel);
    }
    Preconditions.checkNotNull(context.getChannel());
    return callable.bidiStreamingCall(responseObserver, context);
  }

  /**
   * Conduct a server streaming call
   *
   * @param request request
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   */
  public void serverStreamingCall(RequestT request, ApiStreamObserver<ResponseT> responseObserver) {
    Preconditions.checkNotNull(channel);
    callable.serverStreamingCall(
        request, responseObserver, CallContext.createDefault().withChannel(channel));
  }

  /**
   * Conduct a server streaming call with the given {@link com.google.api.gax.grpc.CallContext}.
   *
   * @param request request
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @param context {@link CallContext} to provide context information of the gRPC call. The
   *     existing channel will be overridden by the channel contained in the context (if any).
   */
  public void serverStreamingCall(
      RequestT request, ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
    if (context.getChannel() == null) {
      context = context.withChannel(channel);
    }
    Preconditions.checkNotNull(context.getChannel());
    callable.serverStreamingCall(request, responseObserver, context);
  }

  /**
   * Conduct a iteration server streaming call
   *
   * @param request request
   * @return {@link Iterator} which is used for iterating the responses.
   */
  public Iterator<ResponseT> serverStreamingCall(RequestT request) {
    Preconditions.checkNotNull(channel);
    return callable.blockingServerStreamingCall(
        request, CallContext.createDefault().withChannel(channel));
  }

  /**
   * Conduct a iteration server streaming call with the given {@link CallContext}
   *
   * @param request request
   * @param context {@link CallContext} to provide context information of the gRPC call. The
   *     existing channel will be overridden by the channel contained in the context (if any).
   * @return {@link Iterator} which is used for iterating the responses.
   */
  public Iterator<ResponseT> serverStreamingCall(RequestT request, CallContext context) {
    if (context.getChannel() == null) {
      context = context.withChannel(channel);
    }
    return callable.blockingServerStreamingCall(request, context);
  }

  /**
   * Conduct a client streaming call
   *
   * @param responseObserver {@link ApiStreamObserver} to receive the non-streaming response.
   * @return {@link ApiStreamObserver} which is used for making streaming requests.
   */
  public ApiStreamObserver<RequestT> clientStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver) {
    Preconditions.checkNotNull(channel);
    return callable.clientStreamingCall(
        responseObserver, CallContext.createDefault().withChannel(channel));
  }

  /**
   * Conduct a client streaming call with the given {@link CallContext}
   *
   * @param responseObserver {@link ApiStreamObserver} to receive the non-streaming response.
   * @param context {@link CallContext} to provide context information of the gRPC call. The
   *     existing channel will be overridden by the channel contained in the context (if any)
   * @return {@link ApiStreamObserver} which is used for making streaming requests.
   */
  public ApiStreamObserver<RequestT> clientStreamingCall(
      ApiStreamObserver<ResponseT> responseObserver, CallContext context) {
    if (context.getChannel() == null) {
      context = context.withChannel(channel);
    }
    return callable.clientStreamingCall(responseObserver, context);
  }

  @VisibleForTesting
  Channel getChannel() {
    return channel;
  }
}
