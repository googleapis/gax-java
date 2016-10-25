/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

import com.google.api.gax.core.ConnectionSettings;
import com.google.api.gax.core.PagedListResponse;
import com.google.api.gax.core.RetrySettings;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Tests for {@link ServiceApiSettings}. */
@RunWith(JUnit4.class)
public class SettingsTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static class FakeSettings extends ServiceApiSettings {

    private interface FakePagedListResponse extends PagedListResponse<Integer, Integer, Integer> {}

    @SuppressWarnings("unchecked")
    private static final MethodDescriptor<Integer, Integer> fakeMethodMethodDescriptor =
        Mockito.mock(MethodDescriptor.class);

    @SuppressWarnings("unchecked")
    private static final PagedListResponseFactory<Integer, Integer, FakePagedListResponse>
        fakePagedListResponseFactory = Mockito.mock(PagedListResponseFactory.class);

    @SuppressWarnings("unchecked")
    private static final BundlingDescriptor<Integer, Integer> fakeBundlingDescriptor =
        Mockito.mock(BundlingDescriptor.class);

    public static final String DEFAULT_SERVICE_ADDRESS = "pubsub-experimental.googleapis.com";
    public static final int DEFAULT_SERVICE_PORT = 443;
    public static final ImmutableList<String> DEFAULT_SERVICE_SCOPES =
        ImmutableList.<String>builder()
            .add("https://www.googleapis.com/auth/pubsub")
            .add("https://www.googleapis.com/auth/cloud-platform")
            .build();
    public static final ConnectionSettings DEFAULT_CONNECTION_SETTINGS =
        ConnectionSettings.newBuilder()
            .setServiceAddress(DEFAULT_SERVICE_ADDRESS)
            .setPort(DEFAULT_SERVICE_PORT)
            .provideCredentialsWith(DEFAULT_SERVICE_SCOPES)
            .build();

    private static final ImmutableMap<String, ImmutableSet<Status.Code>> RETRYABLE_CODE_DEFINITIONS;

    static {
      ImmutableMap.Builder<String, ImmutableSet<Status.Code>> definitions = ImmutableMap.builder();
      definitions.put(
          "idempotent",
          Sets.immutableEnumSet(
              Lists.<Status.Code>newArrayList(
                  Status.Code.DEADLINE_EXCEEDED, Status.Code.UNAVAILABLE)));
      definitions.put("non_idempotent", Sets.immutableEnumSet(Lists.<Status.Code>newArrayList()));
      RETRYABLE_CODE_DEFINITIONS = definitions.build();
    }

    private static final ImmutableMap<String, RetrySettings.Builder> RETRY_PARAM_DEFINITIONS;

    static {
      ImmutableMap.Builder<String, RetrySettings.Builder> definitions = ImmutableMap.builder();
      RetrySettings.Builder settingsBuilder = null;
      settingsBuilder =
          RetrySettings.newBuilder()
              .setInitialRetryDelay(Duration.millis(100L))
              .setRetryDelayMultiplier(1.2)
              .setMaxRetryDelay(Duration.millis(1000L))
              .setInitialRpcTimeout(Duration.millis(2000L))
              .setRpcTimeoutMultiplier(1.5)
              .setMaxRpcTimeout(Duration.millis(30000L))
              .setTotalTimeout(Duration.millis(45000L));
      definitions.put("default", settingsBuilder);
      RETRY_PARAM_DEFINITIONS = definitions.build();
    }

    private final SimpleCallSettings<Integer, Integer> fakeMethodSimple;
    private final PagedCallSettings<Integer, Integer, FakePagedListResponse> fakePagedMethod;
    private final BundlingCallSettings<Integer, Integer> fakeMethodBundling;

    public SimpleCallSettings<Integer, Integer> fakeMethodSimple() {
      return fakeMethodSimple;
    }

    public PagedCallSettings<Integer, Integer, FakePagedListResponse> fakePagedMethod() {
      return fakePagedMethod;
    }

    public BundlingCallSettings<Integer, Integer> fakeMethodBundling() {
      return fakeMethodBundling;
    }

    public static Builder defaultBuilder() {
      return Builder.createDefault();
    }

    public Builder toBuilder() {
      return new Builder(this);
    }

    private FakeSettings(Builder settingsBuilder) throws IOException {
      super(
          settingsBuilder.getChannelProvider(),
          settingsBuilder.getExecutorProvider(),
          settingsBuilder.getGeneratorName(),
          settingsBuilder.getGeneratorVersion(),
          settingsBuilder.getClientLibName(),
          settingsBuilder.getClientLibVersion());

      this.fakeMethodSimple = settingsBuilder.fakeMethodSimple().build();
      this.fakePagedMethod = settingsBuilder.fakePagedMethod().build();
      this.fakeMethodBundling = settingsBuilder.fakeMethodBundling().build();
    }

    private static class Builder extends ServiceApiSettings.Builder {

      private SimpleCallSettings.Builder<Integer, Integer> fakeMethodSimple;
      private PagedCallSettings.Builder<Integer, Integer, FakePagedListResponse> fakePagedMethod;
      private BundlingCallSettings.Builder<Integer, Integer> fakeMethodBundling;

      private Builder() {
        super(DEFAULT_CONNECTION_SETTINGS);

        fakeMethodSimple = SimpleCallSettings.newBuilder(fakeMethodMethodDescriptor);
        fakePagedMethod =
            PagedCallSettings.newBuilder(fakeMethodMethodDescriptor, fakePagedListResponseFactory);
        fakeMethodBundling =
            BundlingCallSettings.newBuilder(fakeMethodMethodDescriptor, fakeBundlingDescriptor)
                .setBundlingSettingsBuilder(BundlingSettings.newBuilder());
      }

      private static Builder createDefault() {
        Builder builder = new Builder();
        builder
            .fakeMethodSimple()
            .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
            .setRetrySettingsBuilder(RETRY_PARAM_DEFINITIONS.get("default"));

        builder
            .fakePagedMethod()
            .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
            .setRetrySettingsBuilder(RETRY_PARAM_DEFINITIONS.get("default"));

        builder
            .fakeMethodBundling()
            .getBundlingSettingsBuilder()
            .setElementCountThreshold(800)
            .setElementCountLimit(1000)
            .setRequestByteThreshold(8388608)
            .setRequestByteLimit(10485760)
            .setDelayThreshold(Duration.millis(100))
            .setBlockingCallCountThreshold(1);
        builder
            .fakeMethodBundling()
            .setRetryableCodes(RETRYABLE_CODE_DEFINITIONS.get("idempotent"))
            .setRetrySettingsBuilder(RETRY_PARAM_DEFINITIONS.get("default"));

        return builder;
      }

      private Builder(FakeSettings settings) {
        super(settings);

        fakeMethodSimple = settings.fakeMethodSimple().toBuilder();
        fakePagedMethod = settings.fakePagedMethod().toBuilder();
        fakeMethodBundling = settings.fakeMethodBundling().toBuilder();
      }

      @Override
      public FakeSettings build() throws IOException {
        return new FakeSettings(this);
      }

      @Override
      protected ConnectionSettings.Builder getDefaultConnectionSettingsBuilder() {
        return DEFAULT_CONNECTION_SETTINGS.toBuilder();
      }

      @Override
      public Builder provideExecutorWith(
          final ScheduledExecutorService executor, boolean shouldAutoClose) {
        super.provideExecutorWith(executor, shouldAutoClose);
        return this;
      }

      @Override
      public Builder provideChannelWith(ManagedChannel channel, boolean shouldAutoClose) {
        super.provideChannelWith(channel, shouldAutoClose);
        return this;
      }

      @Override
      public Builder provideChannelWith(ConnectionSettings settings) {
        super.provideChannelWith(settings);
        return this;
      }

      @Override
      public Builder provideChannelWith(Credentials myCredentials) {
        super.provideChannelWith(myCredentials);
        return this;
      }

      @Override
      public Builder provideChannelWith(List<String> scopes) {
        super.provideChannelWith(scopes);
        return this;
      }

      public SimpleCallSettings.Builder<Integer, Integer> fakeMethodSimple() {
        return fakeMethodSimple;
      }

      public PagedCallSettings.Builder<Integer, Integer, FakePagedListResponse> fakePagedMethod() {
        return fakePagedMethod;
      }

      public BundlingCallSettings.Builder<Integer, Integer> fakeMethodBundling() {
        return fakeMethodBundling;
      }
    }
  }

  //RetrySettings
  // ====

  @Test
  public void retrySettingsMerge() {
    RetrySettings.Builder builder =
        RetrySettings.newBuilder()
            .setTotalTimeout(Duration.millis(45000))
            .setInitialRpcTimeout(Duration.millis(2000))
            .setRpcTimeoutMultiplier(1.5)
            .setMaxRpcTimeout(Duration.millis(30000))
            .setInitialRetryDelay(Duration.millis(100))
            .setRetryDelayMultiplier(1.2)
            .setMaxRetryDelay(Duration.millis(1000));
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

  //ServiceApiSettings
  // ====

  @Test
  public void channelCustomCredentials() throws IOException {
    Credentials credentials = Mockito.mock(Credentials.class);
    FakeSettings settings = FakeSettings.defaultBuilder().provideChannelWith(credentials).build();
    ConnectionSettings connSettings = settings.getChannelProvider().connectionSettings();
    Truth.assertThat(connSettings.getServiceAddress())
        .isEqualTo(FakeSettings.DEFAULT_CONNECTION_SETTINGS.getServiceAddress());
    Truth.assertThat(connSettings.getPort())
        .isEqualTo(FakeSettings.DEFAULT_CONNECTION_SETTINGS.getPort());
    //TODO(michaelbausor): create JSON with credentials and define GOOGLE_APPLICATION_CREDENTIALS
    // environment variable to allow travis build to access application default credentials
    //Truth.assertThat(connSettings.getCredentials()).isEqualTo(credentials);
  }

  @Test
  public void channelCustomCredentialScopes() throws IOException {
    ImmutableList<String> scopes =
        ImmutableList.<String>builder().add("https://www.googleapis.com/auth/fakeservice").build();
    FakeSettings settings = FakeSettings.defaultBuilder().provideChannelWith(scopes).build();
    ConnectionSettings connSettings = settings.getChannelProvider().connectionSettings();
    Truth.assertThat(connSettings.getServiceAddress())
        .isEqualTo(FakeSettings.DEFAULT_CONNECTION_SETTINGS.getServiceAddress());
    Truth.assertThat(connSettings.getPort())
        .isEqualTo(FakeSettings.DEFAULT_CONNECTION_SETTINGS.getPort());
    //TODO(michaelbausor): create JSON with credentials and define GOOGLE_APPLICATION_CREDENTIALS
    // environment variable to allow travis build to access application default credentials
    //Truth.assertThat(connSettings.getCredentials()).isNotNull();
  }

  @Test
  public void fixedChannelAutoClose() throws IOException {
    thrown.expect(IllegalStateException.class);
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    FakeSettings settings = FakeSettings.defaultBuilder().provideChannelWith(channel, true).build();
    ChannelProvider channelProvider = settings.getChannelProvider();
    ScheduledExecutorService executor = settings.getExecutorProvider().getOrBuildExecutor();
    channelProvider.getOrBuildChannel(executor);
    channelProvider.getOrBuildChannel(executor);
  }

  @Test
  public void fixedChannelNoAutoClose() throws IOException {
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    FakeSettings settings =
        FakeSettings.defaultBuilder().provideChannelWith(channel, false).build();
    ChannelProvider channelProvider = settings.getChannelProvider();
    ScheduledExecutorService executor = settings.getExecutorProvider().getOrBuildExecutor();
    ManagedChannel channelA = channelProvider.getOrBuildChannel(executor);
    ManagedChannel channelB = channelProvider.getOrBuildChannel(executor);
    Truth.assertThat(channelA).isEqualTo(channelB);
  }

  @Test
  public void defaultExecutor() throws IOException {
    FakeSettings settings = FakeSettings.defaultBuilder().build();
    ExecutorProvider executorProvider = settings.getExecutorProvider();
    ScheduledExecutorService executorA = executorProvider.getOrBuildExecutor();
    ScheduledExecutorService executorB = executorProvider.getOrBuildExecutor();
    Truth.assertThat(executorA).isNotEqualTo(executorB);
  }

  @Test
  public void fixedExecutorAutoClose() throws IOException {
    thrown.expect(IllegalStateException.class);
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
    FakeSettings settings =
        FakeSettings.defaultBuilder().provideExecutorWith(executor, true).build();
    ExecutorProvider executorProvider = settings.getExecutorProvider();
    executorProvider.getOrBuildExecutor();
    executorProvider.getOrBuildExecutor();
  }

  @Test
  public void fixedExecutorNoAutoClose() throws IOException {
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
    FakeSettings settings =
        FakeSettings.defaultBuilder().provideExecutorWith(executor, false).build();
    ExecutorProvider executorProvider = settings.getExecutorProvider();
    ScheduledExecutorService executorA = executorProvider.getOrBuildExecutor();
    ScheduledExecutorService executorB = executorProvider.getOrBuildExecutor();
    Truth.assertThat(executorA).isEqualTo(executorB);
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
    Duration timeout = Duration.millis(60000);

    FakeSettings.Builder builderA = FakeSettings.Builder.createDefault();
    builderA.fakeMethodSimple().setSimpleTimeoutNoRetries(timeout);
    FakeSettings settingsA = builderA.build();

    FakeSettings.Builder builderB = FakeSettings.Builder.createDefault();
    builderB
        .fakeMethodSimple()
        .setRetryableCodes()
        .setRetrySettingsBuilder(
            RetrySettings.newBuilder()
                .setTotalTimeout(timeout)
                .setInitialRetryDelay(Duration.ZERO)
                .setRetryDelayMultiplier(1)
                .setMaxRetryDelay(Duration.ZERO)
                .setInitialRpcTimeout(timeout)
                .setRpcTimeoutMultiplier(1)
                .setMaxRpcTimeout(timeout));
    FakeSettings settingsB = builderB.build();

    assertIsReflectionEqual(builderA, builderB);
    assertIsReflectionEqual(settingsA, settingsB);
  }

  @Test
  public void simpleCallSettingsBuildFailsUnsetProperties() throws IOException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Missing required properties");
    SimpleCallSettings.Builder<Integer, Integer> builder =
        SimpleCallSettings.newBuilder(FakeSettings.fakeMethodMethodDescriptor);
    builder.build();
  }

  @Test
  public void callSettingsBuildFromTimeoutNoRetries() throws IOException {
    Duration timeout = Duration.millis(60000);

    SimpleCallSettings.Builder<Integer, Integer> builderA =
        SimpleCallSettings.newBuilder(FakeSettings.fakeMethodMethodDescriptor);
    builderA.setSimpleTimeoutNoRetries(timeout);
    SimpleCallSettings<Integer, Integer> settingsA = builderA.build();

    SimpleCallSettings.Builder<Integer, Integer> builderB =
        SimpleCallSettings.newBuilder(FakeSettings.fakeMethodMethodDescriptor);
    builderB
        .setRetryableCodes()
        .setRetrySettingsBuilder(
            RetrySettings.newBuilder()
                .setTotalTimeout(timeout)
                .setInitialRetryDelay(Duration.ZERO)
                .setRetryDelayMultiplier(1)
                .setMaxRetryDelay(Duration.ZERO)
                .setInitialRpcTimeout(timeout)
                .setRpcTimeoutMultiplier(1)
                .setMaxRpcTimeout(timeout));
    SimpleCallSettings<Integer, Integer> settingsB = builderB.build();

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
          "fakeMethodBundling",
          "channelProvider",
          "executorProvider"
        });
    assertIsReflectionEqual(settingsA.fakeMethodSimple, settingsB.fakeMethodSimple);
    assertIsReflectionEqual(settingsA.fakePagedMethod, settingsB.fakePagedMethod);
    assertIsReflectionEqual(settingsA.fakeMethodBundling, settingsB.fakeMethodBundling);
    assertIsReflectionEqual(settingsA.getChannelProvider(), settingsB.getChannelProvider());
    assertIsReflectionEqual(settingsA.getExecutorProvider(), settingsB.getExecutorProvider());
  }

  private static void assertIsReflectionEqual(
      FakeSettings.Builder builderA, FakeSettings.Builder builderB) {
    assertIsReflectionEqual(
        builderA,
        builderB,
        new String[] {
          "fakeMethodSimple",
          "fakePagedMethod",
          "fakeMethodBundling",
          "channelProvider",
          "executorProvider"
        });
    assertIsReflectionEqual(builderA.fakeMethodSimple, builderB.fakeMethodSimple);
    assertIsReflectionEqual(builderA.fakePagedMethod, builderB.fakePagedMethod);
    assertIsReflectionEqual(builderA.fakeMethodBundling, builderB.fakeMethodBundling);
    assertIsReflectionEqual(builderA.getChannelProvider(), builderB.getChannelProvider());
    assertIsReflectionEqual(builderA.getExecutorProvider(), builderB.getExecutorProvider());
  }

  private static void assertIsReflectionEqual(
      UnaryCallSettings.Builder builderA, UnaryCallSettings.Builder builderB) {
    assertIsReflectionEqual(builderA, builderB, new String[] {"retrySettingsBuilder"});
    assertIsReflectionEqual(builderA.getRetrySettingsBuilder(), builderB.getRetrySettingsBuilder());
  }

  private static <RequestT, ResponseT> void assertIsReflectionEqual(
      BundlingCallSettings.Builder<RequestT, ResponseT> builderA,
      BundlingCallSettings.Builder<RequestT, ResponseT> builderB) {
    assertIsReflectionEqual(
        builderA, builderB, new String[] {"retrySettingsBuilder", "bundlingSettingsBuilder"});
    assertIsReflectionEqual(builderA.getRetrySettingsBuilder(), builderB.getRetrySettingsBuilder());
    assertIsReflectionEqual(
        builderA.getBundlingSettingsBuilder(), builderB.getBundlingSettingsBuilder());
  }
}
