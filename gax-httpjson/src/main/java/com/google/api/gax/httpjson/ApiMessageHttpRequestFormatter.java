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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Utility class to parse ApiMessages into various HTTP request parts. */
@BetaApi
public class ApiMessageHttpRequestFormatter implements HttpRequestFormatter<ApiMessage> {
  @Override
  public Map<String, List<String>> getQueryParams(ApiMessage apiMessage, Set<String> paramNames) {
    Map<String, List<String>> queryParams = new HashMap<>();
    Map<String, List<String>> nullableParams = apiMessage.populateFieldsInMap(paramNames);
    Iterator<Map.Entry<String, List<String>>> iterator = nullableParams.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, List<String>> pair = iterator.next();
      if (pair.getValue() != null && pair.getValue().size() > 0 && pair.getValue().get(0) != null) {
        queryParams.put(pair.getKey(), pair.getValue());
      }
    }
    return queryParams;
  }

  @Override
  public Map<String, String> getPathParams(ApiMessage apiMessage, Set<String> paramNames) {
    Map<String, String> pathParams = new HashMap<>();
    Map<String, List<String>> pathParamMap = apiMessage.populateFieldsInMap(paramNames);
    Iterator<Map.Entry<String, List<String>>> iterator = pathParamMap.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, List<String>> pair = iterator.next();
      pathParams.put(pair.getKey(), pair.getValue().get(0));
    }
    return pathParams;
  }

  @Override
  public void writeRequestBody(ApiMessage apiMessage, Gson marshaller, Appendable writer) {
    ApiMessage body = apiMessage.getRequestBody();
    if (body != null) {
      marshaller.toJson(body, writer);
    }
  }
}
