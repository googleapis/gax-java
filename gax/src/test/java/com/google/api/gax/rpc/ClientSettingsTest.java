/*
 * Copyright 2017 Google LLC
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
 *     * Neither the name of Google LLC nor the names of its
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
package com.google.api.gax.rpc;

import com.google.api.core.ApiClock;
import com.google.api.core.ApiFunction;
import com.google.api.core.NanoClock;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeClientSettings;
import com.google.auth.Credentials;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class ClientSettingsTest {

  @Test
  public void testEmptyBuilder() throws Exception {
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();
    Truth.assertThat(builder.getExecutorProvider())
        .isInstanceOf(InstantiatingExecutorProvider.class);
    Truth.assertThat(builder.getTransportChannelProvider()).isNull();
    Truth.assertThat(builder.getCredentialsProvider()).isInstanceOf(NoCredentialsProvider.class);
    Truth.assertThat(builder.getClock()).isInstanceOf(NanoClock.class);
    Truth.assertThat(builder.getHeaderProvider()).isInstanceOf(NoHeaderProvider.class);
    Truth.assertThat(builder.getInternalHeaderProvider()).isInstanceOf(NoHeaderProvider.class);
    Truth.assertThat(builder.getWatchdogProvider())
        .isInstanceOf(InstantiatingWatchdogProvider.class);
    Truth.assertThat(builder.getWatchdogCheckInterval()).isGreaterThan(Duration.ZERO);

    FakeClientSettings settings = builder.build();
    Truth.assertThat(settings.getExecutorProvider())
        .isSameInstanceAs(builder.getExecutorProvider());
    Truth.assertThat(settings.getTransportChannelProvider())
        .isSameInstanceAs(builder.getTransportChannelProvider());
    Truth.assertThat(settings.getCredentialsProvider())
        .isSameInstanceAs(builder.getCredentialsProvider());
    Truth.assertThat(settings.getClock()).isSameInstanceAs(builder.getClock());
    Truth.assertThat(settings.getHeaderProvider()).isSameInstanceAs(builder.getHeaderProvider());
    Truth.assertThat(settings.getInternalHeaderProvider())
        .isSameInstanceAs(builder.getInternalHeaderProvider());
    Truth.assertThat(settings.getWatchdogProvider())
        .isInstanceOf(InstantiatingWatchdogProvider.class);
    Truth.assertThat(settings.getWatchdogCheckInterval()).isGreaterThan(Duration.ZERO);

    String settingsString = settings.toString();
    Truth.assertThat(settingsString).contains("executorProvider");
    Truth.assertThat(settingsString).contains("transportChannelProvider");
    Truth.assertThat(settingsString).contains("credentialsProvider");
    Truth.assertThat(settingsString).contains("clock");
    Truth.assertThat(settingsString).contains("headerProvider");
    Truth.assertThat(settingsString).contains("watchdogProvider");
    Truth.assertThat(settingsString).contains("watchdogCheckInterval");
  }

  @Test
  public void testBuilder() throws Exception {
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
    TransportChannelProvider transportProvider = Mockito.mock(TransportChannelProvider.class);
    CredentialsProvider credentialsProvider = Mockito.mock(CredentialsProvider.class);
    ApiClock clock = Mockito.mock(ApiClock.class);
    HeaderProvider headerProvider = Mockito.mock(HeaderProvider.class);
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);
    WatchdogProvider watchdogProvider = Mockito.mock(WatchdogProvider.class);
    Duration watchdogCheckInterval = Duration.ofSeconds(13);

    builder.setExecutorProvider(executorProvider);
    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(credentialsProvider);
    builder.setHeaderProvider(headerProvider);
    builder.setInternalHeaderProvider(internalHeaderProvider);
    builder.setClock(clock);
    builder.setWatchdogProvider(watchdogProvider);
    builder.setWatchdogCheckInterval(watchdogCheckInterval);

    Truth.assertThat(builder.getExecutorProvider()).isSameInstanceAs(executorProvider);
    Truth.assertThat(builder.getTransportChannelProvider()).isSameInstanceAs(transportProvider);
    Truth.assertThat(builder.getCredentialsProvider()).isSameInstanceAs(credentialsProvider);
    Truth.assertThat(builder.getClock()).isSameInstanceAs(clock);
    Truth.assertThat(builder.getHeaderProvider()).isSameInstanceAs(headerProvider);
    Truth.assertThat(builder.getInternalHeaderProvider()).isSameInstanceAs(internalHeaderProvider);
    Truth.assertThat(builder.getWatchdogProvider()).isSameInstanceAs(watchdogProvider);
    Truth.assertThat(builder.getWatchdogCheckInterval()).isSameInstanceAs(watchdogCheckInterval);

    String builderString = builder.toString();
    Truth.assertThat(builderString).contains("executorProvider");
    Truth.assertThat(builderString).contains("transportChannelProvider");
    Truth.assertThat(builderString).contains("credentialsProvider");
    Truth.assertThat(builderString).contains("clock");
    Truth.assertThat(builderString).contains("headerProvider");
    Truth.assertThat(builderString).contains("internalHeaderProvider");
    Truth.assertThat(builderString).contains("watchdogProvider");
    Truth.assertThat(builderString).contains("watchdogCheckInterval");
  }

  @Test
  public void testBuilderFromClientContext() throws Exception {
    ApiClock clock = Mockito.mock(ApiClock.class);
    ApiCallContext callContext = FakeCallContext.createDefault();
    Map<String, String> headers = Collections.singletonMap("spiffykey", "spiffyvalue");
    Watchdog watchdog =
        Watchdog.create(
            Mockito.mock(ApiClock.class),
            Duration.ZERO,
            Mockito.mock(ScheduledExecutorService.class));
    Duration watchdogCheckInterval = Duration.ofSeconds(12);

    ClientContext clientContext =
        ClientContext.newBuilder()
            .setExecutor(Mockito.mock(ScheduledExecutorService.class))
            .setTransportChannel(Mockito.mock(TransportChannel.class))
            .setCredentials(Mockito.mock(Credentials.class))
            .setClock(clock)
            .setDefaultCallContext(callContext)
            .setHeaders(headers)
            .setStreamWatchdog(watchdog)
            .setStreamWatchdogCheckInterval(watchdogCheckInterval)
            .build();

    FakeClientSettings.Builder builder = new FakeClientSettings.Builder(clientContext);

    Truth.assertThat(builder.getExecutorProvider()).isInstanceOf(FixedExecutorProvider.class);
    Truth.assertThat(builder.getTransportChannelProvider())
        .isInstanceOf(FixedTransportChannelProvider.class);
    Truth.assertThat(builder.getCredentialsProvider()).isInstanceOf(FixedCredentialsProvider.class);
    Truth.assertThat(builder.getClock()).isSameInstanceAs(clock);
    Truth.assertThat(builder.getHeaderProvider().getHeaders())
        .containsEntry("spiffykey", "spiffyvalue");
    Truth.assertThat(builder.getWatchdogProvider()).isInstanceOf(FixedWatchdogProvider.class);
    Truth.assertThat(builder.getWatchdogProvider().getWatchdog()).isSameInstanceAs(watchdog);
    Truth.assertThat(builder.getWatchdogCheckInterval()).isEqualTo(watchdogCheckInterval);
  }

  @Test
  public void testBuilderFromSettings() throws Exception {
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
    TransportChannelProvider transportProvider = Mockito.mock(TransportChannelProvider.class);
    CredentialsProvider credentialsProvider = Mockito.mock(CredentialsProvider.class);
    ApiClock clock = Mockito.mock(ApiClock.class);
    HeaderProvider headerProvider = Mockito.mock(HeaderProvider.class);
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);
    WatchdogProvider watchdogProvider = Mockito.mock(WatchdogProvider.class);
    Duration watchdogCheckInterval = Duration.ofSeconds(14);

    builder.setExecutorProvider(executorProvider);
    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(credentialsProvider);
    builder.setClock(clock);
    builder.setHeaderProvider(headerProvider);
    builder.setInternalHeaderProvider(internalHeaderProvider);
    builder.setWatchdogProvider(watchdogProvider);
    builder.setWatchdogCheckInterval(watchdogCheckInterval);

    FakeClientSettings settings = builder.build();
    FakeClientSettings.Builder newBuilder = new FakeClientSettings.Builder(settings);

    Truth.assertThat(newBuilder.getExecutorProvider()).isSameInstanceAs(executorProvider);
    Truth.assertThat(newBuilder.getTransportChannelProvider()).isSameInstanceAs(transportProvider);
    Truth.assertThat(newBuilder.getCredentialsProvider()).isSameInstanceAs(credentialsProvider);
    Truth.assertThat(newBuilder.getClock()).isSameInstanceAs(clock);
    Truth.assertThat(newBuilder.getHeaderProvider()).isSameInstanceAs(headerProvider);
    Truth.assertThat(newBuilder.getInternalHeaderProvider())
        .isSameInstanceAs(internalHeaderProvider);
    Truth.assertThat(newBuilder.getWatchdogProvider()).isSameInstanceAs(watchdogProvider);
    Truth.assertThat(newBuilder.getWatchdogCheckInterval()).isEqualTo(watchdogCheckInterval);
  }

  @Test
  public void testApplyToAllUnaryMethods() throws Exception {
    List<UnaryCallSettings.Builder<?, ?>> builders = new ArrayList<>();
    builders.add(UnaryCallSettings.newUnaryCallSettingsBuilder());
    builders.add(UnaryCallSettings.newUnaryCallSettingsBuilder());
    // using an array to have a mutable integer
    final int[] count = {0};
    ClientSettings.Builder.applyToAllUnaryMethods(
        builders,
        new ApiFunction<UnaryCallSettings.Builder<?, ?>, Void>() {
          @Override
          public Void apply(UnaryCallSettings.Builder<?, ?> input) {
            if (count[0] == 0) {
              input.setRetryableCodes(StatusCode.Code.UNAVAILABLE);
            } else {
              input.setRetryableCodes(StatusCode.Code.DEADLINE_EXCEEDED);
            }
            count[0] += 1;
            return null;
          }
        });

    Truth.assertThat(builders.get(0).getRetryableCodes())
        .containsExactly(StatusCode.Code.UNAVAILABLE);
    Truth.assertThat(builders.get(1).getRetryableCodes())
        .containsExactly(StatusCode.Code.DEADLINE_EXCEEDED);
  }
}
