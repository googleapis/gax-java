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
import com.google.longrunning.Operation;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Option;
import com.google.protobuf.Timestamp;
import com.google.rpc.Status;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
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
  private String fieldJsonNumericEnum;
  private String fieldJsonUnknownNumericEnum;

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

    fieldJsonNumericEnum =
        "{\n"
            + "  \"cardinality\": 1,\n"
            + "  \"number\": 2,\n"
            + "  \"name\": \"field_name1\",\n"
            + "  \"options\": [{\n"
            + "    \"name\": \"opt_name1\"\n"
            + "  }, {\n"
            + "    \"name\": \"opt_name2\"\n"
            + "  }]\n"
            + "}";

    fieldJsonUnknownNumericEnum =
        "{\n"
            + "  \"cardinality\": 7,\n"
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
  public void toJson_numericEnumTrue() {
    String fieldToJson = requestSerializer.toJson(field, true);
    Truth.assertThat(fieldToJson).isEqualTo(fieldJsonNumericEnum);
  }

  @Test
  public void toJson_numericEnumFalse() {
    String fieldToJson = requestSerializer.toJson(field, false);
    Truth.assertThat(fieldToJson).isEqualTo(fieldJson);
  }

  @Test
  public void fromJson_numericEnumTrue() {
    Field fieldFromJson =
        requestSerializer.fromJson(new StringReader(fieldJsonNumericEnum), Field.newBuilder());
    Truth.assertThat(fieldFromJson).isEqualTo(field);
  }

  @Test
  public void fromJson_numericEnumFalse() {
    Field fieldFromJson =
        requestSerializer.fromJson(new StringReader(fieldJson), Field.newBuilder());
    Truth.assertThat(fieldFromJson).isEqualTo(field);
  }

  @Test
  public void fromJson_numericEnumTrueAndUnknownEnum() {
    Field expected = field.toBuilder().setCardinalityValue(7).build();
    Field fieldFromJson =
        requestSerializer.fromJson(
            new StringReader(fieldJsonUnknownNumericEnum), Field.newBuilder());
    Truth.assertThat(fieldFromJson).isEqualTo(expected);
  }

  @Test
  public void fromJsonInvalidJson() {
    try {
      requestSerializer.fromJson(new StringReader("heh"), Field.newBuilder());
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
    expectedFields.put("optName2", "0");
    expectedFields.put("optName3", "three");
    expectedFields.put("optName4", "");

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
    requestSerializer.putQueryParam(
        fields, "optName6", Duration.newBuilder().setSeconds(1).setNanos(1).build());
    requestSerializer.putQueryParam(
        fields, "optName7", Timestamp.newBuilder().setSeconds(1).setNanos(1).build());
    requestSerializer.putQueryParam(
        fields, "optName8", FieldMask.newBuilder().addPaths("a.b").addPaths("c.d").build());
    requestSerializer.putQueryParam(fields, "optName9", Int32Value.of(1));
    requestSerializer.putQueryParam(fields, "optName10", FloatValue.of(1.1f));
    com.google.longrunning.Operation operation = Operation.newBuilder()
        .setDone(true)
        .setError(Status.newBuilder()
            .addDetails(Any.newBuilder().setValue(ByteString.copyFrom("error-1",
              Charset.defaultCharset())).build())
            .addDetails(Any.newBuilder().setValue(ByteString.copyFrom("error-2",
              Charset.defaultCharset())).build()))
        .setName("test")
        .build();
    requestSerializer.putQueryParam(fields, "optName11", operation);

    Map<String, List<String>> expectedFields = new HashMap<>();
    expectedFields.put("optName1", Arrays.asList("1"));
    expectedFields.put("optName2", Arrays.asList("0"));
    expectedFields.put("optName3", Arrays.asList("three"));
    expectedFields.put("optName4", Arrays.asList(""));
    expectedFields.put("optName5", Arrays.asList("four", "five"));
    expectedFields.put("optName6", Arrays.asList("1.000000001s"));
    expectedFields.put("optName7", Arrays.asList("1970-01-01T00:00:01.000000001Z"));
    expectedFields.put("optName8", Arrays.asList("a.b,c.d"));
    expectedFields.put("optName9", Arrays.asList("1"));
    expectedFields.put("optName10", Arrays.asList("1.1"));
    expectedFields.put("optName11.name", Arrays.asList("test"));
    expectedFields.put("optName11.done", Arrays.asList("true"));
    expectedFields.put("optName11.error.details.value", Arrays.asList("error-1", "error-2"));

    Truth.assertThat(fields).isEqualTo(expectedFields);
  }

  @Test
  public void toBody() {
    String body = requestSerializer.toBody("bodyField1", field, false);
    Truth.assertThat(body).isEqualTo(fieldJson);
  }
}
