package com.google.api.gax.grpc;

import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;

import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.io.IOException;
import java.util.Set;

/**
 * A settings class which can be used to configure a bundling method
 * or create the bundling callable object, which can make API method calls.
 */
public class BundlingCallSettings<RequestT, ResponseT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {
  private final BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
  private final BundlingSettings bundlingSettings;
  private BundlerFactory<RequestT, ResponseT> bundlerFactory;

  /**
   * Package-private
   */
  ApiCallable<RequestT, ResponseT> create(
      ServiceApiSettings serviceSettings) throws IOException {
    ApiCallable<RequestT, ResponseT> baseCallable = createBaseCallable(serviceSettings);
    bundlerFactory = new BundlerFactory<>(bundlingDescriptor, bundlingSettings);
    return baseCallable.bundling(bundlingDescriptor, bundlerFactory);
  }

  public BundlerFactory<RequestT, ResponseT> getBundlerFactory() {
    return bundlerFactory;
  }

  private BundlingCallSettings(
      ImmutableSet<Status.Code> retryableCodes,
      RetrySettings retrySettings,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor,
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlingSettings bundlingSettings) {
    super(retryableCodes, retrySettings, methodDescriptor);
    this.bundlingDescriptor = bundlingDescriptor;
    this.bundlingSettings = bundlingSettings;
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder(
      MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor) {
    return new Builder<RequestT, ResponseT>(grpcMethodDescriptor, bundlingDescriptor);
  }

  @Override
  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<RequestT, ResponseT>(getMethodDescriptor(), bundlingDescriptor);
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
    public BundlingCallSettings<RequestT, ResponseT> build() {
      return new BundlingCallSettings<>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          getMethodDescriptor(),
          bundlingDescriptor,
          bundlingSettingsBuilder.build()
      );
    }
  }
}
