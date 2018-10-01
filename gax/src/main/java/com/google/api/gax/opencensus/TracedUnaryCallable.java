package com.google.api.gax.opencensus;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.util.concurrent.MoreExecutors;

public class TracedUnaryCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
  private final UnaryCallable<RequestT, ResponseT> innerCallable;
  private final String spanName;

  public TracedUnaryCallable(
      UnaryCallable<RequestT, ResponseT> innerCallable, String spanName) {
    this.innerCallable = innerCallable;
    this.spanName = spanName;
  }

  @Override
  public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext context) {
    final OpenCensusTracer tracer = OpenCensusTracer.create(spanName);

    ApiFuture<ResponseT> innerFuture;

    try (OpenCensusTracer.TracerContext ignored = tracer.enter()) {
      tracer.operationStarted();
      context = context.withTracer(tracer);

      innerFuture = innerCallable.futureCall(request, context);
    }

    ApiFutures.addCallback(innerFuture, new ApiFutureCallback<ResponseT>() {
      @Override
      public void onFailure(Throwable throwable) {
        tracer.operationFailed(throwable);
      }

      @Override
      public void onSuccess(ResponseT responseT) {
        tracer.operationSucceeded();
      }
    }, MoreExecutors.directExecutor());

    return innerFuture;
  }


}
