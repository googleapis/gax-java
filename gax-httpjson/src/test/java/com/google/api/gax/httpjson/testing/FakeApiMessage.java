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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/** Simple implementation of ApiMessage. */
@InternalApi("for testing")
public class FakeApiMessage implements ApiMessage {
  private final Map<String, Object> fieldValues;
  private final ApiMessage messageBody;
  private List<String> fieldMask;

  /** Instantiate a FakeApiMessage with a message body and a map of field names and their values. */
  public FakeApiMessage(
      Map<String, Object> fieldValues, ApiMessage messageBody, List<String> fieldMask) {
    this.fieldValues = new TreeMap<>(fieldValues);
    this.messageBody = messageBody;
    this.fieldMask = fieldMask;
  }

  @Nullable
  @Override
  public Object getFieldValue(String fieldName) {
    return fieldValues.get(fieldName);
  }

  @Nullable
  @Override
  public List<String> getFieldMask() {
    return fieldMask;
  }

  /* If this is a Request object, return the inner ApiMessage that represents the body
   * of the request; else return null. */
  @Nullable
  @Override
  public ApiMessage getApiMessageRequestBody() {
    return messageBody;
  }
}
