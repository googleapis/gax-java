package com.google.api.gax.grpc;

import com.google.api.gax.core.ConnectionSettings;
import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;

import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.internal.matchers.apachecommons.ReflectionEquals;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tests for {@link ServiceApiSettings}.
 */
@RunWith(JUnit4.class)
public class SettingsTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static class FakeSettings extends ServiceApiSettings {

    private static final MethodDescriptor<Integer, Integer> fakeMethodMethodDescriptor =
        Mockito.mock(MethodDescriptor.class);

    private static final PageStreamingDescriptor<Integer, Integer, Integer>
        fakePageStreamingDescriptor = Mockito.mock(PageStreamingDescriptor.class);

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
    private final PageStreamingCallSettings<Integer, Integer, Integer> fakeMethodPageStreaming;
    private final BundlingCallSettings<Integer, Integer> fakeMethodBundling;

    public SimpleCallSettings<Integer, Integer> fakeMethodSimple() {
      return fakeMethodSimple;
    }

    public PageStreamingCallSettings<Integer, Integer, Integer> fakeMethodPageStreaming() {
      return fakeMethodPageStreaming;
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
      this.fakeMethodPageStreaming = settingsBuilder.fakeMethodPageStreaming().build();
      this.fakeMethodBundling = settingsBuilder.fakeMethodBundling().build();
    }

    private static class Builder extends ServiceApiSettings.Builder {

      private SimpleCallSettings.Builder<Integer, Integer> fakeMethodSimple;
      private PageStreamingCallSettings.Builder<Integer, Integer, Integer> fakeMethodPageStreaming;
      private BundlingCallSettings.Builder<Integer, Integer> fakeMethodBundling;

      private Builder() {
        super(DEFAULT_CONNECTION_SETTINGS);

        fakeMethodSimple = SimpleCallSettings.newBuilder(fakeMethodMethodDescriptor);
        fakeMethodPageStreaming =
            PageStreamingCallSettings.newBuilder(
                fakeMethodMethodDescriptor, fakePageStreamingDescriptor);
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
            .fakeMethodPageStreaming()
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
        fakeMethodPageStreaming = settings.fakeMethodPageStreaming().toBuilder();
        fakeMethodBundling = settings.fakeMethodBundling().toBuilder();
      }

      @Override
      public FakeSettings build() throws IOException {
        return new FakeSettings(this);
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

      public SimpleCallSettings.Builder<Integer, Integer> fakeMethodSimple() {
        return fakeMethodSimple;
      }

      public PageStreamingCallSettings.Builder<Integer, Integer, Integer>
          fakeMethodPageStreaming() {
        return fakeMethodPageStreaming;
      }

      public BundlingCallSettings.Builder<Integer, Integer> fakeMethodBundling() {
        return fakeMethodBundling;
      }

      public void setFakeMethodSimple(SimpleCallSettings.Builder<Integer, Integer> fakeMethod) {
        this.fakeMethodSimple = fakeMethod;
      }

      public void setFakeMethodPageStreaming(
          PageStreamingCallSettings.Builder<Integer, Integer, Integer> fakeMethodPageStreaming) {
        this.fakeMethodPageStreaming = fakeMethodPageStreaming;
      }

      public void setFakeMethodBundling(
          BundlingCallSettings.Builder<Integer, Integer> fakeMethodBundling) {
        this.fakeMethodBundling = fakeMethodBundling;
      }
    }
  }

  //ServiceApiSettings
  // ====

  @Test
  public void fixedChannelAutoClose() throws IOException {
    thrown.expect(IllegalStateException.class);
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    FakeSettings settings = FakeSettings.defaultBuilder().provideChannelWith(channel, true).build();
    ChannelProvider channelProvider = settings.getChannelProvider();
    ScheduledExecutorService executor = settings.getExecutorProvider().getOrBuildExecutor();
    ManagedChannel channelA = channelProvider.getOrBuildChannel(executor);
    ManagedChannel channelB = channelProvider.getOrBuildChannel(executor);
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
    ScheduledExecutorService executorA = executorProvider.getOrBuildExecutor();
    ScheduledExecutorService executorB = executorProvider.getOrBuildExecutor();
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

  //ApiCallSettings
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

  //Reflection Helper Methods
  // ====

  /*
   * The pattern for the assertIsReflectionEqual methods below is to exclude fields that would
   * cause the reflectionEquals check to fail, then explicitly recurse on those fields with another
   * call to assertIsReflectionEqual.
   */

  private static void assertIsReflectionEqual(Object objA, Object objB, String[] excludes) {
    Truth.assertThat(new ReflectionEquals(objA, excludes).matches(objB)).isTrue();
  }

  private static void assertIsReflectionEqual(Object objA, Object objB) {
    assertIsReflectionEqual(objA, objB, null);
  }

  private static void assertIsReflectionEqual(FakeSettings settingsA, FakeSettings settingsB) {
    assertIsReflectionEqual(
        settingsA,
        settingsB,
        new String[] {"fakeMethodSimple", "fakeMethodPageStreaming", "fakeMethodBundling"});
    assertIsReflectionEqual(settingsA.fakeMethodSimple, settingsB.fakeMethodSimple);
    assertIsReflectionEqual(settingsA.fakeMethodPageStreaming, settingsB.fakeMethodPageStreaming);
    assertIsReflectionEqual(settingsA.fakeMethodBundling, settingsB.fakeMethodBundling);
  }

  private static void assertIsReflectionEqual(
      FakeSettings.Builder builderA, FakeSettings.Builder builderB) {
    assertIsReflectionEqual(
        builderA,
        builderB,
        new String[] {"fakeMethodSimple", "fakeMethodPageStreaming", "fakeMethodBundling"});
    assertIsReflectionEqual(builderA.fakeMethodSimple, builderB.fakeMethodSimple);
    assertIsReflectionEqual(builderA.fakeMethodPageStreaming, builderB.fakeMethodPageStreaming);
    assertIsReflectionEqual(builderA.fakeMethodBundling, builderB.fakeMethodBundling);
  }

  private static void assertIsReflectionEqual(
      ApiCallSettings.Builder builderA, ApiCallSettings.Builder builderB) {
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
