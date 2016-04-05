package com.google.api.gax.grpc;

import com.google.common.collect.ImmutableSet;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;


/**
 * A settings class with generic typing that could used to configrue an page-streaming method
 * or create the page-streaming callable object, which can be directly operated against an API.
 */
public class PageStreamingCallSettings<RequestT, ResponseT, ResourceT>
    extends ApiCallSettingsTyped<RequestT, ResponseT> {
  private PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;

  ApiCallable<RequestT, Iterable<ResourceT>> create(
      ServiceApiSettings.Builder serviceSettingsBuilder) throws IOException {
    PageStreamingCallable<RequestT, ResponseT, ResourceT> pageStreamingCallable =
        new PageStreamingCallable<RequestT, ResponseT, ResourceT>(
            createFutureCallable(),
            pageDescriptor);
    ApiCallable<RequestT, Iterable<ResourceT>> callable =
        new ApiCallable<RequestT, Iterable<ResourceT>>(pageStreamingCallable, this);
    ManagedChannel channel = serviceSettingsBuilder.getOrBuildChannel();
    ScheduledExecutorService executor = serviceSettingsBuilder.getOrBuildExecutor();

    if (retryableCodes != null) {
      callable = callable.retryableOn(ImmutableSet.copyOf(retryableCodes));
    }

    if (retrySettingsBuilder != null) {
      callable = callable.retrying(retrySettingsBuilder.build(), executor);
    }

    callable = callable.bind(channel);
    return callable;
  }

  public static <RequestT, ResponseT, ResourceT> Builder<RequestT, ResponseT, ResourceT>
      newBuilder(
          MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
          PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    return new Builder<RequestT, ResponseT, ResourceT>(grpcMethodDescriptor, pageDescriptor);
  }

  @Override
  public Builder<RequestT, ResponseT, ResourceT> toBuilder() {
    return new Builder<RequestT, ResponseT, ResourceT>(methodDescriptor, pageDescriptor);
  }

  PageStreamingCallSettings(Builder<RequestT, ResponseT, ResourceT> builder) {
    super(builder);
    this.pageDescriptor = builder.getPageDescriptor();
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
    public PageStreamingCallSettings<RequestT, ResponseT, ResourceT> build() {
      return new PageStreamingCallSettings(this);
    }
  }
}
