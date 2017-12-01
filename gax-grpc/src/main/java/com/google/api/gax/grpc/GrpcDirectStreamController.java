package com.google.api.gax.grpc;

import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.common.base.Preconditions;
import io.grpc.ClientCall;
import io.grpc.Metadata;

/**
 * Wraps a GRPC ClientCall in a {@link StreamController}. It feeds events to a {@link
 * ResponseObserver} and allows for back pressure.
 *
 * <p>Package-private for internal use.
 */
class GrpcDirectStreamController<RequestT, ResponseT> extends StreamController {
  private final ClientCall<RequestT, ResponseT> clientCall;
  private final ResponseObserver<ResponseT> responseObserver;
  private boolean hasStarted;
  private boolean autoflowControl = true;
  private int numRequested;

  GrpcDirectStreamController(
      ClientCall<RequestT, ResponseT> clientCall, ResponseObserver<ResponseT> responseObserver) {
    this.clientCall = clientCall;
    this.responseObserver = responseObserver;
  }

  @Override
  public void cancel(Throwable cause) {
    clientCall.cancel(null, cause);
  }

  @Override
  public void disableAutoInboundFlowControl() {
    Preconditions.checkState(
        !hasStarted, "Can't disable automatic flow control after the stream has started.");
    autoflowControl = false;
  }

  @Override
  public void request(int count) {
    Preconditions.checkState(!autoflowControl, "Autoflow control is enabled.");

    // Buffer the requested count in case the consumer requested responses in the onStart()
    if (!hasStarted) {
      numRequested += count;
    } else {
      clientCall.request(count);
    }
  }

  void start(RequestT request) {
    responseObserver.onStart(this);

    this.hasStarted = true;

    clientCall.start(
        new GrpcDirectResponseObserverAdapter<>(clientCall, autoflowControl, responseObserver), new Metadata());

    clientCall.sendMessage(request);
    clientCall.halfClose();

    if (autoflowControl) {
      clientCall.request(1);
    } else if (numRequested > 0) {
      clientCall.request(numRequested);
    }
  }
}
