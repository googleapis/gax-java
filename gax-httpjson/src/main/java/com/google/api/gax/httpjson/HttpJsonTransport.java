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
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.rpc.Transport;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import java.util.List;

/** A Transport for gRPC. */
@AutoValue
@BetaApi
public abstract class HttpJsonTransport extends Transport {

  /** The name of the Http-JSON transport. */
  public static String getHttpJsonTransportName() {
    return "httpjson";
  }

  @Override
  public String getTransportName() {
    return getHttpJsonTransportName();
  }

  @Override
  public abstract List<BackgroundResource> getBackgroundResources();

  /** The channel in use. */
  public abstract HttpJsonChannel getChannel();

  public static Builder newBuilder() {
    return new AutoValue_HttpJsonTransport.Builder()
        .setBackgroundResources(Lists.<BackgroundResource>newArrayList());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setBackgroundResources(List<BackgroundResource> value);

    public abstract Builder setChannel(HttpJsonChannel value);

    public abstract HttpJsonTransport build();
  }
}
