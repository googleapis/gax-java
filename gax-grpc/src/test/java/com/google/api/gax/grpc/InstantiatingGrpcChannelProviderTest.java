/*
 * Copyright 2016 Google LLC
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
package com.google.api.gax.grpc;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.api.core.ApiFunction;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider.Builder;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class InstantiatingGrpcChannelProviderTest {

  @Test
  public void testEndpoint() {
    String endpoint = "localhost:8080";
    InstantiatingGrpcChannelProvider.Builder builder =
        InstantiatingGrpcChannelProvider.newBuilder();
    builder.setEndpoint(endpoint);
    assertEquals(builder.getEndpoint(), endpoint);

    InstantiatingGrpcChannelProvider provider = builder.build();
    assertEquals(provider.getEndpoint(), endpoint);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEndpointNoPort() {
    InstantiatingGrpcChannelProvider.newBuilder().setEndpoint("localhost");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEndpointBadPort() {
    InstantiatingGrpcChannelProvider.newBuilder().setEndpoint("localhost:abcd");
  }

  @Test
  public void testKeepAlive() {
    Duration keepaliveTime = Duration.ofSeconds(1);
    Duration keepaliveTimeout = Duration.ofSeconds(2);
    boolean keepaliveWithoutCalls = true;

    InstantiatingGrpcChannelProvider provider =
        InstantiatingGrpcChannelProvider.newBuilder()
            .setKeepAliveTime(keepaliveTime)
            .setKeepAliveTimeout(keepaliveTimeout)
            .setKeepAliveWithoutCalls(keepaliveWithoutCalls)
            .build();

    assertEquals(provider.getKeepAliveTime(), keepaliveTime);
    assertEquals(provider.getKeepAliveTimeout(), keepaliveTimeout);
    assertEquals(provider.getKeepAliveWithoutCalls(), keepaliveWithoutCalls);
  }

  @Test
  public void testMaxInboundMetadataSize() {
    InstantiatingGrpcChannelProvider provider =
        InstantiatingGrpcChannelProvider.newBuilder().setMaxInboundMetadataSize(4096).build();
    assertThat(provider.getMaxInboundMetadataSize()).isEqualTo(4096);
  }

  @Test
  public void testCpuPoolSize() {
    // happy path
    Builder builder = InstantiatingGrpcChannelProvider.newBuilder().setProcessorCount(2);
    builder.setChannelsPerCpu(2.5);
    assertEquals(5, builder.getPoolSize());

    // User specified max
    builder = builder.setProcessorCount(50);
    builder.setChannelsPerCpu(100, 10);
    assertEquals(10, builder.getPoolSize());

    // Sane default maximum
    builder.setChannelsPerCpu(200);
    assertEquals(100, builder.getPoolSize());
  }

  @Test
  public void testWithPoolSize() throws IOException {
    ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
    executor.shutdown();

    TransportChannelProvider provider =
        InstantiatingGrpcChannelProvider.newBuilder()
            .build()
            .withExecutor(executor)
            .withHeaders(Collections.<String, String>emptyMap())
            .withEndpoint("localhost:8080");
    assertThat(provider.acceptsPoolSize()).isTrue();

    // Make sure we can create channels OK.
    provider.getTransportChannel().shutdownNow();

    provider = provider.withPoolSize(2);
    assertThat(provider.acceptsPoolSize()).isFalse();
    provider.getTransportChannel().shutdownNow();

    try {
      provider.withPoolSize(3);
      fail("acceptsPoolSize() returned false; we shouldn't be able to set it again");
    } catch (IllegalStateException e) {

    }
  }

  @Test
  public void testWithInterceptors() throws Exception {
    testWithInterceptors(1);
  }

  @Test
  public void testWithInterceptorsAndMultipleChannels() throws Exception {
    testWithInterceptors(5);
  }

  private void testWithInterceptors(int numChannels) throws Exception {
    final GrpcInterceptorProvider interceptorProvider = Mockito.mock(GrpcInterceptorProvider.class);

    InstantiatingGrpcChannelProvider channelProvider =
        InstantiatingGrpcChannelProvider.newBuilder()
            .setEndpoint("localhost:8080")
            .setPoolSize(numChannels)
            .setHeaderProvider(Mockito.mock(HeaderProvider.class))
            .setExecutorProvider(Mockito.mock(ExecutorProvider.class))
            .setInterceptorProvider(interceptorProvider)
            .build();

    Mockito.verify(interceptorProvider, Mockito.never()).getInterceptors();
    channelProvider.getTransportChannel().shutdownNow();
    Mockito.verify(interceptorProvider, Mockito.times(numChannels)).getInterceptors();
  }

  @Test
  public void testChannelConfigurator() throws IOException {
    final int numChannels = 5;

    // Create a mock configurator that will insert mock channels
    @SuppressWarnings("unchecked")
    ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator =
        Mockito.mock(ApiFunction.class);

    ArgumentCaptor<ManagedChannelBuilder> channelBuilderCaptor =
        ArgumentCaptor.forClass(ManagedChannelBuilder.class);

    ManagedChannelBuilder swappedBuilder = Mockito.mock(ManagedChannelBuilder.class);
    ManagedChannel fakeChannel = Mockito.mock(ManagedChannel.class);
    Mockito.when(swappedBuilder.build()).thenReturn(fakeChannel);

    Mockito.when(channelConfigurator.apply(channelBuilderCaptor.capture()))
        .thenReturn(swappedBuilder);

    // Invoke the provider
    InstantiatingGrpcChannelProvider.newBuilder()
        .setEndpoint("localhost:8080")
        .setHeaderProvider(Mockito.mock(HeaderProvider.class))
        .setExecutorProvider(Mockito.mock(ExecutorProvider.class))
        .setChannelConfigurator(channelConfigurator)
        .setPoolSize(numChannels)
        .build()
        .getTransportChannel();

    // Make sure that the provider passed in a configured channel
    assertThat(channelBuilderCaptor.getValue()).isNotNull();
    // And that it was replaced with the mock
    Mockito.verify(swappedBuilder, Mockito.times(numChannels)).build();
  }
}
