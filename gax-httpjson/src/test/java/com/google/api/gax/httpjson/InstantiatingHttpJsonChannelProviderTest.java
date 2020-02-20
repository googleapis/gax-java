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
import static org.mockito.Mockito.mock;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.httpjson.testing.MockHttpService;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class InstantiatingHttpJsonChannelProviderTest {

  @Test
  public void basicTest() throws IOException {
    String endpoint = "localhost:8080";
    Executor executor = mock(Executor.class);
    ScheduledExecutorService deprecatedExecutor = mock(ScheduledExecutorService.class);

    TransportChannelProvider provider = InstantiatingHttpJsonChannelProvider.newBuilder().build();

    assertThat(provider.needsEndpoint()).isTrue();
    provider = provider.withEndpoint(endpoint);
    assertThat(provider.needsEndpoint()).isFalse();

    assertThat(provider.needsExecutor()).isTrue();
    provider = provider.withExecutor(executor);
    assertThat(provider.needsExecutor()).isFalse();

    TransportChannelProvider otherProvider =
        InstantiatingHttpJsonChannelProvider.newBuilder().build();
    assertThat(otherProvider.needsExecutor());
    otherProvider = otherProvider.withExecutor(deprecatedExecutor);
    assertThat(otherProvider.needsExecutor()).isFalse();

    assertThat(provider.needsHeaders()).isTrue();
    provider = provider.withHeaders(Collections.<String, String>emptyMap());
    assertThat(provider.needsHeaders()).isFalse();

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

  @Test
  public void testExecutorOverride() throws IOException, ExecutionException, InterruptedException {
    // Create a mock service that will always return errors. We just want to inspect the thread that those errors are returned on
    MockHttpService mockHttpService =
        new MockHttpService(Collections.<ApiMethodDescriptor>emptyList(), "/");
    mockHttpService.addException(new RuntimeException("Fake error"));

    final String expectedThreadName = "testExecutorOverrideExecutor";

    ExecutorService executor =
        Executors.newFixedThreadPool(1, newThreadFactory(expectedThreadName));

    try {
      InstantiatingHttpJsonChannelProvider channelProvider =
          InstantiatingHttpJsonChannelProvider.newBuilder()
              .setExecutor(executor)
              .setEndpoint("localhost:1234")
              .setHeaderProvider(FixedHeaderProvider.create())
              .setHttpTransport(mockHttpService)
              .build();

      assertThat(getThreadName(channelProvider)).isEqualTo(expectedThreadName);
    } finally {
      executor.shutdown();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  @Test
  @Deprecated
  public void testDeprecatedExecutorOverride()
      throws IOException, ExecutionException, InterruptedException {
    MockHttpService mockHttpService =
        new MockHttpService(Collections.<ApiMethodDescriptor>emptyList(), "/");
    mockHttpService.addException(new RuntimeException("Fake error"));

    final String expectedThreadName = "testExecutorOverrideExecutor";

    ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(1, newThreadFactory(expectedThreadName));

    try {
      InstantiatingHttpJsonChannelProvider channelProvider =
          InstantiatingHttpJsonChannelProvider.newBuilder()
              .setExecutorProvider(FixedExecutorProvider.create(executor))
              .setEndpoint("localhost:1234")
              .setHeaderProvider(FixedHeaderProvider.create())
              .setHttpTransport(mockHttpService)
              .build();

      assertThat(getThreadName(channelProvider)).isEqualTo(expectedThreadName);
    } finally {
      executor.shutdown();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  private static ThreadFactory newThreadFactory(final String threadName) {
    return new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, threadName);
        thread.setDaemon(true);
        return thread;
      }
    };
  }

  private static String getThreadName(InstantiatingHttpJsonChannelProvider provider)
      throws IOException, InterruptedException, ExecutionException {
    @SuppressWarnings("unchecked")
    ApiMethodDescriptor<Object, Object> apiMethodDescriptor =
        mock(
            ApiMethodDescriptor.class,
            new Answer() {
              @Override
              public Object answer(InvocationOnMock invocation) {
                throw new UnsupportedOperationException("fake error");
              }
            });

    HttpJsonTransportChannel transportChannel =
        (HttpJsonTransportChannel) provider.getTransportChannel();
    final SettableFuture<String> threadNameFuture = SettableFuture.create();
    try {
      HttpJsonChannel channel = transportChannel.getChannel();
      ApiFuture<Object> rpcFuture =
          channel.issueFutureUnaryCall(
              HttpJsonCallOptions.newBuilder().build(), new Object(), apiMethodDescriptor);
      rpcFuture.addListener(
          new Runnable() {
            @Override
            public void run() {
              threadNameFuture.set(Thread.currentThread().getName());
            }
          },
          MoreExecutors.directExecutor());
    } finally {
      transportChannel.shutdown();
      transportChannel.awaitTermination(10, TimeUnit.SECONDS);
    }

    return threadNameFuture.get();
  }
}
