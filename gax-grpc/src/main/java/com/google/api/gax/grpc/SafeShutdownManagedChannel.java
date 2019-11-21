/*
 * Copyright 2019 Google LLC
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

import com.google.common.base.Preconditions;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ManagedChannel} that will complete all calls started on the underlying channel before
 * shutting down.
 *
 * <p>This class is not thread-safe. Caller must synchronize in order to ensure no new calls if safe
 * shutdown has started.
 *
 * <p>Package-private for internal use.
 */
class SafeShutdownManagedChannel extends ManagedChannel {
  private final ManagedChannel delegate;
  private final AtomicInteger outstandingCalls = new AtomicInteger(0);
  private volatile boolean isShutdownSafely = false;

  SafeShutdownManagedChannel(ManagedChannel managedChannel) {
    this.delegate = managedChannel;
  }

  /**
   * Safely shutdown channel by checking that there are no more outstanding calls. If there are
   * outstanding calls, the last call will invoke this method again when it complete
   *
   * <p>Caller should take care to synchronize with newCall so no new calls are started after
   * shutdownSafely is called
   */
  void shutdownSafely() {
    isShutdownSafely = true;
    if (outstandingCalls.get() == 0) {
      delegate.shutdown();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdown() {
    delegate.shutdown();
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdownNow() {
    delegate.shutdownNow();
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  /** {@inheritDoc} */
  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  /**
   * Decrement outstanding call counter and shutdown if there are no more outstanding calls and
   * {@link SafeShutdownManagedChannel#shutdownSafely()} has been invoked
   */
  private void onClientCallClose() {
    if (outstandingCalls.decrementAndGet() == 0 && isShutdownSafely) {
      shutdownSafely();
    }
  }

  /** Listener that's responsible for decrementing outstandingCalls when the call closes */
  private class DecrementOutstandingCalls<RespT> extends SimpleForwardingClientCallListener<RespT> {
    DecrementOutstandingCalls(Listener<RespT> delegate) {
      super(delegate);
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      // decrement in finally block in case onClose throws an exception
      try {
        super.onClose(status, trailers);
      } finally {
        onClientCallClose();
      }
    }
  }

  /** To wrap around delegate to hook in {@link DecrementOutstandingCalls} */
  private class ClientCallProxy<ReqT, RespT> extends SimpleForwardingClientCall<ReqT, RespT> {
    ClientCallProxy(ClientCall<ReqT, RespT> delegate) {
      super(delegate);
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata headers) {
      super.start(new DecrementOutstandingCalls<>(responseListener), headers);
    }
  }

  /**
   * Caller must take care to synchronize newCall and shutdownSafely in order to avoid race
   * conditions of starting new calls after shutdownSafely is called
   *
   * @see io.grpc.ManagedChannel#newCall(MethodDescriptor, CallOptions)
   */
  @Override
  public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    Preconditions.checkState(!isShutdownSafely);
    // increment after client call in case newCall throws an exception
    ClientCall<RequestT, ResponseT> clientCall =
        new ClientCallProxy<>(delegate.newCall(methodDescriptor, callOptions));
    outstandingCalls.incrementAndGet();
    return clientCall;
  }

  /** {@inheritDoc} */
  @Override
  public String authority() {
    return delegate.authority();
  }
}
