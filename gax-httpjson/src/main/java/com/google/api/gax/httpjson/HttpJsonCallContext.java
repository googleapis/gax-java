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
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.TransportChannel;
import com.google.auth.Credentials;
import com.google.common.base.Preconditions;
import java.util.Objects;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * HttpJsonCallContext encapsulates context data used to make an http-json call.
 *
 * <p>HttpJsonCallContext is immutable in the sense that none of its methods modifies the
 * HttpJsonCallContext itself or the underlying data. Methods of the form {@code withX} return
 * copies of the object, but with one field changed. The immutability and thread safety of the
 * arguments solely depends on the arguments themselves.
 */
@BetaApi
public final class HttpJsonCallContext implements ApiCallContext {
  private final HttpJsonChannel channel;
  private final Instant deadline;
  private final Credentials credentials;

  /** Returns an empty instance. */
  public static HttpJsonCallContext of() {
    return new HttpJsonCallContext(null, null, null);
  }

  private HttpJsonCallContext(HttpJsonChannel channel, Instant deadline, Credentials credentials) {
    this.channel = channel;
    this.deadline = deadline;
    this.credentials = credentials;
  }

  /**
   * Returns inputContext cast to {@link HttpJsonCallContext}, or an empty {@link
   * HttpJsonCallContext} if inputContext is null.
   *
   * @param inputContext the {@link ApiCallContext} to cast if it is not null
   */
  @Override
  public HttpJsonCallContext nullToSelf(ApiCallContext inputContext) {
    HttpJsonCallContext httpJsonCallContext;
    if (inputContext == null) {
      httpJsonCallContext = this;
    } else {
      if (!(inputContext instanceof HttpJsonCallContext)) {
        throw new IllegalArgumentException(
            "context must be an instance of HttpJsonCallContext, but found "
                + inputContext.getClass().getName());
      }
      httpJsonCallContext = (HttpJsonCallContext) inputContext;
    }
    return httpJsonCallContext;
  }

  @Override
  public HttpJsonCallContext merge(ApiCallContext inputCallContext) {
    if (inputCallContext == null) {
      return this;
    }
    if (!(inputCallContext instanceof HttpJsonCallContext)) {
      throw new IllegalArgumentException(
          "context must be an instance of GrpcCallContext, but found "
              + inputCallContext.getClass().getName());
    }
    HttpJsonCallContext httpJsonCallContext = (HttpJsonCallContext) inputCallContext;

    HttpJsonChannel newChannel = httpJsonCallContext.channel;
    if (newChannel == null) {
      newChannel = this.channel;
    }

    Instant newDeadline = httpJsonCallContext.deadline;
    if (newDeadline == null) {
      newDeadline = this.deadline;
    }

    Credentials newCredentials = httpJsonCallContext.credentials;
    if (newCredentials == null) {
      newCredentials = this.credentials;
    }

    return new HttpJsonCallContext(newChannel, newDeadline, newCredentials);
  }

  @Override
  public HttpJsonCallContext withCredentials(Credentials newCredentials) {
    return new HttpJsonCallContext(this.channel, this.deadline, newCredentials);
  }

  @Override
  public HttpJsonCallContext withTransportChannel(TransportChannel inputChannel) {
    Preconditions.checkNotNull(inputChannel);
    if (!(inputChannel instanceof HttpJsonTransportChannel)) {
      throw new IllegalArgumentException(
          "Expected HttpJsonTransportChannel, got " + inputChannel.getClass().getName());
    }
    HttpJsonTransportChannel transportChannel = (HttpJsonTransportChannel) inputChannel;
    return withChannel(transportChannel.getChannel());
  }

  @Override
  public HttpJsonCallContext withTimeout(Duration rpcTimeout) {
    Instant newDeadline = Instant.now().plus(rpcTimeout);
    HttpJsonCallContext nextContext = withDeadline(newDeadline);

    if (deadline == null) {
      return nextContext;
    }
    if (deadline.isBefore(newDeadline)) {
      return this;
    }
    return nextContext;
  }

  public HttpJsonChannel getChannel() {
    return channel;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public HttpJsonCallContext withChannel(HttpJsonChannel newChannel) {
    return new HttpJsonCallContext(newChannel, this.deadline, this.credentials);
  }

  public HttpJsonCallContext withDeadline(Instant newDeadline) {
    return new HttpJsonCallContext(this.channel, newDeadline, this.credentials);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash();
  }
}
