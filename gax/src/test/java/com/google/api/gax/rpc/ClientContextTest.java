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
import static org.junit.Assert.fail;

import com.google.api.core.ApiClock;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeClientSettings;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Assert;
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
  public void testHidingQuotaProjectId() throws IOException {
    final String QUOTA_PROJECT_ID_KEY = "x-goog-user-project";
    final String QUOTA_PROJECT_ID_FROM_HEADER_VALUE = "quota_project_id_from_headers";
    final String QUOTA_PROJECT_ID_FROM_CREDENTIALS_VALUE = "quota_project_id_from_credentials";
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    FakeTransportChannel transportChannel = FakeTransportChannel.create(new FakeChannel());
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(transportChannel, executor, true, null, null);
    final Credentials credentialsWithQuota =
        loadCredentials(QUOTA_PROJECT_ID_FROM_CREDENTIALS_VALUE);
    HeaderProvider headerProviderWithQuota = Mockito.mock(HeaderProvider.class);
    Mockito.when(headerProviderWithQuota.getHeaders())
        .thenReturn(ImmutableMap.of(QUOTA_PROJECT_ID_KEY, QUOTA_PROJECT_ID_FROM_HEADER_VALUE));
    HeaderProvider internalHeaderProvider = Mockito.mock(HeaderProvider.class);

    builder.setExecutorProvider(new FakeExecutorProvider(executor, true));
    builder.setTransportChannelProvider(transportProvider);
    builder.setCredentialsProvider(
        new CredentialsProvider() {
          @Override
          public Credentials getCredentials() throws IOException {
            return credentialsWithQuota;
          }
        });

    builder.setHeaderProvider(headerProviderWithQuota);
    builder.setInternalHeaderProvider(internalHeaderProvider);

    ClientContext clientContext = ClientContext.create(builder.build());
    Assert.assertFalse(
        clientContext.getCredentials().getRequestMetadata().containsKey(QUOTA_PROJECT_ID_KEY));
  }

  static GoogleCredentials loadCredentials(String QUOTA_PROJECT_ID_FROM_CREDENTIALS_VALUE) {
    final String JSON_KEY_QUOTA_PROJECT_ID =
        "{\n"
            + "  \"private_key_id\": \"somekeyid\",\n"
            + "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggS"
            + "kAgEAAoIBAQC+K2hSuFpAdrJI\\nnCgcDz2M7t7bjdlsadsasad+fvRSW6TjNQZ3p5LLQY1kSZRqBqylRkzteMOyHg"
            + "aR\\n0Pmxh3ILCND5men43j3h4eDbrhQBuxfEMalkG92sL+PNQSETY2tnvXryOvmBRwa/\\nQP/9dJfIkIDJ9Fw9N4"
            + "Bhhhp6mCcRpdQjV38H7JsyJ7lih/oNjECgYAt\\nknddadwkwewcVxHFhcZJO+XWf6ofLUXpRwiTZakGMn8EE1uVa2"
            + "LgczOjwWHGi99MFjxSer5m9\\n1tCa3/KEGKiS/YL71JvjwX3mb+cewlkcmweBKZHM2JPTk0ZednFSpVZMtycjkbLa"
            + "\\ndYOS8V85AgMBewECggEBAKksaldajfDZDV6nGqbFjMiizAKJolr/M3OQw16K6o3/\\n0S31xIe3sSlgW0+UbYlF"
            + "4U8KifhManD1apVSC3csafaspP4RZUHFhtBywLO9pR5c\\nr6S5aLp+gPWFyIp1pfXbWGvc5VY/v9x7ya1VEa6rXvL"
            + "sKupSeWAW4tMj3eo/64ge\\nsdaceaLYw52KeBYiT6+vpsnYrEkAHO1fF/LavbLLOFJmFTMxmsNaG0tuiJHgjshB\\"
            + "n82DpMCbXG9YcCgI/DbzuIjsdj2JC1cascSP//3PmefWysucBQe7Jryb6NQtASmnv\\nCdDw/0jmZTEjpe4S1lxfHp"
            + "lAhHFtdgYTvyYtaLZiVVkCgYEA8eVpof2rceecw/I6\\n5ng1q3Hl2usdWV/4mZMvR0fOemacLLfocX6IYxT1zA1FF"
            + "JlbXSRsJMf/Qq39mOR2\\nSpW+hr4jCoHeRVYLgsbggtrevGmILAlNoqCMpGZ6vDmJpq6ECV9olliDvpPgWOP+\\nm"
            + "YPDreFBGxWvQrADNbRt2dmGsrsCgYEAyUHqB2wvJHFqdmeBsaacewzV8x9WgmeX\\ngUIi9REwXlGDW0Mz50dxpxcK"
            + "CAYn65+7TCnY5O/jmL0VRxU1J2mSWyWTo1C+17L0\\n3fUqjxL1pkefwecxwecvC+gFFYdJ4CQ/MHHXU81Lwl1iWdF"
            + "Cd2UoGddYaOF+KNeM\\nHC7cmqra+JsCgYEAlUNywzq8nUg7282E+uICfCB0LfwejuymR93CtsFgb7cRd6ak\\nECR"
            + "8FGfCpH8ruWJINllbQfcHVCX47ndLZwqv3oVFKh6pAS/vVI4dpOepP8++7y1u\\ncoOvtreXCX6XqfrWDtKIvv0vjl"
            + "HBhhhp6mCcRpdQjV38H7JsyJ7lih/oNjECgYAt\\nkndj5uNl5SiuVxHFhcZJO+XWf6ofLUregtevZakGMn8EE1uVa"
            + "2AY7eafmoU/nZPT\\n00YB0TBATdCbn/nBSuKDESkhSg9s2GEKQZG5hBmL5uCMfo09z3SfxZIhJdlerreP\\nJ7gSi"
            + "dI12N+EZxYd4xIJh/HFDgp7RRO87f+WJkofMQKBgGTnClK1VMaCRbJZPriw\\nEfeFCoOX75MxKwXs6xgrw4W//AYG"
            + "GUjDt83lD6AZP6tws7gJ2IwY/qP7+lyhjEqN\\nHtfPZRGFkGZsdaksdlaksd323423d+15/UvrlRSFPNj1tWQmNKk"
            + "XyRDW4IG1Oa2p\\nrALStNBx5Y9t0/LQnFI4w3aG\\n-----END PRIVATE KEY-----\\n\",\n"
            + "  \"project_id\": \"someprojectid\",\n"
            + "  \"client_email\": \"someclientid@developer.gserviceaccount.com\",\n"
            + "  \"client_id\": \"someclientid.apps.googleusercontent.com\",\n"
            + "  \"type\": \"service_account\",\n"
            + "  \"quota_project_id\": \""
            + QUOTA_PROJECT_ID_FROM_CREDENTIALS_VALUE
            + "\"\n"
            + "}";
    try {
      InputStream keyStream = new ByteArrayInputStream(JSON_KEY_QUOTA_PROJECT_ID.getBytes());
      return GoogleCredentials.fromStream(keyStream);
    } catch (IOException e) {
      fail("Couldn't create fake JSON credentials.");
    }
    return null;
  }
}
