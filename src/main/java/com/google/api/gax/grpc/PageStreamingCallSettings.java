package com.google.api.gax.grpc;

import com.google.api.gax.core.PageAccessor;
import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;

import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.io.IOException;
import java.util.Set;


/**
 * A settings class to configure an ApiCallable for calls to an API method that supports
 * page streaming.
 */
public final class PageStreamingCallSettings<RequestT, ResponseT, ResourceT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {
  private final PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;

  /**
   * Package-private, for use by ApiCallable.
   */
  ApiCallable<RequestT, ResponseT> create(
      ServiceApiSettings serviceSettings) throws IOException {
    return createBaseCallable(serviceSettings);
  }

  /**
   * Package-private, for use by ApiCallable.
   */
  ApiCallable<RequestT, PageAccessor<ResourceT>> createIterable(
      ServiceApiSettings serviceSettings) throws IOException {
    ApiCallable<RequestT, ResponseT> baseCallable = createBaseCallable(serviceSettings);
    return baseCallable.pageStreaming(pageDescriptor);
  }

  public static <RequestT, ResponseT, ResourceT> Builder<RequestT, ResponseT, ResourceT>
      newBuilder(
          MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
          PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    return new Builder<RequestT, ResponseT, ResourceT>(grpcMethodDescriptor, pageDescriptor);
  }

  @Override
  public final Builder<RequestT, ResponseT, ResourceT> toBuilder() {
    return new Builder<RequestT, ResponseT, ResourceT>(getMethodDescriptor(), pageDescriptor);
  }

  private PageStreamingCallSettings(
      ImmutableSet<Status.Code> retryableCodes,
      RetrySettings retrySettings,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor,
      PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    super(retryableCodes, retrySettings, methodDescriptor);
    this.pageDescriptor = pageDescriptor;
  }

  public static class Builder<RequestT, ResponseT, ResourceT>
      extends ApiCallSettingsTyped.Builder<RequestT, ResponseT> {
    private PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;

    public Builder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
        PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
      super(grpcMethodDescriptor);
      this.pageDescriptor = pageDescriptor;
    }

    public PageStreamingDescriptor<RequestT, ResponseT, ResourceT> getPageDescriptor() {
      return pageDescriptor;
    }

    @Override
    public Builder<RequestT, ResponseT, ResourceT> setRetryableCodes(
        Set<Status.Code> retryableCodes) {
      super.setRetryableCodes(retryableCodes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT, ResourceT> setRetryableCodes(Status.Code... codes) {
      super.setRetryableCodes(codes);
      return this;
    }

    @Override
    public Builder<RequestT, ResponseT, ResourceT> setRetrySettingsBuilder(
        RetrySettings.Builder retrySettingsBuilder) {
      super.setRetrySettingsBuilder(retrySettingsBuilder);
      return this;
    }

    @Override
    public PageStreamingCallSettings<RequestT, ResponseT, ResourceT> build() {
      return new PageStreamingCallSettings<RequestT, ResponseT, ResourceT>(
          ImmutableSet.<Status.Code>copyOf(getRetryableCodes()),
          getRetrySettingsBuilder().build(),
          getMethodDescriptor(),
          pageDescriptor);
    }
  }
}
