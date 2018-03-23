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
package com.google.api.gax.httpjson;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;

/** Utility class to parse {@link ApiMessage}s from HTTP responses. */
@AutoValue
public abstract class ApiMessageHttpResponseParser<ResponseT extends ApiMessage>
    implements HttpResponseParser<ResponseT> {

  public abstract ResponseT getResponseInstance();

  protected abstract Gson getResponseMarshaller();

  /**
   * Constructs an ApiMessageHttpResponseParser from an instance of the message type to be
   * formatted.
   */
  private static <ResponseT extends ApiMessage> ApiMessageHttpResponseParser<ResponseT> create(
      final ResponseT responseInstance) {
    final Gson baseGson = new GsonBuilder().create();
    TypeAdapter responseTypeAdapter;
    Gson responseMarshaller = null;

    if (responseInstance != null) {
      responseTypeAdapter =
          new TypeAdapter<ResponseT>() {
            @Override
            public void write(JsonWriter out, ResponseT value) {
              baseGson.toJson(value, responseInstance.getClass(), out);
            }

            @Override
            public ResponseT read(JsonReader in) {
              return baseGson.fromJson(in, responseInstance.getClass());
            }
          };
      responseMarshaller =
          new GsonBuilder()
              .registerTypeAdapter(responseInstance.getClass(), responseTypeAdapter)
              .create();
    }

    return new AutoValue_ApiMessageHttpResponseParser<>(responseInstance, responseMarshaller);
  }

  public static <ResponseT extends ApiMessage>
      ApiMessageHttpResponseParser.Builder<ResponseT> newBuilder() {
    return new ApiMessageHttpResponseParser.Builder<>();
  }

  @Override
  public ResponseT parse(InputStream httpResponseBody) {
    if (getResponseInstance() == null) {
      return null;
    } else {
      Type responseType = getResponseInstance().getClass();
      return getResponseMarshaller()
          .fromJson(new InputStreamReader(httpResponseBody), responseType);
    }
  }

  @Override
  public String serialize(ResponseT response) {
    return getResponseMarshaller().toJson(response);
  }

  public static class Builder<ResponseT extends ApiMessage> {
    private ResponseT responseInstance;

    private Builder() {}

    public Builder<ResponseT> setResponseInstance(ResponseT responseInstance) {
      this.responseInstance = responseInstance;
      return this;
    }

    public ApiMessageHttpResponseParser<ResponseT> build() {
      return ApiMessageHttpResponseParser.create(responseInstance);
    }
  }
}
