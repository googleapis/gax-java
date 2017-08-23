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
import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeMarshaller;
import com.google.gson.JsonMarshaller;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import javax.print.DocFlavor.STRING;

@BetaApi
public class ApiMethodDescriptor<RequestT, ResponseT> {
  public static <RequestT, ResponseT> ApiMethodDescriptor<RequestT, ResponseT> create(String fullMethodName, Type requestType, Type responseType, String endpointPathTemplate) {

    TypeAdapter<RequestT> requestMarshaller = new com.google.gson.TypeAdapter<RequestT>() {
      @Override
      public void write(JsonWriter out, RequestT value) throws IOException {

      }

      @Override
      public RequestT read(JsonReader in) throws IOException {
        return null;
      }
    };

    TypeAdapter<RequestT> responseMarshaller = new com.google.gson.TypeAdapter<RequestT>() {
      @Override
      public void write(JsonWriter out, RequestT value) throws IOException {

      }

      @Override
      public RequestT read(JsonReader in) throws IOException {
        return null;
      }
    };

    Gson requestGson = new GsonBuilder().registerTypeAdapter(requestType, requestMarshaller).create();
    Gson responseGson = new GsonBuilder().registerTypeAdapter(responseType, responseMarshaller).create();

    return new ApiMethodDescriptor<>(fullMethodName, requestGson, responseGson, endpointPathTemplate);
  }
  
  private ApiMethodDescriptor(String fullMethodName, Gson requestGson, Gson responseGson, String endpointPathTemplate) {
    this.fullMethodName = fullMethodName;
    this.requestGson = requestGson;
    this.responseGson = responseGson;
    this.endpointPathTemplate = endpointPathTemplate;
  }

  private String fullMethodName;


  private Gson requestGson;

  private Gson responseGson;

  /* In the form "[prefix]%s[suffix]", where
   *    [prefix] is any string; if length greater than 0, it should end with '/'.
   *    [suffix] is any string; if length greater than 0, it should begin with '/'.
   * This String format is applied to a serialized ResourceName to create the relative endpoint path.
   */
  private String endpointPathTemplate;

//  private List<String> pathParam();
//
//  private List<String> queryParams();



}
