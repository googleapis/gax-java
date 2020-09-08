package com.google.api.gax.httpjson;

import static org.junit.Assert.*;

import com.google.common.truth.Truth;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Option;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;

/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ProtoMessageResponseParserTest {
  private ProtoMessageResponseParser<Field> parser;
  private Field field;
  private String fieldJson;

  @Before
  public void setUp() {
    parser =
        ProtoMessageResponseParser.<Field>newBuilder()
            .setDefaultInstance(Field.getDefaultInstance())
            .build();

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
  public void parse() {
    Field actualField =
        parser.parse(new ByteArrayInputStream(fieldJson.getBytes(StandardCharsets.UTF_8)));
    Truth.assertThat(actualField).isEqualTo(field);
  }

  @Test
  public void parseInvalidJson() {
    try {
      parser.parse(new ByteArrayInputStream("invalid".getBytes(StandardCharsets.UTF_8)));
      Truth.assertThat(true).isFalse();
    } catch (RuntimeException e) {
      Truth.assertThat(e.getCause()).isInstanceOf(IOException.class);
    }
  }

  @Test
  public void serialize() {
    String actualFieldJson = parser.serialize(field);
    Truth.assertThat(actualFieldJson).isEqualTo(fieldJson);
  }


}
