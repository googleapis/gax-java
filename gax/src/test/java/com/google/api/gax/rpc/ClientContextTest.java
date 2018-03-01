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
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeClientSettings;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.Map;
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
    final ScheduledExecutorService executor;
    final FakeTransportChannel transport;
    final boolean shouldAutoClose;
    final Map<String, String> headers;

    FakeTransportProvider(
        FakeTransportChannel transport,
        ScheduledExecutorService executor,
        boolean shouldAutoClose,
        Map<String, String> headers) {
      this.transport = transport;
      this.executor = executor;
      this.shouldAutoClose = shouldAutoClose;
      this.headers = headers;
      this.transport.setHeaders(headers);
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
      return new FakeTransportProvider(
          this.transport, executor, this.shouldAutoClose, this.headers);
    }

    @Override
    public boolean needsHeaders() {
      return headers == null;
    }

    @Override
    public TransportChannelProvider withHeaders(Map<String, String> headers) {
      return new FakeTransportProvider(
          this.transport, this.executor, this.shouldAutoClose, headers);
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
    public TransportChannel getTransportChannel() throws IOException {
      if (needsExecutor()) {
        throw new IllegalStateException("Needs Executor");
      }
      return transport;
    }

    @Override
    public String getTransportName() {
      return "FakeTransport";
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
            needHeaders ? null : headers);
    Credentials credentials = Mockito.mock(Credentials.class);
    ApiClock clock = Mockito.mock(ApiClock.class);
    Watchdog watchdog = Mockito.mock(Watchdog.class);
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

    Truth.assertThat(clientContext.getExecutor()).isSameAs(executor);
    Truth.assertThat(clientContext.getTransportChannel()).isSameAs(transportChannel);

    FakeTransportChannel actualChannel = (FakeTransportChannel) clientContext.getTransportChannel();
    assert actualChannel != null;
    Truth.assertThat(actualChannel.getHeaders()).isEqualTo(headers);
    Truth.assertThat(clientContext.getCredentials()).isSameAs(credentials);
    Truth.assertThat(clientContext.getClock()).isSameAs(clock);
    Truth.assertThat(clientContext.getStreamWatchdog()).isSameAs(watchdog);
    Truth.assertThat(clientContext.getStreamWatchdogCheckInterval())
        .isEqualTo(watchdogCheckInterval);

    Truth.assertThat(clientContext.getHeaders()).isEqualTo(ImmutableMap.of("k1", "v1"));
    Truth.assertThat(clientContext.getInternalHeaders()).isEqualTo(ImmutableMap.of("k2", "v2"));

    Truth.assertThat(executor.shutdownCalled).isFalse();
    Truth.assertThat(transportChannel.isShutdown()).isFalse();

    for (BackgroundResource backgroundResource : clientContext.getBackgroundResources()) {
      backgroundResource.shutdown();
    }

    Truth.assertThat(executor.shutdownCalled).isEqualTo(shouldAutoClose);
    Truth.assertThat(transportChannel.isShutdown()).isEqualTo(shouldAutoClose);
  }
}
