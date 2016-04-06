package com.google.api.gax.grpc;

import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Package-private: Use other concrete settings classes instead of this class from outside of
 * the package.
 * A settings class with generic typing which can be used to configure an API method or create
 * the method callable object. This class can be used as the base class that other concrete
 * call settings classes inherit from.
 */
class ApiCallSettingsTyped<RequestT, ResponseT> extends ApiCallSettings {

  private final MethodDescriptor<RequestT, ResponseT> methodDescriptor;

  public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
    return methodDescriptor;
  }

  protected ApiCallSettingsTyped(ImmutableSet<Status.Code> retryableCodes,
                                 RetrySettings retrySettings,
                                 MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    super(retryableCodes, retrySettings);
    this.methodDescriptor = methodDescriptor;
  }

  protected ApiCallable<RequestT, ResponseT> createBaseCallable(
      ServiceApiSettings.Builder serviceSettingsBuilder) throws IOException {
    ClientCallFactory<RequestT, ResponseT> clientCallFactory =
        new DescriptorClientCallFactory<>(methodDescriptor);
    ApiCallable<RequestT, ResponseT> callable =
        new ApiCallable<>(new DirectCallable<>(clientCallFactory), this);
    ManagedChannel channel = serviceSettingsBuilder.getOrBuildChannel();
    ScheduledExecutorService executor = serviceSettingsBuilder.getOrBuildExecutor();
    if (getRetryableCodes() != null) {
      callable = callable.retryableOn(ImmutableSet.copyOf(getRetryableCodes()));
    }
    if (getRetrySettings() != null) {
      callable = callable.retrying(getRetrySettings(), executor);
    }
    return callable.bind(channel);
  }

  public static class Builder<RequestT, ResponseT> extends ApiCallSettings.Builder {
    private MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor;

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      this.grpcMethodDescriptor = grpcMethodDescriptor;
    }

    public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
      return grpcMethodDescriptor;
    }

    @Override
    public ApiCallSettingsTyped<RequestT, ResponseT> build() {
      return new ApiCallSettingsTyped<RequestT, ResponseT>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          grpcMethodDescriptor);
    }
  }
}
