/*
 * Copyright 2019 Google LLC
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
package com.google.api.gax.httpjson;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.rpc.mtls.AbstractMtlsTransportChannelTest;
import com.google.api.gax.rpc.mtls.MtlsProvider;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class InstantiatingHttpJsonChannelProviderTest extends AbstractMtlsTransportChannelTest {

  @Test
  public void basicTest() throws IOException {
    String endpoint = "localhost:8080";
    ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
    executor.shutdown();

    TransportChannelProvider provider = InstantiatingHttpJsonChannelProvider.newBuilder().build();

    assertThat(provider.needsEndpoint()).isTrue();
    provider = provider.withEndpoint(endpoint);
    assertThat(provider.needsEndpoint()).isFalse();

    assertThat(provider.needsHeaders()).isTrue();
    provider = provider.withHeaders(Collections.<String, String>emptyMap());
    assertThat(provider.needsHeaders()).isFalse();

    // Make sure getTransportChannel works without setting executor
    assertThat(provider.getTransportChannel()).isInstanceOf(HttpJsonTransportChannel.class);

    assertThat(provider.needsExecutor()).isTrue();
    provider = provider.withExecutor((Executor) executor);
    assertThat(provider.needsExecutor()).isFalse();

    // Added for code coverage. Remove when withExecutor(ScheduledExecutorService) is removed.
    assertThat(provider.needsExecutor()).isFalse();
    provider = provider.withExecutor(executor);
    assertThat(provider.needsExecutor()).isFalse();

    assertThat(provider.acceptsPoolSize()).isFalse();
    Exception thrownException = null;
    try {
      provider.withPoolSize(1);
    } catch (Exception e) {
      thrownException = e;
    }
    assertThat(thrownException).isInstanceOf(UnsupportedOperationException.class);

    assertThat(provider.needsCredentials()).isFalse();
    thrownException = null;
    try {
      provider.withCredentials(null);
    } catch (Exception e) {
      thrownException = e;
    }
    assertThat(thrownException).isInstanceOf(UnsupportedOperationException.class);

    assertEquals(HttpJsonTransportChannel.getHttpJsonTransportName(), provider.getTransportName());
    assertThat(provider.getTransportChannel()).isInstanceOf(HttpJsonTransportChannel.class);

    // Make sure we can create channels OK.
    provider.getTransportChannel().shutdownNow();
  }

  @Override
  protected Object getMtlsObjectFromTransportChannel(MtlsProvider provider)
      throws IOException, GeneralSecurityException {
    InstantiatingHttpJsonChannelProvider channelProvider =
        InstantiatingHttpJsonChannelProvider.newBuilder()
            .setEndpoint("localhost:8080")
            .setMtlsProvider(provider)
            .setHeaderProvider(Mockito.mock(HeaderProvider.class))
            .setExecutor(Mockito.mock(Executor.class))
            .build();
    return channelProvider.createHttpTransport();
  }
}
