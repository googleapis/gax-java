/*
 * Copyright 2016 Google LLC
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
package com.google.api.gax.grpc;

import com.google.api.core.AbstractApiFuture;
import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.base.Preconditions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code GrpcDirectCallable} creates gRPC calls.
 *
 * <p>Package-private for internal use.
 */
class GrpcDirectCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
  private static final Logger LOGGER = Logger.getLogger(GrpcDirectCallable.class.getName());

  private final MethodDescriptor<RequestT, ResponseT> descriptor;

  GrpcDirectCallable(MethodDescriptor<RequestT, ResponseT> descriptor) {
    this.descriptor = Preconditions.checkNotNull(descriptor);
  }

  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext inputContext) {
    Preconditions.checkNotNull(request);
    Preconditions.checkNotNull(inputContext);

    ClientCall<RequestT, ResponseT> clientCall = GrpcClientCalls.newCall(descriptor, inputContext);

    // Start the call
    GrpcFuture<ResponseT> future = new GrpcFuture<>(clientCall);
    clientCall.start(new FutureListener<>(future), new Metadata());

    // Send the request
    try {
      clientCall.sendMessage(request);
      clientCall.halfClose();
      // Request an extra message to detect misconfigured servers
      clientCall.request(2);
    } catch (Throwable sendError) {
      // Cancel if anything goes wrong and
      try {
        clientCall.cancel(null, sendError);
      } catch (Throwable cancelError) {
        LOGGER.log(Level.SEVERE, "Error encountered while closing it", sendError);
      }

      // re-throw the error
      if (sendError instanceof RuntimeException) {
        throw (RuntimeException) sendError;
      } else if (sendError instanceof Error) {
        throw (Error) sendError;
      }
      // Should be impossible
      throw new AssertionError(sendError);
    }

    return future;
  }

  @Override
  public String toString() {
    return String.format("direct(%s)", descriptor);
  }

  /** Thin wrapper around an ApiFuture that will cancel the underlying ClientCall. */
  private static class GrpcFuture<T> extends AbstractApiFuture<T> {
    private ClientCall<?, T> call;

    public GrpcFuture(ClientCall<?, T> call) {
      this.call = call;
    }

    @Override
    protected void interruptTask() {
      call.cancel("GrpcFuture was cancelled", null);
    }

    @Override
    public boolean set(T value) {
      return super.set(value);
    }

    @Override
    public boolean setException(Throwable throwable) {
      return super.setException(throwable);
    }
  }

  /**
   * A bridge between gRPC's ClientCall.Listener to an ApiFuture.
   *
   * <p>The Listener will eagerly resolve the future when the first message is received and will not
   * wait for the trailers. This should cut down on the latency at the expense of safety. If the
   * server is misconfigured and sends a second response for a unary call, the error will be logged,
   * but the future will still be successful.
   */
  private static class FutureListener<T> extends ClientCall.Listener<T> {
    private final GrpcFuture<T> future;

    private FutureListener(GrpcFuture<T> future) {
      this.future = future;
    }

    @Override
    public void onMessage(T message) {
      if (!future.set(message)) {
        throw Status.INTERNAL
            .withDescription("More than one value received for unary call")
            .asRuntimeException();
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      if (!future.isDone()) {
        future.setException(
            Status.INTERNAL
                .withDescription("No value received for unary call")
                .asException(trailers));
      }
      if (!status.isOk()) {
        LOGGER.log(
            Level.SEVERE, "Received error for unary call after receiving a successful response");
      }
    }
  }
}
