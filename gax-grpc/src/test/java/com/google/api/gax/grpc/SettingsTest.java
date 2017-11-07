/*
 * Copyright 2016, Google Inc. All rights reserved.
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
package com.google.api.gax.grpc;

import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
import com.google.api.gax.paging.PagedListResponse;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiClientHeaderProvider;
import com.google.api.gax.rpc.BatchingCallSettings;
import com.google.api.gax.rpc.BatchingDescriptor;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.ClientSettings;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.PagedCallSettings;
import com.google.api.gax.rpc.PagedListResponseFactory;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import io.grpc.MethodDescriptor;
import java.io.IOException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class SettingsTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static class FakeSettings extends ClientSettings {

    private interface FakePagedListResponse extends PagedListResponse<Integer> {}

    @SuppressWarnings("unchecked")
    private static final MethodDescriptor<Integer, Integer> fakeMethodMethodDescriptor =
        FakeMethodDescriptor.create();

    @SuppressWarnings("unchecked")
    private static final PagedListResponseFactory<Integer, Integer, FakePagedListResponse>
        fakePagedListResponseFactory = Mockito.mock(PagedListResponseFactory.class);

    @SuppressWarnings("unchecked")
    private static final BatchingDescriptor<Integer, Integer> FAKE_BATCHING_DESCRIPTOR =
        Mockito.mock(BatchingDescriptor.class);

    private static final String DEFAULT_GAPIC_NAME = "gapic";
    public static final String DEFAULT_SERVICE_ADDRESS = "pubsub-experimental.googleapis.com";
    public static final int DEFAULT_SERVICE_PORT = 443;
    public static final String DEFAULT_SERVICE_ENDPOINT =
        DEFAULT_SERVICE_ADDRESS + ':' + DEFAULT_SERVICE_PORT;
    public static final ImmutableList<String> DEFAULT_SERVICE_SCOPES =
        ImmutableList.<String>builder()
            .add("https://www.googleapis.com/auth/pubsub")
            .add("https://www.googleapis.com/auth/cloud-platform")
            .build();

    private static final ImmutableMap<String, ImmutableSet<StatusCode.Code>>
        RETRYABLE_CODE_DEFINITIONS;

    static {
      ImmutableMap.Builder<String, ImmutableSet<StatusCode.Code>> definitions =
          ImmutableMap.builder();
      definitions.put(
          "idempotent",
          ImmutableSet.copyOf(
              Lists.newArrayList(StatusCode.Code.DEADLINE_EXCEEDED, StatusCode.Code.UNAVAILABLE)));
      definitions.put("non_idempotent", ImmutableSet.copyOf(Lists.<StatusCode.Code>newArrayList()));
      RETRYABLE_CODE_DEFINITIONS = definitions.build();
    }

    private static final ImmutableMap<String, RetrySettings> RETRY_PARAM_DEFINITIONS;

    static {
      ImmutableMap.Builder<String, RetrySettings> definitions = ImmutableMap.builder();
      RetrySettings settings = null;
      settings =
          RetrySettings.newBuilder()
              .setInitialRetryDelay(Duration.ofMillis(100L))
              .setRetryDelayMultiplier(1.2)
              .setMaxRetryDelay(Duration.ofMillis(1000L))
              .setInitialRpcTimeout(Duration.ofMillis(2000L))
              .setRpcTimeoutMultiplier(1.5)
              .setMaxRpcTimeout(Duration.ofMillis(30000L))
              .setTotalTimeout(Duration.ofMillis(45000L))
              .build();
      definitions.put("default", settings);
      RETRY_PARAM_DEFINITIONS = definitions.build();
    }

    private final UnaryCallSettings<Integer, Integer> fakeMethodSimple;
    private final PagedCallSettings<Integer, Integer, FakePagedListResponse> fakePagedMethod;
    private final BatchingCallSettings<Integer, Integer> fakeMethodBatching;

    public UnaryCallSettings<Integer, Integer> fakeMethodSimple() {
      return fakeMethodSimple;
    }

    public PagedCallSettings<Integer, Integer, FakePagedListResponse> fakePagedMethod() {
      return fakePagedMethod;
    }

    public BatchingCallSettings<Integer, Integer> fakeMethodBatching() {
      return fakeMethodBatching;
    }

    public static GoogleCredentialsProvider.Builder defaultCredentialsProviderBuilder() {
      return GoogleCredentialsProvider.newBuilder().setScopesToApply(DEFAULT_SERVICE_SCOPES);
    }

    public static InstantiatingGrpcChannelProvider.Builder defaultChannelProviderBuilder() {
      return InstantiatingGrpcChannelProvider.newBuilder().setEndpoint(DEFAULT_SERVICE_ENDPOINT);
    }

    public static InstantiatingExecutorProvider.Builder defaultExecutorProviderBuilder() {
      return InstantiatingExecutorProvider.newBuilder();
    }

    /** Returns a builder for the default TransportChannelProvider for this service. */
    public static InstantiatingGrpcChannelProvider.Builder defaultGrpcChannelProviderBuilder() {
      return InstantiatingGrpcChannelProvider.newBuilder().setEndpoint(DEFAULT_SERVICE_ENDPOINT);
    }

    public static ApiClientHeaderProvider.Builder defaultGoogleServiceHeaderProviderBuilder() {
      return ApiClientHeaderProvider.newBuilder()
          .setGeneratorHeader(DEFAULT_GAPIC_NAME, "0.10.0")
          .setApiClientHeaderLineKey("x-goog-api-client")
          .addApiClientHeaderLineData(GrpcExtraHeaderData.getXGoogApiClientData());
    }

    public static TransportChannelProvider defaultTransportChannelProvider() {
      return defaultGrpcChannelProviderBuilder().build();
    }

    public static Builder newBuilder() {
      return Builder.createDefault();
    }

    public Builder toBuilder() {
      return new Builder(this);
    }

    private FakeSettings(Builder settingsBuilder) throws IOException {
      super(
          settingsBuilder.getExecutorProvider(),
          settingsBuilder.getTransportChannelProvider(),
          settingsBuilder.getCredentialsProvider(),
          settingsBuilder.getHeaderProvider(),
          settingsBuilder.getClock());

      this.fakeMethodSimple = settingsBuilder.fakeMethodSimple().build();
      this.fakePagedMethod = settingsBuilder.fakePagedMethod().build();
      this.fakeMethodBatching = settingsBuilder.fakeMethodBatching().build();
    }

    private static class Builder extends ClientSettings.Builder {

      private UnaryCallSettings.Builder<Integer, Integer> fakeMethodSimple;
      private PagedCallSettings.Builder<Integer, Integer, FakePagedListResponse> fakePagedMethod;
      private BatchingCallSettings.Builder<Integer, Integer> fakeMethodBatching;

      private Builder() {
        super((ClientContext) null);

        fakeMethodSimple = UnaryCallSettings.newUnaryCallSettingsBuilder();
        fakePagedMethod = PagedCallSettings.newBuilder(fakePagedListResponseFactory);
        fakeMethodBatching =
            BatchingCallSettings.newBuilder(FAKE_BATCHING_DESCRIPTOR)
                .setBatchingSettings(BatchingSettings.newBuilder().build());
      }

      private static Builder createDefault() {
        Builder builder = new Builder();
        builder.setTransportChannelProvider(defaultTransportChannelProvider());
        builder.setExecutorProvider(defaultExecutorProviderBuilder().build());
        builder.setCredentialsProvider(defaultCredentialsProviderBuilder().build());
        builder.setHeaderProvider(defaultGoogleServiceHeaderProviderBuilder().build());

        builder
            .fakeMethodSimple()
            .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
            .setRetrySettings(RETRY_PARAM_DEFINITIONS.get("default"));

        builder
            .fakePagedMethod()
            .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
            .setRetrySettings(RETRY_PARAM_DEFINITIONS.get("default"));

        builder
            .fakeMethodBatching()
            .setBatchingSettings(
                BatchingSettings.newBuilder()
                    .setElementCountThreshold(800L)
                    .setRequestByteThreshold(8388608L)
                    .setDelayThreshold(Duration.ofMillis(100))
                    .build());
        builder
            .fakeMethodBatching()
            .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
            .setRetrySettings(RETRY_PARAM_DEFINITIONS.get("default"));

        return builder;
      }

      private Builder(FakeSettings settings) {
        super(settings);

        fakeMethodSimple = settings.fakeMethodSimple().toBuilder();
        fakePagedMethod = settings.fakePagedMethod().toBuilder();
        fakeMethodBatching = settings.fakeMethodBatching().toBuilder();
      }

      @Override
      public Builder setTransportChannelProvider(
          TransportChannelProvider transportChannelProvider) {
        super.setTransportChannelProvider(transportChannelProvider);
        return this;
      }

      @Override
      public Builder setExecutorProvider(ExecutorProvider executorProvider) {
        super.setExecutorProvider(executorProvider);
        return this;
      }

      @Override
      public Builder setCredentialsProvider(CredentialsProvider credentialsProvider) {
        super.setCredentialsProvider(credentialsProvider);
        return this;
      }

      @Override
      public Builder setHeaderProvider(HeaderProvider headerProvider) {
        super.setHeaderProvider(headerProvider);
        return this;
      }

      @Override
      public FakeSettings build() throws IOException {
        return new FakeSettings(this);
      }

      public UnaryCallSettings.Builder<Integer, Integer> fakeMethodSimple() {
        return fakeMethodSimple;
      }

      public PagedCallSettings.Builder<Integer, Integer, FakePagedListResponse> fakePagedMethod() {
        return fakePagedMethod;
      }

      public BatchingCallSettings.Builder<Integer, Integer> fakeMethodBatching() {
        return fakeMethodBatching;
      }
    }
  }

  //RetrySettings
  // ====

  @Test
  public void retrySettingsMerge() {
    RetrySettings.Builder builder =
        RetrySettings.newBuilder()
            .setTotalTimeout(Duration.ofMillis(45000))
            .setInitialRpcTimeout(Duration.ofMillis(2000))
            .setRpcTimeoutMultiplier(1.5)
            .setMaxRpcTimeout(Duration.ofMillis(30000))
            .setInitialRetryDelay(Duration.ofMillis(100))
            .setRetryDelayMultiplier(1.2)
            .setMaxRetryDelay(Duration.ofMillis(1000));
    RetrySettings.Builder mergedBuilder = RetrySettings.newBuilder();
    mergedBuilder.merge(builder);

    RetrySettings settingsA = builder.build();
    RetrySettings settingsB = mergedBuilder.build();

    Truth.assertThat(settingsA.getTotalTimeout()).isEqualTo(settingsB.getTotalTimeout());
    Truth.assertThat(settingsA.getInitialRetryDelay()).isEqualTo(settingsB.getInitialRetryDelay());
    Truth.assertThat(settingsA.getRpcTimeoutMultiplier())
        .isWithin(0)
        .of(settingsB.getRpcTimeoutMultiplier());
    Truth.assertThat(settingsA.getMaxRpcTimeout()).isEqualTo(settingsB.getMaxRpcTimeout());
    Truth.assertThat(settingsA.getInitialRetryDelay()).isEqualTo(settingsB.getInitialRetryDelay());
    Truth.assertThat(settingsA.getRetryDelayMultiplier())
        .isWithin(0)
        .of(settingsB.getRetryDelayMultiplier());
    Truth.assertThat(settingsA.getMaxRetryDelay()).isEqualTo(settingsB.getMaxRetryDelay());
  }

  //GrpcTransportProvider
  // ====

  @Test
  public void customCredentials() throws IOException {
    Credentials credentials = Mockito.mock(Credentials.class);

    FakeSettings settings =
        FakeSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();

    TransportChannelProvider actualChannelProvider = settings.getTransportChannelProvider();
    Truth.assertThat(actualChannelProvider).isInstanceOf(InstantiatingGrpcChannelProvider.class);
    InstantiatingGrpcChannelProvider actualInstChPr =
        (InstantiatingGrpcChannelProvider) actualChannelProvider;

    Truth.assertThat(actualInstChPr.getEndpoint()).isEqualTo(FakeSettings.DEFAULT_SERVICE_ENDPOINT);
    //TODO(michaelbausor): create JSON with credentials and define GOOGLE_APPLICATION_CREDENTIALS
    // environment variable to allow travis build to access application default credentials
    Truth.assertThat(settings.getCredentialsProvider().getCredentials()).isSameAs(credentials);
  }

  @Test
  public void channelCustomCredentialScopes() throws IOException {
    ImmutableList<String> inputScopes =
        ImmutableList.<String>builder().add("https://www.googleapis.com/auth/fakeservice").build();

    CredentialsProvider credentialsProvider =
        FakeSettings.defaultCredentialsProviderBuilder().setScopesToApply(inputScopes).build();
    FakeSettings settings =
        FakeSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();

    TransportChannelProvider actualChannelProvider = settings.getTransportChannelProvider();
    Truth.assertThat(actualChannelProvider).isInstanceOf(InstantiatingGrpcChannelProvider.class);
    InstantiatingGrpcChannelProvider actualInstChPr =
        (InstantiatingGrpcChannelProvider) actualChannelProvider;

    Truth.assertThat(actualInstChPr.getEndpoint()).isEqualTo(FakeSettings.DEFAULT_SERVICE_ENDPOINT);

    CredentialsProvider actualCredentialsProvider = settings.getCredentialsProvider();
    Truth.assertThat(actualCredentialsProvider).isInstanceOf(GoogleCredentialsProvider.class);
    GoogleCredentialsProvider googCredProv = (GoogleCredentialsProvider) actualCredentialsProvider;

    Truth.assertThat(googCredProv.getScopesToApply()).isEqualTo(inputScopes);

    //TODO(michaelbausor): create JSON with credentials and define GOOGLE_APPLICATION_CREDENTIALS
    // environment variable to allow travis build to access application default credentials
    //Truth.assertThat(connSettings.getCredentials()).isNotNull();
  }

  // CallSettings
  // ====

  @Test
  public void callSettingsToBuilder() throws IOException {
    FakeSettings.Builder builderA = FakeSettings.Builder.createDefault();
    FakeSettings settingsA = builderA.build();
    FakeSettings.Builder builderB = settingsA.toBuilder();
    FakeSettings settingsB = builderB.build();

    assertIsReflectionEqual(builderA, builderB);
    assertIsReflectionEqual(settingsA, settingsB);
  }

  @Test
  public void callSettingsTimeoutNoRetries() throws IOException {
    Duration timeout = Duration.ofMillis(60000);

    FakeSettings.Builder builderA = FakeSettings.Builder.createDefault();
    builderA.fakeMethodSimple().setSimpleTimeoutNoRetries(timeout);
    FakeSettings settingsA = builderA.build();

    FakeSettings.Builder builderB = FakeSettings.Builder.createDefault();
    builderB
        .fakeMethodSimple()
        .setRetryableCodes()
        .setRetrySettings(
            RetrySettings.newBuilder()
                .setTotalTimeout(timeout)
                .setInitialRetryDelay(Duration.ZERO)
                .setRetryDelayMultiplier(1)
                .setMaxRetryDelay(Duration.ZERO)
                .setInitialRpcTimeout(timeout)
                .setRpcTimeoutMultiplier(1)
                .setMaxRpcTimeout(timeout)
                .setMaxAttempts(1)
                .build());
    FakeSettings settingsB = builderB.build();

    assertIsReflectionEqual(builderA, builderB);
    assertIsReflectionEqual(settingsA, settingsB);
  }

  @Test
  public void unaryCallSettingsBuilderBuildDoesNotFailUnsetProperties() throws IOException {
    UnaryCallSettings.Builder<Integer, Integer> builder =
        UnaryCallSettings.newUnaryCallSettingsBuilder();
    builder.build();
  }

  @Test
  public void callSettingsBuildFromTimeoutNoRetries() throws IOException {
    Duration timeout = Duration.ofMillis(60000);

    UnaryCallSettings.Builder<Integer, Integer> builderA =
        UnaryCallSettings.newUnaryCallSettingsBuilder();
    builderA.setSimpleTimeoutNoRetries(timeout);
    UnaryCallSettings<Integer, Integer> settingsA = builderA.build();

    UnaryCallSettings.Builder<Integer, Integer> builderB =
        UnaryCallSettings.newUnaryCallSettingsBuilder();
    builderB
        .setRetryableCodes()
        .setRetrySettings(
            RetrySettings.newBuilder()
                .setTotalTimeout(timeout)
                .setInitialRetryDelay(Duration.ZERO)
                .setRetryDelayMultiplier(1)
                .setMaxRetryDelay(Duration.ZERO)
                .setInitialRpcTimeout(timeout)
                .setRpcTimeoutMultiplier(1)
                .setMaxRpcTimeout(timeout)
                .setMaxAttempts(1)
                .build());
    UnaryCallSettings<Integer, Integer> settingsB = builderB.build();

    assertIsReflectionEqual(builderA, builderB);
    assertIsReflectionEqual(settingsA, settingsB);
  }

  //Reflection Helper Methods
  // ====

  /*
   * The pattern for the assertIsReflectionEqual methods below is to exclude fields that would
   * cause the reflectionEquals check to fail, then explicitly recurse on those fields with another
   * call to assertIsReflectionEqual.
   */

  private static void assertIsReflectionEqual(Object objA, Object objB, String[] excludes) {
    Truth.assertThat(EqualsBuilder.reflectionEquals(objA, objB, excludes)).isTrue();
  }

  private static void assertIsReflectionEqual(Object objA, Object objB) {
    assertIsReflectionEqual(objA, objB, null);
  }

  private static void assertIsReflectionEqual(FakeSettings settingsA, FakeSettings settingsB) {
    assertIsReflectionEqual(
        settingsA,
        settingsB,
        new String[] {
          "fakeMethodSimple",
          "fakePagedMethod",
          "fakeMethodBatching",
          "executorProvider",
          "credentialsProvider",
          "headerProvider",
          "transportChannelProvider",
          "clock"
        });
    assertIsReflectionEqual(settingsA.fakeMethodSimple, settingsB.fakeMethodSimple);
    assertIsReflectionEqual(settingsA.fakePagedMethod, settingsB.fakePagedMethod);
    assertIsReflectionEqual(settingsA.fakeMethodBatching, settingsB.fakeMethodBatching);
    assertIsReflectionEqual(settingsA.getExecutorProvider(), settingsB.getExecutorProvider());
    assertIsReflectionEqual(settingsA.getCredentialsProvider(), settingsB.getCredentialsProvider());
    assertIsReflectionEqual(
        settingsA.getTransportChannelProvider(), settingsB.getTransportChannelProvider());
  }

  private static void assertIsReflectionEqual(
      FakeSettings.Builder builderA, FakeSettings.Builder builderB) {
    assertIsReflectionEqual(
        builderA,
        builderB,
        new String[] {
          "fakeMethodSimple",
          "fakePagedMethod",
          "fakeMethodBatching",
          "executorProvider",
          "credentialsProvider",
          "headerProvider",
          "transportChannelProvider",
          "clock"
        });
    assertIsReflectionEqual(builderA.fakeMethodSimple, builderB.fakeMethodSimple);
    assertIsReflectionEqual(builderA.fakePagedMethod, builderB.fakePagedMethod);
    assertIsReflectionEqual(builderA.fakeMethodBatching, builderB.fakeMethodBatching);
    assertIsReflectionEqual(builderA.getExecutorProvider(), builderB.getExecutorProvider());
    assertIsReflectionEqual(builderA.getCredentialsProvider(), builderB.getCredentialsProvider());
    assertIsReflectionEqual(
        builderA.getTransportChannelProvider(), builderB.getTransportChannelProvider());
  }

  private static void assertIsReflectionEqual(
      UnaryCallSettings.Builder<?, ?> builderA, UnaryCallSettings.Builder<?, ?> builderB) {
    assertIsReflectionEqual(builderA, builderB, new String[] {"retrySettings"});
    assertIsReflectionEqual(builderA.getRetrySettings(), builderB.getRetrySettings());
  }

  private static <RequestT, ResponseT> void assertIsReflectionEqual(
      BatchingCallSettings<RequestT, ResponseT> settingsA,
      BatchingCallSettings<RequestT, ResponseT> settingsB) {
    assertIsReflectionEqual(
        settingsA,
        settingsB,
        new String[] {"retrySettings", "batchingDescriptor", "batchingSettings", "flowController"});
    assertIsReflectionEqual(settingsA.getRetrySettings(), settingsA.getRetrySettings());
    assertIsReflectionEqual(settingsB.getBatchingSettings(), settingsB.getBatchingSettings());
    // TODO compare other batching things (batchingDescriptor, flowController)
  }

  private static <RequestT, ResponseT> void assertIsReflectionEqual(
      BatchingCallSettings.Builder<RequestT, ResponseT> builderA,
      BatchingCallSettings.Builder<RequestT, ResponseT> builderB) {
    assertIsReflectionEqual(
        builderA,
        builderB,
        new String[] {"retrySettings", "batchingDescriptor", "batchingSettings", "flowController"});
    assertIsReflectionEqual(builderA.getRetrySettings(), builderB.getRetrySettings());
    assertIsReflectionEqual(builderA.getBatchingSettings(), builderB.getBatchingSettings());
    // TODO compare other batching things (batchingDescriptor, flowController)
  }
}
