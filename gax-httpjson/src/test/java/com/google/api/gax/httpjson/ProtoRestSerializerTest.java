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

import com.google.common.truth.Truth;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Option;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ProtoRestSerializerTest {
  private ProtoRestSerializer<Field> requestSerializer;
  private Field field;
  private String fieldJson;

  @Before
  public void setUp() {
    requestSerializer = ProtoRestSerializer.create();
    field =
        Field.newBuilder()
            .setNumber(2)
            .setName("field_name1")
            .addOptions(Option.newBuilder().setName("opt_name1").build())
            .addOptions(Option.newBuilder().setName("opt_name2").build())
            .setCardinality(Cardinality.CARDINALITY_OPTIONAL)
            .build();

    fieldJson =
        "{\n"
            + "  \"cardinality\": \"CARDINALITY_OPTIONAL\",\n"
            + "  \"number\": 2,\n"
            + "  \"name\": \"field_name1\",\n"
            + "  \"options\": [{\n"
            + "    \"name\": \"opt_name1\"\n"
            + "  }, {\n"
            + "    \"name\": \"opt_name2\"\n"
            + "  }]\n"
            + "}";
  }

  @Test
  public void toJson() {
    String fieldToJson = requestSerializer.toJson(field);
    Truth.assertThat(fieldToJson).isEqualTo(fieldJson);
  }

  @Test
  public void fromJson() {
    Field fieldFromJson =
        requestSerializer.fromJson(
            new ByteArrayInputStream(fieldJson.getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8,
            Field.newBuilder());

    Truth.assertThat(fieldFromJson).isEqualTo(field);
  }

  @Test
  public void fromJsonInvalidJson() {
    try {
      requestSerializer.fromJson(
          new ByteArrayInputStream("heh".getBytes(StandardCharsets.UTF_8)),
          StandardCharsets.UTF_8,
          Field.newBuilder());
      Assert.fail();
    } catch (RestSerializationException e) {
      Truth.assertThat(e.getCause()).isInstanceOf(IOException.class);
    }
  }

  @Test
  public void putPathParam() {
    Map<String, String> fields = new HashMap<>();
    requestSerializer.putPathParam(fields, "optName1", 1);
    requestSerializer.putPathParam(fields, "optName2", 0);
    requestSerializer.putPathParam(fields, "optName3", "three");
    requestSerializer.putPathParam(fields, "optName4", "");

    Map<String, String> expectedFields = new HashMap<>();
    expectedFields.put("optName1", "1");
    expectedFields.put("optName3", "three");

    Truth.assertThat(fields).isEqualTo(expectedFields);
  }

  @Test
  public void putQueryParam() {
    Map<String, List<String>> fields = new HashMap<>();
    requestSerializer.putQueryParam(fields, "optName1", 1);
    requestSerializer.putQueryParam(fields, "optName2", 0);
    requestSerializer.putQueryParam(fields, "optName3", "three");
    requestSerializer.putQueryParam(fields, "optName4", "");
    requestSerializer.putQueryParam(fields, "optName5", Arrays.asList("four", "five"));

    Map<String, List<String>> expectedFields = new HashMap<>();
    expectedFields.put("optName1", Arrays.asList("1"));
    expectedFields.put("optName3", Arrays.asList("three"));
    expectedFields.put("optName5", Arrays.asList("four", "five"));

    Truth.assertThat(fields).isEqualTo(expectedFields);
  }

  @Test
  public void toBody() {
    String body = requestSerializer.toBody("bodyField1", field);
    Truth.assertThat(body).isEqualTo(fieldJson);
  }
}
