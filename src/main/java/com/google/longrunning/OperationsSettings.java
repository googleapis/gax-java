/*
 * Copyright 2017, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.longrunning;

import static com.google.longrunning.PagedResponseWrappers.ListOperationsPagedResponse;

import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.api.gax.core.RetrySettings;
import com.google.api.gax.grpc.CallContext;
import com.google.api.gax.grpc.ChannelProvider;
import com.google.api.gax.grpc.ClientSettings;
import com.google.api.gax.grpc.ExecutorProvider;
import com.google.api.gax.grpc.InstantiatingChannelProvider;
import com.google.api.gax.grpc.InstantiatingExecutorProvider;
import com.google.api.gax.grpc.PagedCallSettings;
import com.google.api.gax.grpc.PagedListDescriptor;
import com.google.api.gax.grpc.PagedListResponseFactory;
import com.google.api.gax.grpc.SimpleCallSettings;
import com.google.api.gax.grpc.UnaryCallSettings;
import com.google.api.gax.grpc.UnaryCallable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.Empty;
import com.google.protobuf.ExperimentalApi;
import io.grpc.Status;
import java.io.IOException;
import javax.annotation.Generated;
import org.joda.time.Duration;

// AUTO-GENERATED DOCUMENTATION AND CLASS
/** Settings class to configure an instance of {@link OperationsClient}. */
@Generated("by GAPIC v0.0.5")
@ExperimentalApi
public class OperationsSettings extends ClientSettings {

  /** The default port of the service. */
  private static final int DEFAULT_SERVICE_PORT = 443;

  private static final String DEFAULT_GAPIC_NAME = "gapic";
  private static final String DEFAULT_GAPIC_VERSION = "";

  private final SimpleCallSettings<GetOperationRequest, Operation> getOperationSettings;
  private final PagedCallSettings<
          ListOperationsRequest, ListOperationsResponse, ListOperationsPagedResponse>
      listOperationsSettings;
  private final SimpleCallSettings<CancelOperationRequest, Empty> cancelOperationSettings;
  private final SimpleCallSettings<DeleteOperationRequest, Empty> deleteOperationSettings;

  /** Returns the object with the settings used for calls to getOperation. */
  public SimpleCallSettings<GetOperationRequest, Operation> getOperationSettings() {
    return getOperationSettings;
  }

  /** Returns the object with the settings used for calls to listOperations. */
  public PagedCallSettings<
          ListOperationsRequest, ListOperationsResponse, ListOperationsPagedResponse>
      listOperationsSettings() {
    return listOperationsSettings;
  }

  /** Returns the object with the settings used for calls to cancelOperation. */
  public SimpleCallSettings<CancelOperationRequest, Empty> cancelOperationSettings() {
    return cancelOperationSettings;
  }

  /** Returns the object with the settings used for calls to deleteOperation. */
  public SimpleCallSettings<DeleteOperationRequest, Empty> deleteOperationSettings() {
    return deleteOperationSettings;
  }

  /** Returns a builder for the default ExecutorProvider for this service. */
  public static InstantiatingExecutorProvider.Builder defaultExecutorProviderBuilder() {
    return InstantiatingExecutorProvider.newBuilder();
  }

  /** Returns the default service port. */
  public static int getDefaultServicePort() {
    return DEFAULT_SERVICE_PORT;
  }

  /** Returns a builder for the default credentials for this service. */
  public static GoogleCredentialsProvider.Builder defaultCredentialsProviderBuilder() {
    return GoogleCredentialsProvider.newBuilder();
  }

  /** Returns a builder for the default ChannelProvider for this service. */
  public static InstantiatingChannelProvider.Builder defaultChannelProviderBuilder() {
    return InstantiatingChannelProvider.newBuilder()
        .setPort(DEFAULT_SERVICE_PORT)
        .setGeneratorHeader(DEFAULT_GAPIC_NAME, getGapicVersion())
        .setCredentialsProvider(defaultCredentialsProviderBuilder().build());
  }

  private static String getGapicVersion() {
    String packageVersion = OperationsSettings.class.getPackage().getImplementationVersion();
    return packageVersion != null ? packageVersion : DEFAULT_GAPIC_VERSION;
  }

  /** Returns a builder for this class with recommended defaults. */
  public static Builder defaultBuilder() {
    return Builder.createDefault();
  }

  /** Returns a new builder for this class. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Returns a builder containing all the values of this settings class. */
  public Builder toBuilder() {
    return new Builder(this);
  }

  private OperationsSettings(Builder settingsBuilder) throws IOException {
    super(settingsBuilder.getExecutorProvider(), settingsBuilder.getChannelProvider());

    getOperationSettings = settingsBuilder.getOperationSettings().build();
    listOperationsSettings = settingsBuilder.listOperationsSettings().build();
    cancelOperationSettings = settingsBuilder.cancelOperationSettings().build();
    deleteOperationSettings = settingsBuilder.deleteOperationSettings().build();
  }

  private static final PagedListDescriptor<ListOperationsRequest, ListOperationsResponse, Operation>
      LIST_OPERATIONS_PAGE_STR_DESC =
          new PagedListDescriptor<ListOperationsRequest, ListOperationsResponse, Operation>() {
            @Override
            public Object emptyToken() {
              return "";
            }

            @Override
            public ListOperationsRequest injectToken(ListOperationsRequest payload, Object token) {
              return ListOperationsRequest.newBuilder(payload).setPageToken((String) token).build();
            }

            @Override
            public ListOperationsRequest injectPageSize(
                ListOperationsRequest payload, int pageSize) {
              return ListOperationsRequest.newBuilder(payload).setPageSize(pageSize).build();
            }

            @Override
            public Integer extractPageSize(ListOperationsRequest payload) {
              return payload.getPageSize();
            }

            @Override
            public Object extractNextToken(ListOperationsResponse payload) {
              return payload.getNextPageToken();
            }

            @Override
            public Iterable<Operation> extractResources(ListOperationsResponse payload) {
              return payload.getOperationsList();
            }
          };

  private static final PagedListResponseFactory<
          ListOperationsRequest, ListOperationsResponse, ListOperationsPagedResponse>
      LIST_OPERATIONS_PAGE_STR_FACT =
          new PagedListResponseFactory<
              ListOperationsRequest, ListOperationsResponse, ListOperationsPagedResponse>() {
            @Override
            public ListOperationsPagedResponse createPagedListResponse(
                UnaryCallable<ListOperationsRequest, ListOperationsResponse> callable,
                ListOperationsRequest request,
                CallContext context) {
              return new ListOperationsPagedResponse(
                  callable, LIST_OPERATIONS_PAGE_STR_DESC, request, context);
            }
          };

  /** Builder for OperationsSettings. */
  public static class Builder extends ClientSettings.Builder {
    private final ImmutableList<UnaryCallSettings.Builder> unaryMethodSettingsBuilders;

    private final SimpleCallSettings.Builder<GetOperationRequest, Operation> getOperationSettings;
    private final PagedCallSettings.Builder<
            ListOperationsRequest, ListOperationsResponse, ListOperationsPagedResponse>
        listOperationsSettings;
    private final SimpleCallSettings.Builder<CancelOperationRequest, Empty> cancelOperationSettings;
    private final SimpleCallSettings.Builder<DeleteOperationRequest, Empty> deleteOperationSettings;

    private static final ImmutableMap<String, ImmutableSet<Status.Code>> RETRYABLE_CODE_DEFINITIONS;

    static {
      ImmutableMap.Builder<String, ImmutableSet<Status.Code>> definitions = ImmutableMap.builder();
      definitions.put(
          "idempotent",
          Sets.immutableEnumSet(
              Lists.<Status.Code>newArrayList(
                  Status.Code.DEADLINE_EXCEEDED, Status.Code.UNAVAILABLE)));
      definitions.put(
          "non_idempotent",
          Sets.immutableEnumSet(Lists.<Status.Code>newArrayList(Status.Code.UNAVAILABLE)));
      RETRYABLE_CODE_DEFINITIONS = definitions.build();
    }

    private static final ImmutableMap<String, RetrySettings.Builder> RETRY_PARAM_DEFINITIONS;

    static {
      ImmutableMap.Builder<String, RetrySettings.Builder> definitions = ImmutableMap.builder();
      RetrySettings.Builder settingsBuilder = null;
      settingsBuilder =
          RetrySettings.newBuilder()
              .setInitialRetryDelay(Duration.millis(100L))
              .setRetryDelayMultiplier(1.3)
              .setMaxRetryDelay(Duration.millis(60000L))
              .setInitialRpcTimeout(Duration.millis(20000L))
              .setRpcTimeoutMultiplier(1.0)
              .setMaxRpcTimeout(Duration.millis(20000L))
              .setTotalTimeout(Duration.millis(600000L));
      definitions.put("default", settingsBuilder);
      RETRY_PARAM_DEFINITIONS = definitions.build();
    }

    private Builder() {
      super((InstantiatingChannelProvider) null);

      getOperationSettings = SimpleCallSettings.newBuilder(OperationsGrpc.METHOD_GET_OPERATION);

      listOperationsSettings =
          PagedCallSettings.newBuilder(
              OperationsGrpc.METHOD_LIST_OPERATIONS, LIST_OPERATIONS_PAGE_STR_FACT);

      cancelOperationSettings =
          SimpleCallSettings.newBuilder(OperationsGrpc.METHOD_CANCEL_OPERATION);

      deleteOperationSettings =
          SimpleCallSettings.newBuilder(OperationsGrpc.METHOD_DELETE_OPERATION);

      unaryMethodSettingsBuilders =
          ImmutableList.<UnaryCallSettings.Builder>of(
              getOperationSettings,
              listOperationsSettings,
              cancelOperationSettings,
              deleteOperationSettings);
    }

    private static Builder createDefault() {
      Builder builder = new Builder();

      builder
          .getOperationSettings()
          .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
          .setRetrySettingsBuilder(RETRY_PARAM_DEFINITIONS.get("default"));

      builder
          .listOperationsSettings()
          .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
          .setRetrySettingsBuilder(RETRY_PARAM_DEFINITIONS.get("default"));

      builder
          .cancelOperationSettings()
          .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
          .setRetrySettingsBuilder(RETRY_PARAM_DEFINITIONS.get("default"));

      builder
          .deleteOperationSettings()
          .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
          .setRetrySettingsBuilder(RETRY_PARAM_DEFINITIONS.get("default"));

      return builder;
    }

    private Builder(OperationsSettings settings) {
      super(settings);

      getOperationSettings = settings.getOperationSettings.toBuilder();
      listOperationsSettings = settings.listOperationsSettings.toBuilder();
      cancelOperationSettings = settings.cancelOperationSettings.toBuilder();
      deleteOperationSettings = settings.deleteOperationSettings.toBuilder();

      unaryMethodSettingsBuilders =
          ImmutableList.<UnaryCallSettings.Builder>of(
              getOperationSettings,
              listOperationsSettings,
              cancelOperationSettings,
              deleteOperationSettings);
    }

    @Override
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      super.setExecutorProvider(executorProvider);
      return this;
    }

    @Override
    public Builder setChannelProvider(ChannelProvider channelProvider) {
      super.setChannelProvider(channelProvider);
      return this;
    }

    /**
     * Applies the given settings to all of the unary API methods in this service. Only values that
     * are non-null will be applied, so this method is not capable of un-setting any values.
     *
     * <p>
     * Note: This method does not support applying settings to streaming methods.
     */
    public Builder applyToAllUnaryMethods(UnaryCallSettings.Builder unaryCallSettings)
        throws Exception {
      super.applyToAllUnaryMethods(unaryMethodSettingsBuilders, unaryCallSettings);
      return this;
    }

    /** Returns the builder for the settings used for calls to getOperation. */
    public SimpleCallSettings.Builder<GetOperationRequest, Operation> getOperationSettings() {
      return getOperationSettings;
    }

    /** Returns the builder for the settings used for calls to listOperations. */
    public PagedCallSettings.Builder<
            ListOperationsRequest, ListOperationsResponse, ListOperationsPagedResponse>
        listOperationsSettings() {
      return listOperationsSettings;
    }

    /** Returns the builder for the settings used for calls to cancelOperation. */
    public SimpleCallSettings.Builder<CancelOperationRequest, Empty> cancelOperationSettings() {
      return cancelOperationSettings;
    }

    /** Returns the builder for the settings used for calls to deleteOperation. */
    public SimpleCallSettings.Builder<DeleteOperationRequest, Empty> deleteOperationSettings() {
      return deleteOperationSettings;
    }

    @Override
    public OperationsSettings build() throws IOException {
      return new OperationsSettings(this);
    }
  }
}
