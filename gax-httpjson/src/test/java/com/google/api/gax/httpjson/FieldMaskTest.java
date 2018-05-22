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

import com.google.api.gax.httpjson.testing.FakeApiMessage;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;

public class FieldMaskTest {

  private static final TreeMessage treeMessage =
      new TreeMessage("Cedrus", Lists.newArrayList(2, 0, 6));

  @Test
  public void testFieldMaskGenus() {
    List<String> fieldMask = Lists.newArrayList("genus");

    JsonSerializer<ApiMessage> jsonSerializer = new FieldMaskedSerializer(fieldMask);
    Gson gson = new GsonBuilder().registerTypeAdapter(TreeMessage.class, jsonSerializer).create();
    Truth.assertThat(gson.toJson(treeMessage)).isEqualTo("{\"genus\":\"Cedrus\"}");
  }

  @Test
  public void testFieldMaskBranches() {
    List<String> fieldMask = Lists.newArrayList("branchLengths");

    JsonSerializer<ApiMessage> jsonSerializer = new FieldMaskedSerializer(fieldMask);
    Gson gson = new GsonBuilder().registerTypeAdapter(TreeMessage.class, jsonSerializer).create();
    Truth.assertThat(gson.toJson(treeMessage)).isEqualTo("{\"branchLengths\":[2,0,6]}");
  }

  @Test
  public void testEmptyFieldMask() {
    List<String> fieldMask = null;

    JsonSerializer<ApiMessage> jsonSerializer = new FieldMaskedSerializer(fieldMask);
    Gson gson =
        new GsonBuilder().registerTypeAdapter(FakeApiMessage.class, jsonSerializer).create();
    Truth.assertThat(gson.toJson(treeMessage))
        .isEqualTo("{\"genus\":\"Cedrus\",\"branchLengths\":[2,0,6]}");
  }

  // Represents a resource message type.
  private static class TreeMessage implements ApiMessage {

    private String genus;
    private List<Integer> branchLengths;

    TreeMessage(String genus, List<Integer> branchLengths) {
      this.genus = genus;
      this.branchLengths = branchLengths;
    }

    @Nullable
    @Override
    public Object getFieldValue(String fieldName) {
      if (fieldName.equals("genus")) {
        return genus;
      }
      if (fieldName.equals("branchLengths")) {
        return branchLengths;
      }
      return null;
    }

    @Nullable
    @Override
    public List<String> getFieldMask() {
      return null;
    }

    @Nullable
    @Override
    public ApiMessage getApiMessageRequestBody() {
      return null;
    }
  }
}
