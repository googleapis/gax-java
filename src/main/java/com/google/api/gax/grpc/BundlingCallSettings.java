package com.google.api.gax.grpc;

import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class with generic typing that could used to configrue an bundling method
 * or create the bundling callable object, which can be directly operated against an API.
 */
public class BundlingCallSettings<RequestT, ResponseT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {
  private BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
  private BundlingSettings bundlingSettings;

  ApiCallable<RequestT, ResponseT> create(ServiceApiSettings.Builder serviceSettings)
      throws IOException {
    BundlerFactory<RequestT, ResponseT> bundlerFactory = null;
    bundlerFactory = new BundlerFactory<>(bundlingDescriptor, bundlingSettings);
    BundlingCallable<RequestT, ResponseT> bundlingCallable =
        new BundlingCallable<>(createFutureCallable(), bundlingDescriptor, bundlerFactory);
    ApiCallable<RequestT, ResponseT> callable =
        new ApiCallable<RequestT, ResponseT>(bundlingCallable, this);
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

  BundlingCallSettings(Builder<RequestT, ResponseT> builder) {
    super(builder);
    bundlingDescriptor = builder.getBundlingDescriptor();
    bundlingSettings = builder.getBundlingSettingsBuilder().build();
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
      BundlingDescriptor<RequestT, ResponseT> bundlingDescripto) {
    return new Builder<RequestT, ResponseT>(grpcMethodDescriptor, bundlingDescripto);
  }

  @Override
  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<RequestT, ResponseT>(methodDescriptor, bundlingDescriptor);
  }

  public static class Builder<RequestT, ResponseT>
      extends ApiCallSettingsTyped.Builder<RequestT, ResponseT> {

    private BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
    private BundlingSettings.Builder bundlingSettingsBuilder;

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
                   BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor) {
      super(grpcMethodDescriptor);
      this.bundlingDescriptor = bundlingDescriptor;
    }

    public BundlingDescriptor<RequestT, ResponseT> getBundlingDescriptor() {
      return bundlingDescriptor;
    }

    public Builder<RequestT, ResponseT> setBundlingSettingsBuilder(
        BundlingSettings.Builder bundlingSettingsBuilder) {
      this.bundlingSettingsBuilder = bundlingSettingsBuilder;
      return this;
    }

    public BundlingSettings.Builder getBundlingSettingsBuilder() {
      return this.bundlingSettingsBuilder;
    }

    @Override
    public BundlingCallSettings<RequestT, ResponseT> build() {
      return new BundlingCallSettings<>(this);
    }
  }
}
