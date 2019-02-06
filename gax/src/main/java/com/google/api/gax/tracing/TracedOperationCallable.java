package com.google.api.gax.tracing;

import com.google.api.core.ApiFuture;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.OperationCallable;
import javax.annotation.Nonnull;

public class TracedOperationCallable<RequestT, ResponseT, MetadataT> extends
    OperationCallable<RequestT, ResponseT, MetadataT> {

  private @Nonnull OperationCallable<RequestT, ResponseT, MetadataT> innerCallable;
  private @Nonnull ApiTracerFactory tracerFactory;
  private @Nonnull SpanName spanName;

  public TracedOperationCallable(
      @Nonnull OperationCallable<RequestT, ResponseT, MetadataT> innerCallable,
      @Nonnull ApiTracerFactory tracerFactory, @Nonnull SpanName spanName) {
    this.innerCallable = innerCallable;
    this.tracerFactory = tracerFactory;
    this.spanName = spanName;
  }

  @Override
  public OperationFuture<ResponseT, MetadataT> futureCall(RequestT request,
      ApiCallContext context) {

    ApiTracer tracer = tracerFactory
        .newTracer(context.getTracer(), spanName, ApiTracer.Type.LongRunning);

    context = context.withTracer(tracer);

    return innerCallable.futureCall(request, context);
  }

  @Override
  public OperationFuture<ResponseT, MetadataT> resumeFutureCall(String operationName,
      ApiCallContext context) {
    ApiTracer tracer = tracerFactory
        .newTracer(context.getTracer(), spanName, ApiTracer.Type.LongRunning);

    context = context.withTracer(tracer);

    return innerCallable.resumeFutureCall(operationName, context);
  }

  @Override
  public ApiFuture<Void> cancel(String operationName, ApiCallContext context) {
    SpanName cancelSpanName = SpanName.of(spanName.getClientName(), spanName.getMethodName() + ".Cancel");

    ApiTracer tracer = tracerFactory
        .newTracer(context.getTracer(), cancelSpanName, ApiTracer.Type.Unary);

    context = context.withTracer(tracer);

    return innerCallable.cancel(operationName, context);
  }
}
