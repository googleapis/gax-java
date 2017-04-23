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
package com.google.api.gax.grpc;

import com.google.auto.value.AutoValue;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * ChannelAndExecutor holds a ManagedChannel and a ScheduledExecutorService that are being provided
 * as a pair.
 */
@AutoValue
public abstract class ChannelAndExecutor {
  public abstract ScheduledExecutorService getExecutor();

  public abstract ManagedChannel getChannel();

  /** Creates a ChannelAndExecutor simply containing the given channel and executor. */
  public static ChannelAndExecutor create(
      ScheduledExecutorService executor, ManagedChannel channel) {
    return new AutoValue_ChannelAndExecutor(executor, channel);
  }

  /**
   * Creates an executor using the given ExecutorProvider and a channel using the given
   * ChannelProvider, providing the executor to the channel if the channel needs an executor, and
   * then returns a ChannelAndExecutor containing both.
   */
  public static ChannelAndExecutor create(
      ExecutorProvider executorProvider, ChannelProvider channelProvider) throws IOException {
    ScheduledExecutorService executor = executorProvider.getExecutor();
    ManagedChannel channel = null;
    if (channelProvider.needsExecutor()) {
      channel = channelProvider.getChannel(executor);
    } else {
      channel = channelProvider.getChannel();
    }
    return ChannelAndExecutor.create(executor, channel);
  }
}
