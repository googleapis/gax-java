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
package com.google.api.gax.rpc;

import com.google.api.core.BetaApi;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Provides an interface to either build a TransportChannel or provide a fixed TransportChannel that
 * will be used to make calls to a service.
 *
 * <p>Implementations of {@link TransportChannelProvider} may choose to create a new {@link
 * TransportChannel} for each call to {@link #getTransportChannel}, or may return a fixed {@link
 * TransportChannel} instance.
 *
 * <p>Callers should use the following pattern to get a channel:
 *
 * <pre><code>
 * TransportChannelProvider transportChannelProvider = ...;
 * if (transportChannelProvider.needsExecutor()) {
 *   transportChannelProvider = transportChannelProvider.withExecutor(executor);
 * }
 * if (transportChannelProvider.needsHeaders()) {
 *   transportChannelProvider = transportChannelProvider.withHeaders(headers);
 * }
 * TransportChannel transportChannel = transportChannelProvider.getTransportChannel();
 * </code></pre>
 */
@BetaApi
public interface TransportChannelProvider {
  /** Indicates whether the TransportChannel should be closed by the containing client class. */
  boolean shouldAutoClose();

  /** True if the TransportProvider needs an executor. */
  boolean needsExecutor();

  /**
   * Sets the executor to use when constructing a new transport.
   *
   * <p>This method should only be called if {@link #needsExecutor()} returns true.
   */
  TransportChannelProvider withExecutor(ScheduledExecutorService executor);

  /** True if the TransportProvider has no headers provided. */
  boolean needsHeaders();

  /**
   * Sets the headers to use when constructing a new transport.
   *
   * <p>This method should only be called if {@link #needsHeaders()} returns true.
   */
  TransportChannelProvider withHeaders(Map<String, String> headers);

  /**
   * Provides a Transport, which could either be a new instance for every call, or the same
   * instance, depending on the implementation.
   *
   * <p>If {@link #needsExecutor()} is true, then {@link #withExecutor(ScheduledExecutorService)}
   * needs to be called first to provide an executor.
   *
   * <p>If {@link #needsHeaders()} is true, then {@link #withHeaders(Map)} needs to be called first
   * to provide headers.
   */
  TransportChannel getTransportChannel() throws IOException;

  /**
   * The name of the transport.
   *
   * <p>This string can be used for identifying transports for switching logic.
   */
  String getTransportName();
}
