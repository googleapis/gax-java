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

/** Grpc-specific settings for creating callables. */
public class HttpJsonCallSettings<RequestT, ResponseT> {
  private final HttpRequestFormatter<RequestT> requestFormatter;
  private final HttpResponseFormatter<ResponseT> responseFormatter;

  private HttpJsonCallSettings(
      HttpRequestFormatter<RequestT> requestFormatter,
      HttpResponseFormatter<ResponseT> responseFormatter) {
    this.requestFormatter = requestFormatter;
    this.responseFormatter = responseFormatter;
  }

  public HttpRequestFormatter<RequestT> getRequestFormatter() {
    return requestFormatter;
  }

  public HttpResponseFormatter<ResponseT> getResponseFormatter() {
    return responseFormatter;
  }

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<>();
  }

  public static <RequestT, ResponseT> HttpJsonCallSettings<RequestT, ResponseT> create(
      HttpRequestFormatter<RequestT> requestFormatter,
      HttpResponseFormatter<ResponseT> responseFormatter) {
    return HttpJsonCallSettings.<RequestT, ResponseT>newBuilder()
        .setRequestFormatter(requestFormatter)
        .setResponseFormatter(responseFormatter)
        .build();
  }

  public Builder<RequestT, ResponseT> toBuilder() {
    return new Builder<>(this);
  }

  public static class Builder<RequestT, ResponseT> {
    private HttpRequestFormatter<RequestT> requestFormatter;
    private HttpResponseFormatter<ResponseT> responseFormatter;

    private Builder() {}

    private Builder(HttpJsonCallSettings<RequestT, ResponseT> settings) {
      this.requestFormatter = settings.requestFormatter;
      this.responseFormatter = settings.responseFormatter;
    }

    public Builder<RequestT, ResponseT> setRequestFormatter(
        HttpRequestFormatter<RequestT> requestFormatter) {
      this.requestFormatter = requestFormatter;
      return this;
    }

    public Builder<RequestT, ResponseT> setResponseFormatter(
        HttpResponseFormatter<ResponseT> responseFormatter) {
      this.responseFormatter = responseFormatter;
      return this;
    }

    public HttpJsonCallSettings<RequestT, ResponseT> build() {
      return new HttpJsonCallSettings<RequestT, ResponseT>(requestFormatter, responseFormatter);
    }
  }
}
