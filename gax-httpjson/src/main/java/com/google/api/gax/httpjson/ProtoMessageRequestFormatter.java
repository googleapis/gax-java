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
import com.google.api.core.InternalApi;
import com.google.api.pathtemplate.PathTemplate;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Map;

/** Creates parts of a HTTP request from a protobuf message. */
@BetaApi
public class ProtoMessageRequestFormatter<RequestT extends Message>
    implements HttpRequestFormatter<RequestT> {

  private final FieldsExtractor<RequestT, String> requestBodyExtractor;
  // Using of triple nested generics (which is not pretty) is predetermined by the
  // Map<String, List<String>> returned value type of the getQueryParamNames interface method
  // implemented by this class.
  private final FieldsExtractor<RequestT, Map<String, List<String>>> queryParamsExtractor;
  private final String rawPath;
  private final PathTemplate pathTemplate;
  private final FieldsExtractor<RequestT, Map<String, String>> pathVarsExtractor;

  private ProtoMessageRequestFormatter(
      FieldsExtractor<RequestT, String> requestBodyExtractor,
      FieldsExtractor<RequestT, Map<String, List<String>>> queryParamsExtractor,
      String rawPath,
      PathTemplate pathTemplate,
      FieldsExtractor<RequestT, Map<String, String>> pathVarsExtractor) {
    this.requestBodyExtractor = requestBodyExtractor;
    this.queryParamsExtractor = queryParamsExtractor;
    this.rawPath = rawPath;
    this.pathTemplate = pathTemplate;
    this.pathVarsExtractor = pathVarsExtractor;
  }

  public static <RequestT extends Message>
      ProtoMessageRequestFormatter.Builder<RequestT> newBuilder() {
    return new Builder<>();
  }

  public Builder<RequestT> toBuilder() {
    return new Builder<RequestT>()
        .setPath(rawPath, pathVarsExtractor)
        .setQueryParamsExtractor(queryParamsExtractor)
        .setRequestBodyExtractor(requestBodyExtractor);
  }

  /* {@inheritDoc} */
  @Override
  public Map<String, List<String>> getQueryParamNames(RequestT apiMessage) {
    return queryParamsExtractor.extract(apiMessage);
  }

  /* {@inheritDoc} */
  @Override
  public String getRequestBody(RequestT apiMessage) {
    return requestBodyExtractor.extract(apiMessage);
  }

  /* {@inheritDoc} */
  @Override
  public String getPath(RequestT apiMessage) {
    return pathTemplate.instantiate(pathVarsExtractor.extract(apiMessage));
  }

  /* {@inheritDoc} */
  @Override
  public PathTemplate getPathTemplate() {
    return pathTemplate;
  }

  // This has class has compound setter methods (multiple arguments in setters), that is why not
  // using @AutoValue.
  public static class Builder<RequestT extends Message> {
    private FieldsExtractor<RequestT, String> requestBodyExtractor;
    private FieldsExtractor<RequestT, Map<String, List<String>>> queryParamsExtractor;
    private String rawPath;
    private FieldsExtractor<RequestT, Map<String, String>> pathVarsExtractor;

    public Builder<RequestT> setRequestBodyExtractor(
        FieldsExtractor<RequestT, String> requestBodyExtractor) {
      this.requestBodyExtractor = requestBodyExtractor;
      return this;
    }

    public Builder<RequestT> setQueryParamsExtractor(
        FieldsExtractor<RequestT, Map<String, List<String>>> queryParamsExtractor) {
      this.queryParamsExtractor = queryParamsExtractor;
      return this;
    }

    public Builder<RequestT> setPath(
        String rawPath, FieldsExtractor<RequestT, Map<String, String>> pathVarsExtractor) {
      this.rawPath = rawPath;
      this.pathVarsExtractor = pathVarsExtractor;
      return this;
    }

    @InternalApi
    public Builder<RequestT> updateRawPath(String target, String replacement) {
      this.rawPath = this.rawPath.replace(target, replacement);
      return this;
    }

    public ProtoMessageRequestFormatter<RequestT> build() {
      return new ProtoMessageRequestFormatter<>(
          requestBodyExtractor,
          queryParamsExtractor,
          rawPath,
          PathTemplate.create(rawPath),
          pathVarsExtractor);
    }
  }
}
