package com.google.api.gax.grpc;

import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;

import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.io.IOException;
import java.util.Set;

/**
 * A settings class which can be used to configure a simple api method
 * or create the callable object, which can make API method calls.
 */
public class SimpleCallSettings<RequestT, ResponseT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {

  /**
   * Package-private
   */
  ApiCallable<RequestT, ResponseT> create(
      ServiceApiSettings.Builder serviceSettingsBuilder) throws IOException {
    return createBaseCallable(serviceSettingsBuilder);
  }

  private SimpleCallSettings(ImmutableSet<Status.Code> retryableCodes,
                             RetrySettings retrySettings,
                             MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    super(retryableCodes, retrySettings, methodDescriptor);
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
    return new Builder<RequestT, ResponseT>(grpcMethodDescriptor);
  }

  @Override
  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<RequestT, ResponseT>(getMethodDescriptor());
  }

  public static class Builder<RequestT, ResponseT>
      extends ApiCallSettingsTyped.Builder {

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      super(grpcMethodDescriptor);
    }

    @Override
    public Builder<RequestT, ResponseT> setRetryableCodes(Set<Status.Code> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT> setRetryableCodes(Status.Code... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT> setRetrySettingsBuilder(
        RetrySettings.Builder retrySettingsBuilder) {
      super.setRetrySettingsBuilder(retrySettingsBuilder);
      return this;
    }

    @Override
    public SimpleCallSettings<RequestT, ResponseT> build() {
      return new SimpleCallSettings<RequestT, ResponseT>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          getMethodDescriptor());
    }
  }
}
