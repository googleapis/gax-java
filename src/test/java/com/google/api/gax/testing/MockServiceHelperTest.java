/*
 * Copyright 2016, Google Inc. All rights reserved.
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
package com.google.api.gax.testing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.truth.Truth;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link MockServiceHelper}.
 */
@RunWith(JUnit4.class)
public class MockServiceHelperTest {
  @Mock private MockGrpcService grpcService;
  @Mock private MockGrpcService grpcService2;

  @Mock private Server server;

  /**
   * Sets up mocks.
   */
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(grpcService.getServiceDefinition())
        .thenReturn(ServerServiceDefinition.builder("fake-service").build());
    when(grpcService2.getServiceDefinition())
        .thenReturn(ServerServiceDefinition.builder("fake-service2").build());
  }

  @Test
  public void testGetService() {
    MockServiceHelper serviceHelper = new MockServiceHelper(server, "fake-address", grpcService);
    Truth.assertThat(serviceHelper.getService()).isSameAs(grpcService);
    Truth.assertThat(serviceHelper.getServices().size()).isEqualTo(1);
  }

  @Test(expected = IllegalStateException.class)
  public void testGetServiceInvalid() {
    MockServiceHelper serviceHelper =
        new MockServiceHelper(server, "fake-address2", Arrays.asList(grpcService, grpcService2));
    serviceHelper.getService();
  }

  @Test
  public void testStart() throws IOException {
    MockServiceHelper serviceHelper = new MockServiceHelper(server, "fake-address", grpcService);
    serviceHelper.start();
    verify(server, times(1)).start();
  }

  @Test
  public void testReset() {
    MockServiceHelper serviceHelper = new MockServiceHelper("fake-address", grpcService);
    serviceHelper.reset();
    verify(grpcService, times(1)).getServiceDefinition();
    verify(grpcService, times(1)).reset();
  }

  @Test
  public void testCreateChannelProvider() {
    MockServiceHelper serviceHelper = new MockServiceHelper("fake-address", grpcService);
    ManagedChannel channel = serviceHelper.createChannelProvider().getChannel();
    assertNotNull(channel);
    assertFalse(channel.isTerminated());
    channel.shutdownNow();
  }
}
