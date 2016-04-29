package com.google.api.gax.grpc;

import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A settings class to configure an ApiCallable for calls to a simple API method (i.e. that
 * doesn't support things like page streaming or bundling.)
 */
public final class SimpleCallSettings<RequestT, ResponseT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {

  /**
   * Package-private, for use by ApiCallable.
   */
  ApiCallable<RequestT, ResponseT> create(
      ManagedChannel channel, ScheduledExecutorService executor) {
    return createBaseCallable(channel, executor);
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
  public final Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<RequestT, ResponseT>(this);
  }

  public static class Builder<RequestT, ResponseT>
      extends ApiCallSettingsTyped.Builder<RequestT, ResponseT> {

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      super(grpcMethodDescriptor);
    }

    public Builder(SimpleCallSettings<RequestT, ResponseT> settings) {
      super(settings);
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
