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
package com.google.api.gax.grpc;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import io.grpc.Metadata;
import javax.annotation.Nullable;

public class GrpcResponseMetadata {

  private final Function<Metadata, Void> metadataHandler;
  private final Function<Metadata, Void> trailingMetadataHandler;

  private final Function<Metadata, Void> defaultMetadataHandler =
      new Function<Metadata, Void>() {
        @Override
        public Void apply(@Nullable Metadata input) {
          responseMetadata = input;
          return null;
        }
      };
  private final Function<Metadata, Void> defaultTrailingMetadataHandler =
      new Function<Metadata, Void>() {
        @Override
        public Void apply(@Nullable Metadata input) {
          trailingMetadata = input;
          return null;
        }
      };

  private volatile Metadata responseMetadata = null;
  private volatile Metadata trailingMetadata = null;

  public GrpcResponseMetadata() {
    this.metadataHandler = this.defaultMetadataHandler;
    this.trailingMetadataHandler = this.defaultTrailingMetadataHandler;
  }

  public GrpcResponseMetadata(
      Function<Metadata, Void> metadataHandler, Function<Metadata, Void> trailingMetadataHandler) {
    this.metadataHandler = metadataHandler;
    this.trailingMetadataHandler = trailingMetadataHandler;
  }

  public GrpcCallContext addHandlers(ApiCallContext apiCallContext) {
    if (Preconditions.checkNotNull(apiCallContext) instanceof GrpcCallContext) {
      return addHandlers((GrpcCallContext) apiCallContext);
    }
    throw new IllegalArgumentException(
        "context must be an instance of GrpcCallContext, but found "
            + apiCallContext.getClass().getName());
  }

  public GrpcCallContext createContextWithHandlers() {
    return addHandlers(GrpcCallContext.createDefault());
  }

  private GrpcCallContext addHandlers(GrpcCallContext grpcCallContext) {
    return Preconditions.checkNotNull(grpcCallContext)
        .withCallOptions(
            CallOptionsUtil.putTrailingMetadataHandlerOption(
                CallOptionsUtil.putMetadataHandlerOption(
                    grpcCallContext.getCallOptions(), metadataHandler),
                trailingMetadataHandler));
  }

  public Metadata getMetadata() {
    return responseMetadata;
  }

  public Metadata getTrailingMetadata() {
    return trailingMetadata;
  }
}
