package com.google.api.gax.grpc;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestCountingManagedChannel extends ManagedChannel {
  private ManagedChannel delegate;
  private AtomicInteger requestCounter = new AtomicInteger();
  private AtomicBoolean isShutdown = new AtomicBoolean();

  RequestCountingManagedChannel(ManagedChannel managedChannel) {
    this.delegate = managedChannel;
  }

  @Override
  public ManagedChannel shutdown() {
    isShutdown.set(true);
    delegate.shutdown();
    return this;
  }

  @Override
  public boolean isShutdown() {
    return isShutdown.get() && delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public ManagedChannel shutdownNow() {
    isShutdown.set(true);
    delegate.shutdownNow();
    return this;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  /**
   * Wrap client call to decrement oustandingRequest counter when the call completes
   *
   * @param call call to be wrapped
   * @return the wrapped call
   */
  private <ReqT, RespT> ClientCall<ReqT, RespT> clientCallWrapper(
      ClientCall<ReqT, RespT> call) {
    return new SimpleForwardingClientCall<ReqT, RespT>(call) {
      public void start(Listener<RespT> responseListener, Metadata headers) {
        Listener<RespT> forwardingResponseListener =
            new SimpleForwardingClientCallListener<RespT>(responseListener) {

              @Override
              public void onClose(Status status, Metadata trailers) {
                super.onClose(status, trailers);
                // the ordering of decrementing and checking requestCounter before checking
                // isShutdown is important. This is the opposite ordering from refreshChannel. This
                // ensures shutdown on channel is called at least once
                if (requestCounter.decrementAndGet() == 0 && isShutdown.get()) {
                  System.err.println("last request. shut down");
                  shutdownNow();
                }
              }
            };
        super.start(forwardingResponseListener, headers);
      }
    };
  }
  @Override
  public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    requestCounter.incrementAndGet();
    return clientCallWrapper(delegate.newCall(methodDescriptor, callOptions));
  }

  @Override
  public String authority() {
    return delegate.authority();
  }
}
