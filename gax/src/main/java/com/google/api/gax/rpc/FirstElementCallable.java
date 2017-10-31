package com.google.api.gax.rpc;

import com.google.api.core.AbstractApiFuture;
import com.google.api.core.ApiFuture;
import javax.annotation.concurrent.GuardedBy;

class FirstElementCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
  private final ServerStreamingCallable<RequestT, ResponseT> streamingCallable;

  FirstElementCallable(ServerStreamingCallable<RequestT, ResponseT> streamingCallable) {
    this.streamingCallable = streamingCallable;
  }

  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext context) {
    ResponseObserverToFutureAdapter<ResponseT> adapter = new ResponseObserverToFutureAdapter<>();

    streamingCallable.call(request, adapter, context);
    return adapter.future;
  }


  static class ResponseObserverToFutureAdapter<ResponseT> implements ResponseObserver<ResponseT> {
    private final MyFuture future = new MyFuture();
    private final Object lock = new Object();
    @GuardedBy("lock")
    private StreamController controller;
    @GuardedBy("lock")
    private boolean isCancelled = false;

    @Override
    public void onStart(StreamController controller) {
      controller.disableAutoInboundFlowControl();

      final boolean wasCancelled;
      synchronized (lock) {
        wasCancelled = isCancelled;
        this.controller = controller;
      }
      if (wasCancelled) {
        controller.cancel();
      } else {
        controller.request(1);
      }
    }

    @Override
    public void onResponse(ResponseT response) {
      future.set(response);
      controller.cancel();
    }

    @Override
    public void onError(Throwable t) {
      future.setException(t);
    }

    @Override
    public void onComplete() {
      future.set(null);
    }

    class MyFuture extends AbstractApiFuture<ResponseT> {
      @Override
      protected void interruptTask() {
        final StreamController currentController;

        synchronized (lock) {
          isCancelled = true;
          currentController = controller;
        }
        if (currentController != null) {
          currentController.cancel();
        }
      }
    }
  }
}