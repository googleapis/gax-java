/*
 * Copyright 2015, Google Inc.
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

import io.grpc.CallOptions;
import io.grpc.Channel;

/**
 * CallContext encapsulates arguments used to make an RPC call.
 *
 * CallContext is "immutable" in the sense that none of its methods modifies
 * the CallContext itself or the underlying data.
 * Methods of the form {@code withX}, such as {@link #withChannel},
 * return copies of the object, but with one field changed.
 * The immutability and thread safety of the arguments solely depends on the arguments themselves.
 */
public final class CallContext<RequestT> {
  private final Channel channel;
  private final CallOptions callOptions;
  private final RequestT request;

  private CallContext(Channel channel, CallOptions callOptions, RequestT request) {
    this.channel = channel;
    this.callOptions = callOptions;
    this.request = request;
  }

  public static <T> CallContext<T> of(Channel channel, CallOptions callOptions, T request) {
    return new CallContext<T>(channel, callOptions, request);
  }

  public static <T> CallContext<T> of(T request) {
    return of(null, CallOptions.DEFAULT, request);
  }

  public Channel getChannel() {
    return channel;
  }

  public CallOptions getCallOptions() {
    return callOptions;
  }

  public RequestT getRequest() {
    return request;
  }

  public CallContext<RequestT> withChannel(Channel channel) {
    return new CallContext<RequestT>(channel, this.callOptions, this.request);
  }

  public CallContext<RequestT> withCallOptions(CallOptions callOptions) {
    return new CallContext<RequestT>(this.channel, callOptions, this.request);
  }

  public CallContext<RequestT> withRequest(RequestT request) {
    return new CallContext<RequestT>(this.channel, this.callOptions, request);
  }
}
