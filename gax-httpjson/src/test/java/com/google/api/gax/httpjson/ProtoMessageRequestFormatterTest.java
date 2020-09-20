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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class ProtoMessageRequestFormatterTest {
  private Field field;
  private HttpRequestFormatter<Field> formatter;

  @Before
  public void setUp() {
    field =
        Field.newBuilder()
            .setNumber(2)
            .setName("field_name1")
            .addOptions(Option.newBuilder().setName("opt_name1").build())
            .addOptions(Option.newBuilder().setName("opt_name2").build())
            .setCardinality(Cardinality.CARDINALITY_OPTIONAL)
            .build();

    formatter =
        ProtoMessageRequestFormatter.<Field>newBuilder()
            .setPath(
                "/api/v1/names/{name}/aggregated",
                new FieldsExtractor<Field, Map<String, String>>() {
                  @Override
                  public Map<String, String> extract(Field request) {
                    Map<String, String> fields = new HashMap<>();
                    ProtoRestSerializer<Field> serializer = ProtoRestSerializer.create();
                    serializer.putPathParam(fields, "name", request.getName());
                    serializer.putPathParam(fields, "kindValue", request.getKindValue());
                    return fields;
                  }
                })
            .setQueryParamsExtractor(
                new FieldsExtractor<Field, Map<String, List<String>>>() {
                  @Override
                  public Map<String, List<String>> extract(Field request) {
                    Map<String, List<String>> fields = new HashMap<>();
                    ProtoRestSerializer<Field> serializer = ProtoRestSerializer.create();
                    serializer.putQueryParam(fields, "number", request.getNumber());
                    serializer.putQueryParam(fields, "typeUrl", request.getTypeUrl());
                    return fields;
                  }
                })
            .setRequestBodyExtractor(
                new FieldsExtractor<Field, String>() {
                  @Override
                  public String extract(Field request) {
                    ProtoRestSerializer<Field> serializer = ProtoRestSerializer.create();
                    return serializer.toBody("field", request);
                  }
                })
            .build();
  }

  @Test
  public void getQueryParamNames() {
    Map<String, List<String>> queryParamNames = formatter.getQueryParamNames(field);
    Map<String, List<String>> expected = new HashMap<>();
    expected.put("number", Arrays.asList("2"));
    Truth.assertThat(queryParamNames).isEqualTo(expected);
  }

  @Test
  public void getRequestBody() {
    String bodyJson = formatter.getRequestBody(field);
    String expectedBodyJson =
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
    Truth.assertThat(bodyJson).isEqualTo(expectedBodyJson);
  }

  @Test
  public void getPath() {
    String path = formatter.getPath(field);
    Truth.assertThat(path).isEqualTo("api/v1/names/field_name1/aggregated");
  }

  @Test
  public void getPathTemplate() {
    String path =
        formatter.getPathTemplate().instantiate(Collections.singletonMap("name", "field_name1"));
    Truth.assertThat(path).isEqualTo("api/v1/names/field_name1/aggregated");
  }
}
