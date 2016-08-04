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
