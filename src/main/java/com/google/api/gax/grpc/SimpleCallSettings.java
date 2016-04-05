package com.google.api.gax.grpc;

import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class with generic typing that could used to configrue a simple api method
 * or create the callable object, which can be directly operated against an API.
 */
public class SimpleCallSettings<RequestT, ResponseT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {

  SimpleCallSettings(Builder<RequestT, ResponseT> builder) {
    super(builder);
  }

  ApiCallable<RequestT, ResponseT> create(
      ServiceApiSettings.Builder serviceSettings) throws IOException {
    ApiCallable<RequestT, ResponseT> callable =new ApiCallable<>(createFutureCallable(), this);
    ManagedChannel channel = serviceSettings.getOrBuildChannel();
    ScheduledExecutorService executor = serviceSettings.getOrBuildExecutor();

    if (retryableCodes != null) {
      callable = callable.retryableOn(ImmutableSet.copyOf(retryableCodes));
    }

    if (retrySettingsBuilder != null) {
      callable = callable.retrying(retrySettingsBuilder.build(), executor);
    }

    callable = callable.bind(channel);
    return callable;
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
    return new Builder<RequestT, ResponseT>(grpcMethodDescriptor);
  }

  @Override
  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<RequestT, ResponseT>(methodDescriptor);
  }

  public static class Builder<RequestT, ResponseT>
      extends ApiCallSettingsTyped.Builder<RequestT, ResponseT> {

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      super(grpcMethodDescriptor);
    }

    @Override
    public SimpleCallSettings<RequestT, ResponseT> build() {
      return new SimpleCallSettings<>(this);
    }
  }

}
