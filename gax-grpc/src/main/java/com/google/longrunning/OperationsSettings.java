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

import com.google.api.core.ApiFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.PropertiesProvider;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.grpc.GrpcTransport;
import com.google.api.gax.grpc.InstantiatingChannelProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ClientSettings;
import com.google.api.gax.rpc.PageContext;
import com.google.api.gax.rpc.PagedCallSettings;
import com.google.api.gax.rpc.PagedListDescriptor;
import com.google.api.gax.rpc.PagedListResponseFactory;
import com.google.api.gax.rpc.SimpleCallSettings;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.TransportProvider;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.longrunning.stub.GrpcOperationsStub;
import com.google.longrunning.stub.OperationsStub;
import com.google.protobuf.Empty;
import io.grpc.Status;
import java.io.IOException;
import javax.annotation.Generated;
import org.threeten.bp.Duration;

// AUTO-GENERATED DOCUMENTATION AND CLASS
/** Settings class to configure an instance of {@link OperationsClient}. */
@Generated("by GAPIC v0.0.5")
@BetaApi
public class OperationsSettings extends ClientSettings {

  private static final String DEFAULT_GAPIC_NAME = "gapic";
  private static final String DEFAULT_GAPIC_VERSION = "";

  private static final String PROPERTIES_FILE = "/com/google/longrunning/project.properties";
  private static final String META_VERSION_KEY = "artifact.version";

  private static String gapicVersion;

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

  public OperationsStub createStub() throws IOException {
    if (getTransportProvider().getTransportName().equals(GrpcTransport.getGrpcTransportName())) {
      return GrpcOperationsStub.create(this);
    } else {
      throw new UnsupportedOperationException(
          "Transport not supported: " + getTransportProvider().getTransportName());
    }
  }

  /** Returns a builder for the default ExecutorProvider for this service. */
  public static InstantiatingExecutorProvider.Builder defaultExecutorProviderBuilder() {
    return InstantiatingExecutorProvider.newBuilder();
  }

  /** Returns a builder for the default credentials for this service. */
  public static GoogleCredentialsProvider.Builder defaultCredentialsProviderBuilder() {
    return GoogleCredentialsProvider.newBuilder();
  }

  /** Returns a builder for the default ChannelProvider for this service. */
  public static InstantiatingChannelProvider.Builder defaultGrpcChannelProviderBuilder() {
    return InstantiatingChannelProvider.newBuilder()
        .setGeneratorHeader(DEFAULT_GAPIC_NAME, getGapicVersion());
  }

  private static String getGapicVersion() {
    if (gapicVersion == null) {
      gapicVersion =
          PropertiesProvider.loadProperty(
              OperationsSettings.class, PROPERTIES_FILE, META_VERSION_KEY);
      gapicVersion = gapicVersion == null ? DEFAULT_GAPIC_VERSION : gapicVersion;
    }
    return gapicVersion;
  }

  /** Returns a builder for this class with recommended defaults. */
  @Deprecated
  public static Builder defaultBuilder() {
    return Builder.createDefault();
  }

  /**
   * Returns a builder for this class with recommened defaults for API methods, and the given
   * ClientContext used for executor/transport/credentials.
   */
  @Deprecated
  public static Builder defaultBuilder(ClientContext clientContext) {
    return new Builder(clientContext);
  }

  /** Returns a new builder for this class. */
  public static Builder newBuilder() {
    return Builder.createDefault();
  }

  /** Returns a new builder for this class. */
  public static Builder newBuilder(ClientContext clientContext) {
    return new Builder(clientContext);
  }

  /** Returns a builder containing all the values of this settings class. */
  public Builder toBuilder() {
    return new Builder(this);
  }

  private OperationsSettings(Builder settingsBuilder) throws IOException {
    super(
        settingsBuilder.getExecutorProvider(),
        settingsBuilder.getTransportProvider(),
        settingsBuilder.getCredentialsProvider(),
        settingsBuilder.getClock());

    getOperationSettings = settingsBuilder.getOperationSettings().build();
    listOperationsSettings = settingsBuilder.listOperationsSettings().build();
    cancelOperationSettings = settingsBuilder.cancelOperationSettings().build();
    deleteOperationSettings = settingsBuilder.deleteOperationSettings().build();
  }

  private static final PagedListDescriptor<ListOperationsRequest, ListOperationsResponse, Operation>
      LIST_OPERATIONS_PAGE_STR_DESC =
          new PagedListDescriptor<ListOperationsRequest, ListOperationsResponse, Operation>() {
            @Override
            public String emptyToken() {
              return "";
            }

            @Override
            public ListOperationsRequest injectToken(ListOperationsRequest payload, String token) {
              return ListOperationsRequest.newBuilder(payload).setPageToken(token).build();
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
            public String extractNextToken(ListOperationsResponse payload) {
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
            public ApiFuture<ListOperationsPagedResponse> getFuturePagedResponse(
                UnaryCallable<ListOperationsRequest, ListOperationsResponse> callable,
                ListOperationsRequest request,
                ApiCallContext context,
                ApiFuture<ListOperationsResponse> futureResponse) {
              PageContext<ListOperationsRequest, ListOperationsResponse, Operation> pageContext =
                  PageContext.create(callable, LIST_OPERATIONS_PAGE_STR_DESC, request, context);
              return ListOperationsPagedResponse.createAsync(pageContext, futureResponse);
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

    private static final ImmutableMap<String, ImmutableSet<StatusCode>> RETRYABLE_CODE_DEFINITIONS;

    static {
      ImmutableMap.Builder<String, ImmutableSet<StatusCode>> definitions = ImmutableMap.builder();
      definitions.put(
          "idempotent",
          ImmutableSet.copyOf(
              Lists.<StatusCode>newArrayList(
                  GrpcStatusCode.of(Status.Code.DEADLINE_EXCEEDED),
                  GrpcStatusCode.of(Status.Code.UNAVAILABLE))));
      definitions.put("non_idempotent", ImmutableSet.copyOf(Lists.<StatusCode>newArrayList()));
      RETRYABLE_CODE_DEFINITIONS = definitions.build();
    }

    private static final ImmutableMap<String, RetrySettings> RETRY_PARAM_DEFINITIONS;

    static {
      ImmutableMap.Builder<String, RetrySettings> definitions = ImmutableMap.builder();
      RetrySettings settings = null;
      settings =
          RetrySettings.newBuilder()
              .setInitialRetryDelay(Duration.ofMillis(100L))
              .setRetryDelayMultiplier(1.3)
              .setMaxRetryDelay(Duration.ofMillis(60000L))
              .setInitialRpcTimeout(Duration.ofMillis(90000L))
              .setRpcTimeoutMultiplier(1.0)
              .setMaxRpcTimeout(Duration.ofMillis(90000L))
              .setTotalTimeout(Duration.ofMillis(600000L))
              .build();
      definitions.put("default", settings);
      RETRY_PARAM_DEFINITIONS = definitions.build();
    }

    private Builder() {
      this((ClientContext) null);
    }

    private Builder(ClientContext clientContext) {
      super(clientContext);

      getOperationSettings = SimpleCallSettings.newBuilder();

      listOperationsSettings = PagedCallSettings.newBuilder(LIST_OPERATIONS_PAGE_STR_FACT);

      cancelOperationSettings = SimpleCallSettings.newBuilder();

      deleteOperationSettings = SimpleCallSettings.newBuilder();

      unaryMethodSettingsBuilders =
          ImmutableList.<UnaryCallSettings.Builder>of(
              getOperationSettings,
              listOperationsSettings,
              cancelOperationSettings,
              deleteOperationSettings);

      initDefaults(this);
    }

    private static Builder createDefault() {
      Builder builder = new Builder((ClientContext) null);
      return initDefaults(builder);
    }

    private static Builder initDefaults(Builder builder) {

      builder
          .getOperationSettings()
          .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
          .setRetrySettings(RETRY_PARAM_DEFINITIONS.get("default"));

      builder
          .listOperationsSettings()
          .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
          .setRetrySettings(RETRY_PARAM_DEFINITIONS.get("default"));

      builder
          .cancelOperationSettings()
          .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
          .setRetrySettings(RETRY_PARAM_DEFINITIONS.get("default"));

      builder
          .deleteOperationSettings()
          .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
          .setRetrySettings(RETRY_PARAM_DEFINITIONS.get("default"));

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
    public Builder setTransportProvider(TransportProvider transportProvider) {
      super.setTransportProvider(transportProvider);
      return this;
    }

    @Override
    public Builder setCredentialsProvider(CredentialsProvider credentialsProvider) {
      super.setCredentialsProvider(credentialsProvider);
      return this;
    }

    /**
     * Applies the given settings updater function to all of the unary API methods in this service.
     *
     * <p>Note: This method does not support applying settings to streaming methods.
     */
    public Builder applyToAllUnaryMethods(
        ApiFunction<UnaryCallSettings.Builder, Void> settingsUpdater) throws Exception {
      super.applyToAllUnaryMethods(unaryMethodSettingsBuilders, settingsUpdater);
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
