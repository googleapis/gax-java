/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * A utility class to control a local service which is used by testing.
 */
public class MockServiceHelper {
  private static final int FLOW_CONTROL_WINDOW = 65 * 1024;

  private final SocketAddress address;
  private final Server server;
  private final MockGrpcService mockService;

  /**
   * Constructs a new MockServiceHelper. The method start() must
   * be called before it is used.
   */
  public MockServiceHelper(String addressString, MockGrpcService mockService) {
    this.address = new LocalAddress(addressString);
    this.mockService = mockService;
    NettyServerBuilder builder =
        NettyServerBuilder.forAddress(address)
            .flowControlWindow(FLOW_CONTROL_WINDOW)
            .channelType(LocalServerChannel.class);
    builder.addService(mockService.getServiceDefinition());
    this.server = builder.build();
  }

  /**
   * Starts the local server.
   */
  public void start() {
    try {
      server.start();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Resets the state of the mock service.
   */
  public void reset() {
    mockService.reset();
  }

  /**
   * Stops the local server.
   */
  public void stop() {
    server.shutdownNow();
  }

  /**
   * Returns the mock grpc service.
   */
  public MockGrpcService getService() {
    return mockService;
  }

  /**
   * Creates a channel for making requests to the mock service.
   */
  public ManagedChannel createChannel() {
    return NettyChannelBuilder.forAddress(address)
        .negotiationType(NegotiationType.PLAINTEXT)
        .channelType(LocalChannel.class)
        .build();
  }

  @VisibleForTesting
  MockServiceHelper(Server server, String address, MockGrpcService mockService) {
    this.server = server;
    this.address = new LocalAddress(address);
    this.mockService = mockService;
  }
}
