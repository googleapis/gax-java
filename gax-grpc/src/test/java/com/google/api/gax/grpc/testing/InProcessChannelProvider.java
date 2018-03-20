/*
 * Copyright 2016 Google LLC
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
package com.google.api.gax.grpc.testing;

import com.google.api.core.BetaApi;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;

/**
 * InProcessChannelProvider is a TransportChannelProvider which constructs an in-process
 * ManagedChannel. It is meant to be used in combination with {@link InProcessServer} for functional
 * tests. The endpoint will be used as the channel's name.
 *
 * <p>Intended usage:
 *
 * <pre>{@code
 * MyFakeServiceImpl service = new MyFakeServiceImpl();
 * InProcessServer server = new InProcessServer(service, "fake:123");
 * server.start();
 *
 * BigtableDataSettings settings = BigtableDataSettings.newBuilder()
 *   .setEndpoint("fake:123")
 *   .setTransportChannelProvider(InProcessChannelProvider.create())
 *   .setCredentialsProvider(NoCredentialsProvider.create())
 *   .build();
 *
 * BigtableDataClient client = BigtableDataClient.create(settings);
 *
 * }</pre>
 */
@BetaApi("Surface for testing helpers is not stable yet.")
public final class InProcessChannelProvider extends InstantiatingGrpcChannelProvider {
  public static InProcessChannelProvider create() {
    return newBuilder().build();
  }

  private InProcessChannelProvider(Builder builder) {
    super(builder);
  }

  @Override
  public InstantiatingGrpcChannelProvider.Builder toBuilder() {
    return new Builder(this);
  }

  public static InProcessChannelProvider.Builder newBuilder() {
    return new InProcessChannelProvider.Builder();
  }

  @Override
  protected ManagedChannelBuilder<?> getChannelBuilder(String endpoint) {
    return InProcessChannelBuilder.forName(endpoint);
  }

  public static class Builder extends InstantiatingGrpcChannelProvider.Builder {
    private Builder() {
      super();
      setPoolSize(1);
    }

    private Builder(InProcessChannelProvider provider) {
      super(provider);
    }

    @Override
    public InProcessChannelProvider build() {
      return new InProcessChannelProvider(this);
    }
  }
}
