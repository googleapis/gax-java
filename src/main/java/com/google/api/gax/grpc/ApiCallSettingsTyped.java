package com.google.api.gax.grpc;

import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class with generic typing configure an ApiCallable.
 *
 * <p>This class can be used as the base class that other concrete call settings classes inherit
 * from. We need this intermediate class to add generic typing, because ApiCallSettings is
 * not parameterized for its request and response types.
 *
 * <p>This class is package-private; use the concrete settings classes instead of this class from
 * outside of the package.
 */
abstract class ApiCallSettingsTyped<RequestT, ResponseT> extends ApiCallSettings {

  private final MethodDescriptor<RequestT, ResponseT> methodDescriptor;

  public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
    return methodDescriptor;
  }

  @Override
  public abstract Builder<RequestT, ResponseT> toBuilder();

  protected ApiCallSettingsTyped(ImmutableSet<Status.Code> retryableCodes,
                                 RetrySettings retrySettings,
                                 MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    super(retryableCodes, retrySettings);
    this.methodDescriptor = methodDescriptor;
  }

  protected ApiCallable<RequestT, ResponseT> createBaseCallable(
      ServiceApiSettings serviceSettings) throws IOException {
    ClientCallFactory<RequestT, ResponseT> clientCallFactory =
        new DescriptorClientCallFactory<>(methodDescriptor);
    ApiCallable<RequestT, ResponseT> callable =
        new ApiCallable<>(new DirectCallable<>(clientCallFactory), this);
    ManagedChannel channel = serviceSettings.getChannel();
    ScheduledExecutorService executor = serviceSettings.getExecutor();
    if (getRetryableCodes() != null) {
      callable = callable.retryableOn(ImmutableSet.copyOf(getRetryableCodes()));
    }
    if (getRetrySettings() != null) {
      callable = callable.retrying(getRetrySettings(), executor);
    }
    return callable.bind(channel);
  }

  public abstract static class Builder<RequestT, ResponseT> extends ApiCallSettings.Builder {
    private MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor;

    protected Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      this.grpcMethodDescriptor = grpcMethodDescriptor;
    }

    public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
      return grpcMethodDescriptor;
    }

    @Override
    public abstract ApiCallSettingsTyped<RequestT, ResponseT> build();
  }
}
