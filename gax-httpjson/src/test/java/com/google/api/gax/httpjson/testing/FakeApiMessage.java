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
package com.google.api.gax.httpjson.testing;

import com.google.api.core.InternalApi;
import com.google.api.gax.httpjson.ApiMessage;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

/** Simple implementation of ApiMessage. */
@InternalApi("for testing")
public class FakeApiMessage implements ApiMessage {
  private final Map<String, List<String>> fieldValues;
  private final ApiMessage messageBody;

  /** Instantiate a FakeApiMessage with a message body and a map of field names and their values. */
  public FakeApiMessage(Map<String, List<String>> fieldValues, ApiMessage messageBody) {
    this.fieldValues = ImmutableMap.copyOf(fieldValues);
    this.messageBody = messageBody;
  }

  @Override
  public Map<String, List<String>> populateFieldsInMap(Set<String> fieldNames) {
    Map<String, List<String>> fieldMap = new TreeMap<>();
    for (String key : fieldNames) {
      if (fieldValues.containsKey(key)) {
        fieldMap.put(key, fieldValues.get(key));
      }
    }
    return fieldMap;
  }

  /* Get the first value of a field in this message. */
  @Nullable
  @Override
  public String getFieldStringValue(String fieldName) {
    List<String> fieldValue = fieldValues.get(fieldName);
    if (fieldValue == null || fieldValue.size() == 0) {
      return null;
    }
    return fieldValue.get(0);
  }

  /* If this is a Request object, return the inner ApiMessage that represents the body
   * of the request; else return null. */
  @Nullable
  @Override
  public ApiMessage getApiMessageRequestBody() {
    return messageBody;
  }
}
