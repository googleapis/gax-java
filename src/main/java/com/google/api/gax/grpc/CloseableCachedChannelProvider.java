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
package com.google.api.gax.grpc;

import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * CloseableCachedChannelProvider calls an inner ChannelProvider the first time getChannel()
 * or getChannel(Executor) is called and caches the channel, and then returns that cached
 * channel on subsequent calls.
 *
 * Note: close() must be called on this class to clean up the channel.
 */
public class CloseableCachedChannelProvider implements ChannelProvider {
  private final ChannelProvider innerProvider;
  private ManagedChannel cachedChannel;

  private CloseableCachedChannelProvider(ChannelProvider innerProvider) {
    this.innerProvider = innerProvider;
  }

  @Override
  public boolean shouldAutoClose() {
    return false;
  }

  @Override
  public boolean needsExecutor() {
    if (cachedChannel == null) {
      return innerProvider.needsExecutor();
    } else {
      return false;
    }
  }

  @Override
  public ManagedChannel getChannel() throws IOException {
    if (cachedChannel == null) {
      cachedChannel = innerProvider.getChannel();
    }
    return cachedChannel;
  }

  @Override
  public ManagedChannel getChannel(Executor executor) throws IOException {
    if (cachedChannel == null) {
      cachedChannel = innerProvider.getChannel(executor);
      return cachedChannel;
    } else {
      throw new IllegalStateException("getChannel(Executor) called when needsExecutor() is false");
    }
  }

  public void close() {
    cachedChannel.shutdown();
  }

  public static CloseableCachedChannelProvider create(ChannelProvider innerProvider) {
    return new CloseableCachedChannelProvider(innerProvider);
  }
}
