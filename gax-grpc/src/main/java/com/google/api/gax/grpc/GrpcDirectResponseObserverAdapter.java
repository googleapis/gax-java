package com.google.api.gax.grpc;

import com.google.api.gax.rpc.ResponseObserver;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;

/**
 * Adapts the events from a {@link ClientCall.Listener} to a {@link ResponseObserver} and handles
 * automatic flow control.
 *
 * <p>Package-private for internal use.
 *
 * @param <ResponseT> The type of the response.
 */
class GrpcDirectResponseObserverAdapter<ResponseT> extends ClientCall.Listener<ResponseT> {
  private final ClientCall<?, ResponseT> clientCall;
  private final boolean autoflowControl;
  private final ResponseObserver<ResponseT> delegate;

  GrpcDirectResponseObserverAdapter(
      ClientCall<?, ResponseT> clientCall,
      boolean autoflowControl,
      ResponseObserver<ResponseT> delegate) {
    this.clientCall = clientCall;
    this.autoflowControl = autoflowControl;
    this.delegate = delegate;
  }

  /**
   * Notifies the delegate of the new message and if automatic flow control is enabled, requests
   * the next message. Any errors raised by the delegate will be bubbled up to GRPC, which cancel
   * the ClientCall and close this listener.
   *
   * @param message The new message.
   */
  @Override
  public void onMessage(ResponseT message) {
    delegate.onResponse(message);

    if (autoflowControl) {
      clientCall.request(1);
    }
  }

  @Override
  public void onClose(Status status, Metadata trailers) {
    if (status.isOk()) {
      delegate.onComplete();
    } else {
      delegate.onError(status.asRuntimeException(trailers));
    }
  }
}
