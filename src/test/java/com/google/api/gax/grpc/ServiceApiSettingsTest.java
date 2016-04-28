package com.google.api.gax.grpc;

import com.google.api.gax.core.ConnectionSettings;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import io.grpc.ManagedChannel;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tests for {@link ServiceApiSettings}.
 */
@RunWith(JUnit4.class)
public class ServiceApiSettingsTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static class FakeSettings extends ServiceApiSettings {

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

    public static Builder createBuilder(ConnectionSettings connectionSettings) {
      return new Builder(connectionSettings);
    }

    private FakeSettings(Builder settingsBuilder) throws IOException {
      super(
          settingsBuilder.getChannelProvider(),
          settingsBuilder.getExecutorProvider(),
          settingsBuilder.getGeneratorName(),
          settingsBuilder.getGeneratorVersion(),
          settingsBuilder.getClientLibName(),
          settingsBuilder.getClientLibVersion());
    }

    private static class Builder extends ServiceApiSettings.Builder {

      private Builder(ConnectionSettings connectionSettings) {
        super(connectionSettings);
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
    }
  }

  @Test
  public void fixedChannelAutoClose() throws IOException {
    thrown.expect(IllegalStateException.class);
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    FakeSettings settings =
        FakeSettings.createBuilder(FakeSettings.DEFAULT_CONNECTION_SETTINGS)
            .provideChannelWith(channel, true)
            .build();
    ChannelProvider channelProvider = settings.getChannelProvider();
    ScheduledExecutorService executor = settings.getExecutorProvider().getOrBuildExecutor();
    ManagedChannel channelA = channelProvider.getOrBuildChannel(executor);
    ManagedChannel channelB = channelProvider.getOrBuildChannel(executor);
  }

  @Test
  public void fixedChannelNoAutoClose() throws IOException {
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    FakeSettings settings =
        FakeSettings.createBuilder(FakeSettings.DEFAULT_CONNECTION_SETTINGS)
            .provideChannelWith(channel, false)
            .build();
    ChannelProvider channelProvider = settings.getChannelProvider();
    ScheduledExecutorService executor = settings.getExecutorProvider().getOrBuildExecutor();
    ManagedChannel channelA = channelProvider.getOrBuildChannel(executor);
    ManagedChannel channelB = channelProvider.getOrBuildChannel(executor);
    Truth.assertThat(channelA).isEqualTo(channelB);
  }

  @Test
  public void defaultExecutor() throws IOException {
    FakeSettings settings =
        FakeSettings.createBuilder(FakeSettings.DEFAULT_CONNECTION_SETTINGS).build();
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
        FakeSettings.createBuilder(FakeSettings.DEFAULT_CONNECTION_SETTINGS)
            .provideExecutorWith(executor, true)
            .build();
    ExecutorProvider executorProvider = settings.getExecutorProvider();
    ScheduledExecutorService executorA = executorProvider.getOrBuildExecutor();
    ScheduledExecutorService executorB = executorProvider.getOrBuildExecutor();
  }

  @Test
  public void fixedExecutorNoAutoClose() throws IOException {
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
    FakeSettings settings =
        FakeSettings.createBuilder(FakeSettings.DEFAULT_CONNECTION_SETTINGS)
            .provideExecutorWith(executor, false)
            .build();
    ExecutorProvider executorProvider = settings.getExecutorProvider();
    ScheduledExecutorService executorA = executorProvider.getOrBuildExecutor();
    ScheduledExecutorService executorB = executorProvider.getOrBuildExecutor();
    Truth.assertThat(executorA).isEqualTo(executorB);
  }
}

