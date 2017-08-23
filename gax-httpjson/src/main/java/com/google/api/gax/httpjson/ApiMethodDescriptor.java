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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;

@BetaApi
public class ApiMethodDescriptor<RequestT, ResponseT> {
  private final String fullMethodName;
  private final Gson baseGson;
  private final Type requestType;
  private final Type responseType;

  private final List<String> pathParams;
  private final List<String> queryParams;

  /* In the form "[prefix]%s[suffix]", where
   *    [prefix] is any string; if length greater than 0, it should end with '/'.
   *    [suffix] is any string; if length greater than 0, it should begin with '/'.
   * This String format is applied to a serialized ResourceName to create the relative endpoint path.
   */
  private final String endpointPathTemplate;

  public static <RequestT, ResponseT> ApiMethodDescriptor<RequestT, ResponseT> create(
      String fullMethodName,
      RequestT requestInstance,
      ResponseT responseInstance,
      String endpointPathTemplate,
      List<String> pathParams,
      List<String> queryParams) {
    final Gson baseGson = new GsonBuilder().create();
    return new ApiMethodDescriptor<>(
        fullMethodName, baseGson, requestInstance.getClass(), responseInstance.getClass(), endpointPathTemplate, pathParams, queryParams);
  }

  private ApiMethodDescriptor(
      String fullMethodName,
      Gson baseGson,
      Type requestType,
      Type responseType,
      String endpointPathTemplate,
      List<String> pathParams,
      List<String> queryParams) {
    this.fullMethodName = fullMethodName;
    this.requestType = requestType;
    this.responseType = responseType;
    this.baseGson = baseGson;
    this.endpointPathTemplate = endpointPathTemplate;
    this.pathParams = pathParams;
    this.queryParams = queryParams;
  }

  public ResponseT parseResponse(Reader input) {
    return this.baseGson.fromJson(input, responseType);
  }

  public void writeRequest(Appendable output, RequestT request) {
    this.baseGson.toJson(request, output);
  }


}
