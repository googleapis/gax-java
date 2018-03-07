/*
 * Copyright 2018 Google LLC
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
 *     * Neither the name of Google LLC nor the names of its
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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import org.threeten.bp.Duration;

/**
 * A callable that uses a {@link Watchdog} to monitor streams.
 *
 * <p>It extracts the {@code StreamWaitTimeout} and the {@code StreamIdleTimeout} from the {@link
 * ApiCallContext} and applies then to the stream using the {@link Watchdog}.
 *
 * <p>Package-private for internal use.
 *
 * @see Watchdog for more details
 */
class WatchdogServerStreamingCallable<RequestT, ResponseT>
    extends ServerStreamingCallable<RequestT, ResponseT> {
  private final ServerStreamingCallable<RequestT, ResponseT> inner;
  private final Watchdog watchdog;

  WatchdogServerStreamingCallable(
      ServerStreamingCallable<RequestT, ResponseT> inner, Watchdog watchdog) {
    Preconditions.checkNotNull(inner);
    Preconditions.checkNotNull(watchdog);

    this.inner = inner;
    this.watchdog = watchdog;
  }

  @Override
  public void call(
      RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context) {

    // If the caller never configured the timeouts, disable them
    Duration waitTimeout = MoreObjects.firstNonNull(context.getStreamWaitTimeout(), Duration.ZERO);
    Duration idleTimeout = MoreObjects.firstNonNull(context.getStreamIdleTimeout(), Duration.ZERO);

    responseObserver = watchdog.watch(responseObserver, waitTimeout, idleTimeout);
    inner.call(request, responseObserver, context);
  }
}
