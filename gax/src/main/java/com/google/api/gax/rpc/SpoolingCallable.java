package com.google.api.gax.rpc;

import com.google.api.core.AbstractApiFuture;
import com.google.api.core.ApiFuture;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;

class SpoolingCallable<RequestT, ResponseT> extends
    UnaryCallable<RequestT, List<ResponseT>> {
  private final ServerStreamingCallable<RequestT, ResponseT> streamingCallable;

  SpoolingCallable(ServerStreamingCallable<RequestT, ResponseT> streamingCallable) {
    this.streamingCallable = streamingCallable;
  }

  @Override
  public ApiFuture<List<ResponseT>> futureCall(RequestT request, ApiCallContext context) {
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
    private List<ResponseT> buffer = Lists.newArrayList();

    @Override
    public void onStart(StreamController controller) {
      final boolean wasCancelled;

      synchronized (lock) {
        wasCancelled = isCancelled;
        this.controller = controller;
      }
      if (wasCancelled) {
        controller.cancel();
      }
    }

    @Override
    public void onResponse(ResponseT response) {
      buffer.add(response);
    }

    @Override
    public void onError(Throwable t) {
      future.setException(t);
    }

    @Override
    public void onComplete() {
      future.set(buffer);
    }

    class MyFuture extends AbstractApiFuture<List<ResponseT>> {
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
