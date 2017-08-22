/*
 * Copyright 2017, Google Inc. All rights reserved.
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
package com.google.api.gax.httpjson;

import com.google.api.core.BetaApi;
import java.util.concurrent.Executor;

/**
 * FixedHttpJsonChannelProvider is an HttpJsonChannelProvider which always provides the same
 * channel.
 */
@BetaApi
public final class FixedHttpJsonChannelProvider implements HttpJsonChannelProvider {
  private final ManagedHttpJsonChannel channel;

  private FixedHttpJsonChannelProvider(ManagedHttpJsonChannel channel) {
    this.channel = channel;
  }

  @Override
  public boolean shouldAutoClose() {
    return false;
  }

  @Override
  public boolean needsExecutor() {
    return false;
  }

  @Override
  public ManagedHttpJsonChannel getChannel() {
    return channel;
  }

  @Override
  public ManagedHttpJsonChannel getChannel(Executor executor) {
    throw new IllegalStateException("getChannel(Executor) called when needsExecutor() is false.");
  }

  /** Creates a FixedHttpJsonChannelProvider. */
  public static FixedHttpJsonChannelProvider create(ManagedHttpJsonChannel channel) {
    return new FixedHttpJsonChannelProvider(channel);
  }
}
