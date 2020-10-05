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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.core.ApiClock;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeClientSettings;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class ClientContextTest {

  private static class InterceptingExecutor extends ScheduledThreadPoolExecutor {
    boolean shutdownCalled = false;

    public InterceptingExecutor(int corePoolSize) {
      super(corePoolSize);
    }

    public void shutdown() {
      shutdownCalled = true;
    }
  }

  private static class FakeExecutorProvider implements ExecutorProvider {
    ScheduledExecutorService executor;
    boolean shouldAutoClose;

    FakeExecutorProvider(ScheduledExecutorService executor, boolean shouldAutoClose) {
      this.executor = executor;
      this.shouldAutoClose = shouldAutoClose;
    }

    @Override
    public boolean shouldAutoClose() {
      return shouldAutoClose;
    }

    @Override
    public ScheduledExecutorService getExecutor() {
      return executor;
    }
  }

  private static class FakeTransportProvider implements TransportChannelProvider {
    final Executor executor;
    final FakeTransportChannel transport;
    final boolean shouldAutoClose;
    final Map<String, String> headers;
    final Credentials credentials;

    FakeTransportProvider(
        FakeTransportChannel transport,
        Executor executor,
        boolean shouldAutoClose,
        Map<String, String> headers,
        Credentials credentials) {
      this.transport = transport;
      this.executor = executor;
      this.shouldAutoClose = shouldAutoClose;
      this.headers = headers;
      this.transport.setHeaders(headers);
      this.credentials = credentials;
    }

    @Override
    public boolean shouldAutoClose() {
      return shouldAutoClose;
    }

    @Override
    public boolean needsExecutor() {
      return executor == null;
    }

    @Override
    public TransportChannelProvider withExecutor(ScheduledExecutorService executor) {
      return withExecutor((Executor) executor);
    }

    @Override
    public TransportChannelProvider withExecutor(Executor executor) {
      return new FakeTransportProvider(
          this.transport, executor, this.shouldAutoClose, this.headers, this.credentials);
    }

    @Override
    public boolean needsHeaders() {
      return headers == null;
    }

    @Override
    public TransportChannelProvider withHeaders(Map<String, String> headers) {
      return new FakeTransportProvider(
          this.transport, this.executor, this.shouldAutoClose, headers, this.credentials);
    }

    @Override
    public boolean needsEndpoint() {
      return false;
    }

    @Override
    public TransportChannelProvider withEndpoint(String endpoint) {
      return this;
    }

    @Override
    public boolean acceptsPoolSize() {
      return false;
    }

    @Override
    public TransportChannelProvider withPoolSize(int size) {
      throw new UnsupportedOperationException(
          "FakeTransportProvider doesn't allow pool size customization");
    }

    @Override
    public TransportChannel getTransportChannel() throws IOException {
      if (needsExecutor()) {
        throw new IllegalStateException("Needs Executor");
      }
      if (needsCredentials()) {
        throw new IllegalStateException("Needs Credentials");
      }
      return transport;
    }

    @Override
    public String getTransportName() {
      return "FakeTransport";
    }

    @Override
    public boolean needsCredentials() {
      return credentials == null;
    }

    @Override
    public TransportChannelProvider withCredentials(Credentials credentials) {
      return new FakeTransportProvider(
          this.transport, this.executor, this.shouldAutoClose, this.headers, credentials);
    }
  }

  @Test
  public void testNoAutoCloseContextNeedsNoExecutor() throws Exception {
    runTest(false, false, false, false);
  }

  @Test
  public void testWithAutoCloseContextNeedsNoExecutor() throws Exception {
    runTest(true, false, false, false);
  }

  @Test
  public void testWithAutoCloseContextNeedsExecutor() throws Exception {
    runTest(true, true, false, false);
  }

  @Test
  public void testNeedsHeaders() throws Exception {
    runTest(false, false, true, false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNeedsHeadersCollision() throws Exception {
    runTest(false, false, true, true);
  }

  private void runTest(
      boolean shouldAutoClose,
      boolean contextNeedsExecutor,
      boolean needHeaders,
      boolean headersCollision)
      throws Exception {
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    ExecutorProvider executorProvider = new FakeExecutorProvider(executor, shouldAutoClose);
    Map<String, String> headers = ImmutableMap.of("k1", "v1", "k2", "v2");
    FakeTransportChannel transportChannel = FakeTransportChannel.create(new FakeChannel());
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(
            transportChannel,
            contextNeedsExecutor ? null : executor,
            shouldAutoClose,
            needHeaders ? null : headers,
            null);
    Credentials credentials = Mockito.mock(Credentials.class);
    ApiClock clock = Mockito.mock(ApiClock.class);
    Watchdog watchdog =
        Watchdog.create(
            Mockito.mock(ApiClock.class),
            Duration.ZERO,
            Mockito.mock(ScheduledExecutorService.class));
    Duration watchdogCheckInterval = Duration.ofSeconds(11);

    builder.setExecutorProvider(executorProvider);
    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
    builder.setWatchdogProvider(FixedWatchdogProvider.create(watchdog));
    builder.setWatchdogCheckInterval(watchdogCheckInterval);
    builder.setClock(clock);

    HeaderProvider headerProvider = Mockito.mock(HeaderProvider.class);
    Mockito.when(headerProvider.getHeaders()).thenReturn(ImmutableMap.of("k1", "v1"));
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);
    if (headersCollision) {
      Mockito.when(internalHeaderProvider.getHeaders()).thenReturn(ImmutableMap.of("k1", "v1"));
    } else {
      Mockito.when(internalHeaderProvider.getHeaders()).thenReturn(ImmutableMap.of("k2", "v2"));
    }

    builder.setHeaderProvider(headerProvider);
    builder.setInternalHeaderProvider(internalHeaderProvider);

    FakeClientSettings settings = builder.build();
    ClientContext clientContext = ClientContext.create(settings);

    Truth.assertThat(clientContext.getExecutor()).isSameInstanceAs(executor);
    Truth.assertThat(clientContext.getTransportChannel()).isSameInstanceAs(transportChannel);

    FakeTransportChannel actualChannel = (FakeTransportChannel) clientContext.getTransportChannel();
    assert actualChannel != null;
    Truth.assertThat(actualChannel.getHeaders()).isEqualTo(headers);
    Truth.assertThat(clientContext.getCredentials()).isSameInstanceAs(credentials);
    Truth.assertThat(clientContext.getClock()).isSameInstanceAs(clock);
    Truth.assertThat(clientContext.getStreamWatchdog()).isSameInstanceAs(watchdog);
    Truth.assertThat(clientContext.getStreamWatchdogCheckInterval())
        .isEqualTo(watchdogCheckInterval);

    Truth.assertThat(clientContext.getHeaders()).isEqualTo(ImmutableMap.of("k1", "v1"));
    Truth.assertThat(clientContext.getInternalHeaders()).isEqualTo(ImmutableMap.of("k2", "v2"));

    Truth.assertThat(executor.shutdownCalled).isFalse();
    Truth.assertThat(transportChannel.isShutdown()).isFalse();

    List<BackgroundResource> resources = clientContext.getBackgroundResources();

    if (!resources.isEmpty()) {
      // This is slightly too implementation-specific, but we need to ensure that executor is shut
      // down after the transportChannel: https://github.com/googleapis/gax-java/issues/785
      Truth.assertThat(resources.size()).isEqualTo(2);
      Truth.assertThat(transportChannel.isShutdown()).isNotEqualTo(shouldAutoClose);
      Truth.assertThat(executor.shutdownCalled).isNotEqualTo(shouldAutoClose);
      resources.get(0).shutdown();
      Truth.assertThat(transportChannel.isShutdown()).isEqualTo(shouldAutoClose);
      Truth.assertThat(executor.shutdownCalled).isNotEqualTo(shouldAutoClose);
      resources.get(1).shutdown();
      Truth.assertThat(transportChannel.isShutdown()).isEqualTo(shouldAutoClose);
      Truth.assertThat(executor.shutdownCalled).isEqualTo(shouldAutoClose);
    }
  }

  @Test
  public void testWatchdogProvider() throws IOException {
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    FakeTransportChannel transportChannel = FakeTransportChannel.create(new FakeChannel());
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(transportChannel, executor, true, null, null);
    ApiClock clock = Mockito.mock(ApiClock.class);

    builder.setClock(clock);
    builder.setCredentialsProvider(
        FixedCredentialsProvider.create(Mockito.mock(Credentials.class)));
    builder.setExecutorProvider(new FakeExecutorProvider(executor, true));
    builder.setTransportChannelProvider(transportProvider);

    Duration watchdogCheckInterval = Duration.ofSeconds(11);
    builder.setWatchdogProvider(
        InstantiatingWatchdogProvider.create()
            .withClock(clock)
            .withCheckInterval(watchdogCheckInterval)
            .withExecutor(executor));
    builder.setWatchdogCheckInterval(watchdogCheckInterval);

    HeaderProvider headerProvider = Mockito.mock(HeaderProvider.class);
    Mockito.when(headerProvider.getHeaders()).thenReturn(ImmutableMap.of("k1", "v1"));
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);

    Mockito.when(internalHeaderProvider.getHeaders()).thenReturn(ImmutableMap.of("k2", "v2"));
    builder.setHeaderProvider(headerProvider);
    builder.setInternalHeaderProvider(internalHeaderProvider);

    ClientContext context = ClientContext.create(builder.build());
    List<BackgroundResource> resources = context.getBackgroundResources();
    assertThat(resources.get(2)).isInstanceOf(Watchdog.class);
  }

  @Test
  public void testMergeHeaders_getQuotaProjectIdFromHeadersProvider() throws IOException {
    final String QUOTA_PROJECT_ID_KEY = "x-goog-user-project";
    final String QUOTA_PROJECT_ID_FROM_SETTINGS = "quota_project_id_from_settings";
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    FakeTransportChannel transportChannel = FakeTransportChannel.create(new FakeChannel());
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(transportChannel, executor, true, null, null);

    HeaderProvider headerProvider = Mockito.mock(HeaderProvider.class);
    Mockito.when(headerProvider.getHeaders()).thenReturn(ImmutableMap.of("header_k1", "v1"));
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);
    Mockito.when(internalHeaderProvider.getHeaders())
        .thenReturn(ImmutableMap.of("internal_header_k1", "v1"));

    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(
        FixedCredentialsProvider.create(Mockito.mock(Credentials.class)));
    builder.setHeaderProvider(headerProvider);
    builder.setInternalHeaderProvider(internalHeaderProvider);
    builder.setQuotaProjectId(QUOTA_PROJECT_ID_FROM_SETTINGS);

    ClientContext context = ClientContext.create(builder.build());
    List<BackgroundResource> resources = context.getBackgroundResources();
    FakeTransportChannel fakeTransportChannel = (FakeTransportChannel) resources.get(0);
    assertThat(fakeTransportChannel.getHeaders().size())
        .isEqualTo(
            headerProvider.getHeaders().size() + internalHeaderProvider.getHeaders().size() + 1);
    assertThat(fakeTransportChannel.getHeaders().get(QUOTA_PROJECT_ID_KEY))
        .isEqualTo(QUOTA_PROJECT_ID_FROM_SETTINGS);
  }

  @Test
  public void testMergeHeaders_getQuotaProjectIdFromSettings() throws IOException {
    final String QUOTA_PROJECT_ID_KEY = "x-goog-user-project";
    final String QUOTA_PROJECT_ID_FROM_HEADERS = "quota_project_id_from_headers";
    final String QUOTA_PROJECT_ID_FROM_INTERNAL_HEADERS = "quota_project_id_from_internal_headers";
    final String QUOTA_PROJECT_ID_FROM_SETTINGS = "quota_project_id_from_settings";
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    FakeTransportChannel transportChannel = FakeTransportChannel.create(new FakeChannel());
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(transportChannel, executor, true, null, null);

    HeaderProvider headerProvider =
        new HeaderProvider() {
          @Override
          public Map<String, String> getHeaders() {
            return ImmutableMap.of(QUOTA_PROJECT_ID_KEY, QUOTA_PROJECT_ID_FROM_HEADERS, "k2", "v2");
          }
        };
    HeaderProvider internalHeaderProvider =
        new HeaderProvider() {
          @Override
          public Map<String, String> getHeaders() {
            return ImmutableMap.of(
                QUOTA_PROJECT_ID_KEY,
                QUOTA_PROJECT_ID_FROM_INTERNAL_HEADERS,
                "internal_header_k1",
                "v1");
          }
        };

    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(
        FixedCredentialsProvider.create(Mockito.mock(Credentials.class)));
    builder.setHeaderProvider(headerProvider);
    builder.setInternalHeaderProvider(internalHeaderProvider);
    builder.setQuotaProjectId(QUOTA_PROJECT_ID_FROM_SETTINGS);

    ClientContext context = ClientContext.create(builder.build());
    List<BackgroundResource> resources = context.getBackgroundResources();
    FakeTransportChannel fakeTransportChannel = (FakeTransportChannel) resources.get(0);
    assertThat(fakeTransportChannel.getHeaders().size())
        .isEqualTo(
            headerProvider.getHeaders().size() + internalHeaderProvider.getHeaders().size() - 1);
    assertThat(fakeTransportChannel.getHeaders().get(QUOTA_PROJECT_ID_KEY))
        .isEqualTo(QUOTA_PROJECT_ID_FROM_SETTINGS);
  }

  @Test
  public void testMergeHeaders_noQuotaProjectIdSet() throws IOException {
    final String QUOTA_PROJECT_ID_KEY = "x-goog-user-project";
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    FakeTransportChannel transportChannel = FakeTransportChannel.create(new FakeChannel());
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(transportChannel, executor, true, null, null);

    HeaderProvider headerProvider = Mockito.mock(HeaderProvider.class);
    Mockito.when(headerProvider.getHeaders()).thenReturn(ImmutableMap.of("header_k1", "v1"));
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);
    Mockito.when(internalHeaderProvider.getHeaders())
        .thenReturn(ImmutableMap.of("internal_header_k1", "v1"));

    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(
        FixedCredentialsProvider.create(Mockito.mock(Credentials.class)));
    builder.setHeaderProvider(headerProvider);
    builder.setInternalHeaderProvider(internalHeaderProvider);

    ClientContext context = ClientContext.create(builder.build());
    List<BackgroundResource> resources = context.getBackgroundResources();
    FakeTransportChannel fakeTransportChannel = (FakeTransportChannel) resources.get(0);
    assertThat(fakeTransportChannel.getHeaders().size())
        .isEqualTo(headerProvider.getHeaders().size() + internalHeaderProvider.getHeaders().size());
    assertThat(fakeTransportChannel.getHeaders().containsKey(QUOTA_PROJECT_ID_KEY)).isFalse();
  }

  @Test
  public void testHidingQuotaProjectId_quotaSetFromSetting() throws IOException {
    final String QUOTA_PROJECT_ID_KEY = "x-goog-user-project";
    final String QUOTA_PROJECT_ID_FROM_CREDENTIALS_VALUE = "quota_project_id_from_credentials";
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    FakeTransportChannel transportChannel = FakeTransportChannel.create(new FakeChannel());
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(transportChannel, executor, true, null, null);
    Map<String, List<String>> metaDataWithQuota =
        ImmutableMap.of(
            "k1",
            Collections.singletonList("v1"),
            QUOTA_PROJECT_ID_KEY,
            Collections.singletonList(QUOTA_PROJECT_ID_FROM_CREDENTIALS_VALUE));
    final Credentials credentialsWithQuotaProjectId = Mockito.mock(GoogleCredentials.class);
    Mockito.when(credentialsWithQuotaProjectId.getRequestMetadata(null))
        .thenReturn(metaDataWithQuota);
    HeaderProvider headerProviderWithQuota = Mockito.mock(HeaderProvider.class);
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);

    builder.setExecutorProvider(new FakeExecutorProvider(executor, true));
    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(
        new CredentialsProvider() {
          @Override
          public Credentials getCredentials() throws IOException {
            return credentialsWithQuotaProjectId;
          }
        });
    builder.setHeaderProvider(headerProviderWithQuota);
    builder.setInternalHeaderProvider(internalHeaderProvider);
    builder.setQuotaProjectId(QUOTA_PROJECT_ID_FROM_CREDENTIALS_VALUE);

    ClientContext clientContext = ClientContext.create(builder.build());
    assertThat(clientContext.getCredentials().getRequestMetadata().size())
        .isEqualTo(metaDataWithQuota.size() - 1);
    assertThat(
            clientContext.getCredentials().getRequestMetadata().containsKey(QUOTA_PROJECT_ID_KEY))
        .isFalse();
  }

  @Test
  public void testHidingQuotaProjectId_noQuotaSetFromSetting() throws IOException {
    final String QUOTA_PROJECT_ID_KEY = "x-goog-user-project";
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    FakeTransportChannel transportChannel = FakeTransportChannel.create(new FakeChannel());
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(transportChannel, executor, true, null, null);
    Map<String, List<String>> metaData = ImmutableMap.of("k1", Collections.singletonList("v1"));
    final Credentials credentialsWithoutQuotaProjectId = Mockito.mock(GoogleCredentials.class);
    Mockito.when(credentialsWithoutQuotaProjectId.getRequestMetadata(null)).thenReturn(metaData);
    HeaderProvider headerProviderWithQuota = Mockito.mock(HeaderProvider.class);
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);

    builder.setExecutorProvider(new FakeExecutorProvider(executor, true));
    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(
        new CredentialsProvider() {
          @Override
          public Credentials getCredentials() throws IOException {
            return credentialsWithoutQuotaProjectId;
          }
        });
    builder.setHeaderProvider(headerProviderWithQuota);
    builder.setInternalHeaderProvider(internalHeaderProvider);

    ClientContext clientContext = ClientContext.create(builder.build());
    assertThat(clientContext.getCredentials().getRequestMetadata(null)).isEqualTo(metaData);
  }

  @Test
  public void testUserAgentInternalOnly() throws Exception {
    TransportChannelProvider transportChannelProvider =
        new FakeTransportProvider(
            FakeTransportChannel.create(new FakeChannel()), null, true, null, null);

    ClientSettings.Builder builder =
        new FakeClientSettings.Builder()
            .setExecutorProvider(
                FixedExecutorProvider.create(Mockito.mock(ScheduledExecutorService.class)))
            .setTransportChannelProvider(transportChannelProvider)
            .setCredentialsProvider(
                FixedCredentialsProvider.create(Mockito.mock(GoogleCredentials.class)));

    builder.setInternalHeaderProvider(FixedHeaderProvider.create("user-agent", "internal-agent"));

    ClientContext clientContext = ClientContext.create(builder.build());
    FakeTransportChannel transportChannel =
        (FakeTransportChannel) clientContext.getTransportChannel();

    assertThat(transportChannel.getHeaders()).containsEntry("user-agent", "internal-agent");
  }

  @Test
  public void testUserAgentExternalOnly() throws Exception {
    TransportChannelProvider transportChannelProvider =
        new FakeTransportProvider(
            FakeTransportChannel.create(new FakeChannel()), null, true, null, null);

    ClientSettings.Builder builder =
        new FakeClientSettings.Builder()
            .setExecutorProvider(
                FixedExecutorProvider.create(Mockito.mock(ScheduledExecutorService.class)))
            .setTransportChannelProvider(transportChannelProvider)
            .setCredentialsProvider(
                FixedCredentialsProvider.create(Mockito.mock(GoogleCredentials.class)));

    builder.setHeaderProvider(FixedHeaderProvider.create("user-agent", "user-supplied-agent"));

    ClientContext clientContext = ClientContext.create(builder.build());
    FakeTransportChannel transportChannel =
        (FakeTransportChannel) clientContext.getTransportChannel();

    assertThat(transportChannel.getHeaders()).containsEntry("user-agent", "user-supplied-agent");
  }

  @Test
  public void testUserAgentConcat() throws Exception {
    TransportChannelProvider transportChannelProvider =
        new FakeTransportProvider(
            FakeTransportChannel.create(new FakeChannel()), null, true, null, null);

    ClientSettings.Builder builder =
        new FakeClientSettings.Builder()
            .setExecutorProvider(
                FixedExecutorProvider.create(Mockito.mock(ScheduledExecutorService.class)))
            .setTransportChannelProvider(transportChannelProvider)
            .setCredentialsProvider(
                FixedCredentialsProvider.create(Mockito.mock(GoogleCredentials.class)));

    builder.setHeaderProvider(FixedHeaderProvider.create("user-agent", "user-supplied-agent"));
    builder.setInternalHeaderProvider(FixedHeaderProvider.create("user-agent", "internal-agent"));

    ClientContext clientContext = ClientContext.create(builder.build());
    FakeTransportChannel transportChannel =
        (FakeTransportChannel) clientContext.getTransportChannel();

    assertThat(transportChannel.getHeaders())
        .containsEntry("user-agent", "user-supplied-agent internal-agent");
  }
}
