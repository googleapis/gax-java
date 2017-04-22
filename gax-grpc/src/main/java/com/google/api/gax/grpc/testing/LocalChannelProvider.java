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

import com.google.api.gax.grpc.ChannelProvider;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import java.net.SocketAddress;
import java.util.concurrent.Executor;

/**
 * LocalChannelProvider creates channels for in-memory gRPC services.
 */
public class LocalChannelProvider implements ChannelProvider {
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
  public ManagedChannel getChannel() {
    return NettyChannelBuilder.forAddress(address)
        .negotiationType(NegotiationType.PLAINTEXT)
        .channelType(LocalChannel.class)
        .build();
  }

  @Override
  public ManagedChannel getChannel(Executor executor) {
    throw new IllegalStateException("getChannel(Executor) called when needsExecutor() is false.");
  }

  /**
   * Creates a LocalChannelProvider.
   */
  public static LocalChannelProvider create(String addressString) {
    return new LocalChannelProvider(addressString);
  }
}
