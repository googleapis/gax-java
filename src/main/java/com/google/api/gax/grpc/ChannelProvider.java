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
 * Provides an interface to hold and build the channel that will be used. If the channel does not
 * already exist, it will be constructed when {@link #getChannel} is called.
 *
 * Implementations of {@link ChannelProvider} may choose to create a new {@link ManagedChannel} for
 * each call to {@link #getChannel}, or may return a fixed {@link ManagedChannel} instance.
 * In cases where the same {@link ManagedChannel} instance is returned, for example by a
 * {@link ChannelProvider} created using the {@link ServiceApiSettings}
 * provideChannelWith(ManagedChannel, boolean) method, and shouldAutoClose returns true, the
 * {@link #getChannel} method will throw an {@link IllegalStateException} if it is called
 * more than once. This is to prevent the same {@link ManagedChannel} being closed prematurely when
 * it is used by multiple client objects.
 */
public interface ChannelProvider {
  /**
   * Indicates whether the channel should be closed by the containing API class.
   */
  boolean shouldAutoClose();

  boolean needsExecutor();

  /**
   * Get the channel to be used to connect to the service, if this ChannelProvider
   * does not need an executor.
   */
  ManagedChannel getChannel() throws IOException;

  /**
   * Get the channel to be used to connect to the service, if this ChannelProvider
   * needs an executor.
   */
  ManagedChannel getChannel(Executor executor) throws IOException;

  Builder toBuilder();

  interface Builder {
    ChannelProvider build();
  }
}
