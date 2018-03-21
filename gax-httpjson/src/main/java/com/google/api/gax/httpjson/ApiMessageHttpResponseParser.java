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

import com.google.api.core.InternalApi;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Utility class to parse {@link ApiMessage}s from HTTP responses. */
public class ApiMessageHttpResponseParser<ResponseT extends ApiMessage>
    implements HttpResponseParser<ResponseT> {

  private final ApiMethodDescriptor<?, ResponseT> methodDescriptor;
  private final Gson responseMarshaller;

  /* Constructs an ApiMessageHttpResponseParser from an ApiMethodDescriptor. */
  public ApiMessageHttpResponseParser(final ApiMethodDescriptor<?, ResponseT> methodDescriptor) {
    Preconditions.checkNotNull(methodDescriptor);
    this.methodDescriptor = methodDescriptor;

    final Gson baseGson = new GsonBuilder().create();
    TypeAdapter responseTypeAdapter;

    if (methodDescriptor.getResponseType() == null) {
      this.responseMarshaller = null;
    } else {
      responseTypeAdapter =
          new TypeAdapter<ResponseT>() {
            @Override
            public void write(JsonWriter out, ResponseT value) {
              baseGson.toJson(value, methodDescriptor.getResponseType(), out);
            }

            @Override
            public ResponseT read(JsonReader in) {
              return baseGson.fromJson(in, methodDescriptor.getResponseType());
            }
          };
      this.responseMarshaller =
          new GsonBuilder()
              .registerTypeAdapter(methodDescriptor.getResponseType(), responseTypeAdapter)
              .create();
    }
  }

  @Override
  public ResponseT parse(InputStream httpResponseBody) {
    if (methodDescriptor.getResponseType() == null) {
      return null;
    } else {
      return responseMarshaller.fromJson(
          new InputStreamReader(httpResponseBody), methodDescriptor.getResponseType());
    }
  }

  @InternalApi
  public String writeResponse(Object response) {
    return responseMarshaller.toJson(response);
  }
}
