/*
 * Copyright 2020 Google LLC
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
package com.google.api.gax.httpjson;

import com.google.api.core.BetaApi;
import com.google.protobuf.Message;
import java.io.InputStream;
import java.nio.charset.Charset;

/** The implementation of {@link HttpResponseParser} which works with protobuf messages. */
@BetaApi
public class ProtoMessageResponseParser<ResponseT extends Message>
    implements HttpResponseParser<ResponseT> {

  private final ResponseT defaultInstance;

  private ProtoMessageResponseParser(ResponseT defaultInstance) {
    this.defaultInstance = defaultInstance;
  }

  public static <RequestT extends Message>
      ProtoMessageResponseParser.Builder<RequestT> newBuilder() {
    return new ProtoMessageResponseParser.Builder<>();
  }

  /* {@inheritDoc} */
  @Override
  public ResponseT parse(InputStream httpContent, Charset httpContentCharset) {
    return ProtoRestSerializer.<ResponseT>create()
        .fromJson(httpContent, httpContentCharset, defaultInstance.newBuilderForType());
  }

  /* {@inheritDoc} */
  @Override
  public String serialize(ResponseT response) {
    return ProtoRestSerializer.create().toJson(response);
  }

  // Convert to @AutoValue if this class gets more complicated
  public static class Builder<ResponseT extends Message> {
    private ResponseT defaultInstance;

    public Builder<ResponseT> setDefaultInstance(ResponseT defaultInstance) {
      this.defaultInstance = defaultInstance;
      return this;
    }

    public ProtoMessageResponseParser<ResponseT> build() {
      return new ProtoMessageResponseParser<>(defaultInstance);
    }
  }
}
