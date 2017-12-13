/*
 * Copyright 2017, Google LLC All rights reserved.
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
import java.util.Iterator;

/**
 * A ServerStreamingCallable is an immutable object which is capable of making RPC calls to server
 * streaming API methods. Not all transports support streaming.
 *
 * <p>It is considered advanced usage for a user to create a ServerStreamingCallable themselves.
 * This class is intended to be created by a generated client class, and configured by instances of
 * StreamingCallSettings.Builder which are exposed through the client settings class.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public abstract class ServerStreamingCallable<RequestT, ResponseT> {

  protected ServerStreamingCallable() {}

  /**
   * Conduct a iteration server streaming call.
   *
   * <p>This returns a live stream that must either be fully consumed or cancelled. Example usage:
   *
   * <pre>{@code
   * StreamingCallable<String> streamingCallable = // ..
   * ServerStream stream = streamingCallable.call(request)
   * for (String s : stream) {
   *   if ("needle".equals(s)) {
   *     // Cancelling the stream will cause `hasNext()` to return false on the next iteration,
   *     // naturally breaking the loop.
   *     stream.cancel();
   *   }
   * }
   * List<String></String> theResult = streamingCallable.all().call(request);
   * ApiFuture<List<String>> theResult = streamingCallable.all().futureCall(request);
   * }</pre>
   *
   * @param request request
   * @return {@link ServerStream} which is used for iterating the responses.
   */
  public ServerStream<ResponseT> call(RequestT request) {
    return call(request, (ApiCallContext) null);
  }

  /**
   * Conduct a server streaming call with the given {@link ApiCallContext}.
   *
   * <p>This returns a live stream that must either be fully consumed or cancelled.
   *
   * @param request request
   * @param context the context
   * @return {@link ServerStream} which is used for iterating the responses.
   */
  public ServerStream<ResponseT> call(RequestT request, ApiCallContext context) {
    ServerStream<ResponseT> stream = new ServerStream<>();
    call(request, stream.observer(), context);

    return stream;
  }

  /**
   * Conduct a server streaming call with the given {@link ApiCallContext}.
   *
   * @param request request
   * @param responseObserver {@link ResponseObserver} to observe the streaming responses
   * @param context {@link ApiCallContext} to provide context information for the RPC call.
   */
  public abstract void call(
      RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context);

  /**
   * Conduct a server streaming call
   *
   * @param request request
   * @param responseObserver {@link ResponseObserver} to observe the streaming responses
   */
  public void call(RequestT request, ResponseObserver<ResponseT> responseObserver) {
    call(request, responseObserver, null);
  }

  /**
   * Conduct a server streaming call with the given {@link ApiCallContext}.
   *
   * @param request request
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @param context {@link ApiCallContext} to provide context information for the RPC call.
   * @deprecated Please use the {@link ResponseObserver} variant instead.
   */
  @Deprecated
  public void serverStreamingCall(
      RequestT request,
      final ApiStreamObserver<ResponseT> responseObserver,
      ApiCallContext context) {

    call(request, new ApiStreamObserverAdapter<>(responseObserver), context);
  }

  /**
   * Conduct a server streaming call
   *
   * @param request request
   * @param responseObserver {@link ApiStreamObserver} to observe the streaming responses
   * @deprecated Please use the {@link ResponseObserver} variant instead.
   */
  @Deprecated
  public void serverStreamingCall(RequestT request, ApiStreamObserver<ResponseT> responseObserver) {
    serverStreamingCall(request, responseObserver, null);
  }

  /**
   * Conduct an iteration server streaming call
   *
   * @param request request
   * @param context context
   * @return {@link Iterator} which is used for iterating the responses.
   * @deprecated Please use call() instead.
   */
  @Deprecated
  public Iterator<ResponseT> blockingServerStreamingCall(RequestT request, ApiCallContext context) {
    return call(request, context).iterator();
  }

  /**
   * Conduct a iteration server streaming call
   *
   * @param request request
   * @return {@link Iterator} which is used for iterating the responses.
   * @deprecated Please use call() instead.
   */
  @Deprecated
  public Iterator<ResponseT> blockingServerStreamingCall(RequestT request) {
    return blockingServerStreamingCall(request, null);
  }

  /**
   * Returns a new {@code ServerStreamingCallable} with an {@link ApiCallContext} that is used as a
   * default when none is supplied in individual calls.
   *
   * @param defaultCallContext the default {@link ApiCallContext}.
   */
  public ServerStreamingCallable<RequestT, ResponseT> withDefaultCallContext(
      final ApiCallContext defaultCallContext) {
    return new ServerStreamingCallable<RequestT, ResponseT>() {
      @Override
      public void call(
          RequestT request,
          ResponseObserver<ResponseT> responseObserver,
          ApiCallContext thisCallContext) {
        ServerStreamingCallable.this.call(
            request, responseObserver, defaultCallContext.merge(thisCallContext));
      }
    };
  }

  /**
   * Backwards compatibility bridge from the new {@link ResponseObserver} api to the old {@link
   * ApiStreamObserver} api.
   *
   * @param <T> The type of the response.
   * @deprecated Use ResponseObserver directly
   */
  @Deprecated
  private static class ApiStreamObserverAdapter<T> implements ResponseObserver<T> {
    private final ApiStreamObserver<T> delegate;

    ApiStreamObserverAdapter(ApiStreamObserver<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onStart(StreamController controller) {
      // Noop: the old style assumes automatic flow control and doesn't support cancellation.
    }

    @Override
    public void onResponse(T response) {
      delegate.onNext(response);
    }

    @Override
    public void onError(Throwable t) {
      delegate.onError(t);
    }

    @Override
    public void onComplete() {
      delegate.onCompleted();
    }
  }
}
