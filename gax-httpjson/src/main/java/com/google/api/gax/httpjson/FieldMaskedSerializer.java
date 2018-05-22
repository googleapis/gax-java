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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.List;

/** JSON Serializer for messages with a field mask to selectively serialize fields. */
public class FieldMaskedSerializer implements JsonSerializer<ApiMessage> {
  private final List<String> fieldMask;

  public FieldMaskedSerializer(List<String> fieldMask) {
    this.fieldMask = fieldMask;
  }

  @Override
  public JsonElement serialize(
      ApiMessage requestBody, Type typeOfSrc, JsonSerializationContext context) {
    Gson gson = new GsonBuilder().serializeNulls().create();
    if (fieldMask == null) {
      return gson.toJsonTree(requestBody, typeOfSrc);
    }
    JsonObject jsonObject = new JsonObject();
    for (String fieldName : fieldMask) {
      Object fieldValue = requestBody.getFieldValue(fieldName);
      if (fieldValue != null) {
        jsonObject.add(fieldName, gson.toJsonTree(fieldValue, fieldValue.getClass()));
      } else {
        // TODO(andrealin): This doesn't distinguish between the non-existence of a field
        // and a field value being null.
        jsonObject.add(fieldName, JsonNull.INSTANCE);
      }
    }

    return jsonObject;
  }
}
