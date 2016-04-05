package com.google.api.gax.grpc;

import io.grpc.MethodDescriptor;

/**
 * A settings class with generic typing that could used to configrue an API method or create
 * the method callable object, which can be directly operated against an API.
 */
class ApiCallSettingsTyped<RequestT, ResponseT> extends ApiCallSettings {
  protected MethodDescriptor<RequestT, ResponseT> methodDescriptor;

  protected FutureCallable<RequestT, ResponseT> createFutureCallable() {
    ClientCallFactory<RequestT, ResponseT> clientCallFactory =
        new DescriptorClientCallFactory<>(methodDescriptor);
    return new DirectCallable<>(clientCallFactory);
  }

  ApiCallSettingsTyped(Builder<RequestT, ResponseT> builder) {
    super(builder);
    this.methodDescriptor = builder.getDescriptor();
  }

  public static class Builder<RequestT, ResponseT>
      extends ApiCallSettings.Builder {
    protected MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor;

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      this.grpcMethodDescriptor = grpcMethodDescriptor;
    }

    public MethodDescriptor<RequestT, ResponseT> getDescriptor() {
      return grpcMethodDescriptor;
    }

    @Override
    public ApiCallSettingsTyped<RequestT, ResponseT> build() {
      return new ApiCallSettingsTyped<RequestT, ResponseT>(this);
    }
  }
}
