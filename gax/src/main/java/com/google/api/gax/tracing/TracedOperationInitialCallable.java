package com.google.api.gax.tracing;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.logging.Logger;

public class TracedOperationInitialCallable<RequestT> extends TracedUnaryCallable<RequestT, OperationSnapshot> {
  private static final Logger logger = Logger.getLogger(TracedUnaryCallable.class.getName());

  public TracedOperationInitialCallable(
      UnaryCallable<RequestT, OperationSnapshot> innerCallable,
      ApiTracerFactory tracerFactory, SpanName spanName) {
    super(innerCallable, tracerFactory, spanName);
  }

  @Override
  public ApiFuture<OperationSnapshot> futureCall(RequestT request, ApiCallContext context) {
    if (!(context.getTracer() instanceof ApiLroTracer)) {
      return ApiFutures.immediateFailedFuture(
          new IllegalArgumentException("context must contain an ApiLroTracer")
      );
    }
    ApiLroTracer lroTracer = (ApiLroTracer)context.getTracer();

    ApiFuture<OperationSnapshot> future = super.futureCall(request, context);

    ApiFutures.addCallback(future, new InitialCallableFinisher(lroTracer), MoreExecutors.directExecutor());
    return future;
  }

  private static class InitialCallableFinisher implements ApiFutureCallback<OperationSnapshot> {
    private final ApiLroTracer operationTracer;

    private InitialCallableFinisher(ApiLroTracer operationTracer) {
      this.operationTracer = operationTracer;
    }

    @Override
    public void onSuccess(OperationSnapshot result) {
      operationTracer.lroStartSucceeded();
    }

    @Override
    public void onFailure(Throwable t) {
      operationTracer.lroStartFailed(t);
    }
  }
}
