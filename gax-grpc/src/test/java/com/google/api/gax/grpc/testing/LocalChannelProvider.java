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
package com.google.api.gax.grpc.testing;

import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/** LocalChannelProvider creates channels for in-memory gRPC services. */
public class LocalChannelProvider implements TransportChannelProvider {
  private final SocketAddress address;

  private LocalChannelProvider(String addressString) {
    this.address = new LocalAddress(addressString);
  }

  @Override
  public boolean shouldAutoClose() {
    return true;
  }

  @Override
  public boolean needsExecutor() {
    return false;
  }

  @Override
  public TransportChannelProvider withExecutor(ScheduledExecutorService executor) {
    throw new UnsupportedOperationException(
        "FixedTransportChannelProvider doesn't need an executor");
  }

  @Override
  public boolean needsHeaders() {
    return false;
  }

  @Override
  public TransportChannelProvider withHeaders(Map<String, String> headers) {
    throw new UnsupportedOperationException("FixedTransportChannelProvider doesn't need headers");
  }

  @Override
  public TransportChannel getTransportChannel() throws IOException {
    ManagedChannel channel =
        NettyChannelBuilder.forAddress(address)
            .negotiationType(NegotiationType.PLAINTEXT)
            .channelType(LocalChannel.class)
            .build();

    return GrpcTransportChannel.newBuilder().setManagedChannel(channel).build();
  }

  @Override
  public String getTransportName() {
    return GrpcTransportChannel.getGrpcTransportName();
  }

  /** Creates a LocalChannelProvider. */
  public static LocalChannelProvider create(String addressString) {
    return new LocalChannelProvider(addressString);
  }
}
