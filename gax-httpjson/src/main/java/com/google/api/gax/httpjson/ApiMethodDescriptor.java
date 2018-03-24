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
package com.google.api.gax.httpjson;

import com.google.api.core.BetaApi;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@BetaApi
@AutoValue
/* Method descriptor for messages to be transmitted over HTTP. */
public abstract class ApiMethodDescriptor<RequestT, ResponseT> {

  public abstract String getFullMethodName();

  public abstract HttpRequestFormatter<RequestT> getRequestFormatter();

  @Nullable
  public abstract HttpResponseParser<ResponseT> getResponseParser();

  /** Return the HTTP method for this request message type. */
  @Nullable
  public abstract String getHttpMethod();

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new AutoValue_ApiMethodDescriptor.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder<RequestT, ResponseT> {

    public abstract Builder<RequestT, ResponseT> setFullMethodName(String fullMethodName);

    public abstract Builder<RequestT, ResponseT> setRequestFormatter(
        HttpRequestFormatter<RequestT> requestFormatter);

    public abstract Builder<RequestT, ResponseT> setResponseParser(
        HttpResponseParser<ResponseT> responseParser);

    public abstract Builder<RequestT, ResponseT> setHttpMethod(String httpMethod);

    public abstract ApiMethodDescriptor<RequestT, ResponseT> build();
  }
}
