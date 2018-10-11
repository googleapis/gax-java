/*
 * Copyright 2017 Google LLC
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
package com.google.api.gax.grpc;

import com.google.api.client.util.Preconditions;
import com.google.api.gax.rpc.ApiCallContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;

/**
 * {@code GrpcClientCalls} creates a new {@code ClientCall} from the given call context.
 *
 * <p>Package-private for internal use.
 */
class GrpcClientCalls {
  private GrpcClientCalls() {};

  public static <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
      MethodDescriptor<RequestT, ResponseT> descriptor, ApiCallContext context) {
    if (!(context instanceof GrpcCallContext)) {
      throw new IllegalArgumentException(
          "context must be an instance of GrpcCallContext, but found "
              + context.getClass().getName());
    }

    GrpcCallContext grpcContext = (GrpcCallContext) context;
    Preconditions.checkNotNull(grpcContext.getChannel());

    CallOptions callOptions = grpcContext.getCallOptions();
    Preconditions.checkNotNull(callOptions);

    // Try to convert the timeout into a deadline and use it if it occurs before the actual deadline
    if (grpcContext.getTimeout() != null) {
      Deadline newDeadline =
          Deadline.after(grpcContext.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
      Deadline oldDeadline = callOptions.getDeadline();

      if (oldDeadline == null || newDeadline.isBefore(oldDeadline)) {
        callOptions = callOptions.withDeadline(newDeadline);
      }
    }

    Channel channel = grpcContext.getChannel();
    if (grpcContext.getChannelAffinity() != null && channel instanceof ChannelPool) {
      channel = ((ChannelPool) channel).getChannel(grpcContext.getChannelAffinity());
    }

    if (!grpcContext.getExtraHeaders().isEmpty()) {
      ClientInterceptor interceptor =
          MetadataUtils.newAttachHeadersInterceptor(grpcContext.getMetadata());
      channel = ClientInterceptors.intercept(channel, interceptor);
    }

    return channel.newCall(descriptor, callOptions);
  }
}
