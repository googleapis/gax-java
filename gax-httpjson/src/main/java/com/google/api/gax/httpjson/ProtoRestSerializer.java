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
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class serializes/deserializes protobuf {@link Message} for REST interactions. It serializes
 * requests protobuf messages into REST messages, splitting the message into the JSON request body,
 * URL path parameters, and query parameters. It deserializes JSON responses into response protobuf
 * message.
 */
@BetaApi
public class ProtoRestSerializer<RequestT extends Message> {
  private final TypeRegistry registry;

  // well-known types obtained from
  // https://github.com/googleapis/gapic-showcase/blob/fe414784c18878d704b884348d84c68fd6b87466/util/genrest/resttools/populatefield.go#L27
  private static final Set<Class<GeneratedMessageV3>> jsonSerializableMessages = new HashSet(
      Arrays.asList(
          com.google.protobuf.BoolValue.class,
          com.google.protobuf.BytesValue.class,
          com.google.protobuf.DoubleValue.class,
          com.google.protobuf.Duration.class,
          com.google.protobuf.FieldMask.class,
          com.google.protobuf.FloatValue.class,
          com.google.protobuf.Int32Value.class,
          com.google.protobuf.Int64Value.class,
          com.google.protobuf.StringValue.class,
          com.google.protobuf.Timestamp.class,
          com.google.protobuf.UInt32Value.class,
          com.google.protobuf.UInt64Value.class
      ));

  private boolean isNonSerializableMessageValue(Object value) {
    return value instanceof GeneratedMessageV3 && !jsonSerializableMessages.contains(value.getClass());
  }

  private ProtoRestSerializer(TypeRegistry registry) {
    this.registry = registry;
  }

  /** Creates a new instance of ProtoRestSerializer. */
  public static <RequestT extends Message> ProtoRestSerializer<RequestT> create() {
    return create(TypeRegistry.getEmptyTypeRegistry());
  }

  /** Creates a new instance of ProtoRestSerializer. */
  static <RequestT extends Message> ProtoRestSerializer<RequestT> create(TypeRegistry registry) {
    return new ProtoRestSerializer<>(registry);
  }

  /**
   * Serializes the data from {@code message} to a JSON string. The implementation relies on
   * protobuf native JSON formatter.
   *
   * @param message a message to serialize
   * @param numericEnum a boolean flag that determine if enum values should be serialized to number
   *     or not
   * @throws InvalidProtocolBufferException if failed to serialize the protobuf message to JSON
   *     format
   */
  String toJson(MessageOrBuilder message, boolean numericEnum) {
    try {
      Printer printer = JsonFormat.printer().usingTypeRegistry(registry);
      if (numericEnum) {
        return printer.printingEnumsAsInts().print(message);
      } else {
        return printer.print(message);
      }
    } catch (InvalidProtocolBufferException e) {
      throw new RestSerializationException("Failed to serialize message to JSON", e);
    }
  }

  /**
   * Deserializes a {@code message} from an input stream to a protobuf message.
   *
   * @param json the input reader with a JSON-encoded message in it
   * @param builder an empty builder for the specific {@code RequestT} message to serialize
   * @throws RestSerializationException if failed to deserialize a protobuf message from the JSON
   *     stream
   */
  @SuppressWarnings("unchecked")
  RequestT fromJson(Reader json, Message.Builder builder) {
    try {
      JsonFormat.parser().usingTypeRegistry(registry).ignoringUnknownFields().merge(json, builder);
      return (RequestT) builder.build();
    } catch (IOException e) {
      throw new RestSerializationException("Failed to parse response message", e);
    }
  }

  /**
   * Puts a message field in {@code fields} map which will be used to populate URL path of a
   * request.
   *
   * @param fields a map with serialized fields
   * @param fieldName a field name
   * @param fieldValue a field value
   */
  public void putPathParam(Map<String, String> fields, String fieldName, Object fieldValue) {
    fields.put(fieldName, String.valueOf(fieldValue));
  }

  private void putDecomposedMessageQueryParam(
      Map<String, List<String>> fields, String fieldName, Object fieldValue
  ) {
    for (Map.Entry<FieldDescriptor, Object> fieldEntry : ((GeneratedMessageV3) fieldValue)
        .getAllFields().entrySet()) {
      Object value = fieldEntry.getValue();
      putQueryParam(fields, String.format("%s.%s",fieldName, fieldEntry.getKey().toProto().getName()), fieldEntry.getValue());
    }
  }

  /**
   * Puts a message field in {@code fields} map which will be used to populate query parameters of a
   * request.
   *
   * @param fields a map with serialized fields
   * @param fieldName a field name
   * @param fieldValue a field value
   */
  public void putQueryParam(Map<String, List<String>> fields, String fieldName, Object fieldValue) {
    ArrayList<String> paramValueList = new ArrayList();
    if (fieldValue instanceof List<?>) {
      boolean hasProcessedMessage = false;
      for (Object fieldValueItem : (List<?>) fieldValue) {
        if (isNonSerializableMessageValue(fieldValueItem)) {
          putDecomposedMessageQueryParam(fields, fieldName, fieldValueItem);
          hasProcessedMessage = true;
        } else {
          paramValueList.add(toQueryParamValue(fieldValueItem));
        }
      }
      if (hasProcessedMessage) {
        return;
      }
    } else {
      if (isNonSerializableMessageValue(fieldValue)) {
        putDecomposedMessageQueryParam(fields, fieldName, fieldValue);
        return;
      } else {
        paramValueList.add(toQueryParamValue(fieldValue));
      }
    }

    if (fields.containsKey(fieldName)) {
      fields.get(fieldName).addAll(paramValueList);
    } else {
      fields.put(fieldName, paramValueList);
    }
  }

  /**
   * Serializes a message to a request body in a form of JSON-encoded string.
   *
   * @param fieldName a name of a request message field this message belongs to
   * @param fieldValue a field value to serialize
   */
  public String toBody(String fieldName, RequestT fieldValue) {
    return toJson(fieldValue, false);
  }

  /**
   * Serializes a message to a request body in a form of JSON-encoded string.
   *
   * @param fieldName a name of a request message field this message belongs to
   * @param fieldValue a field value to serialize
   * @param numericEnum a boolean flag that determine if enum values should be serialized to number
   */
  public String toBody(String fieldName, RequestT fieldValue, boolean numericEnum) {
    return toJson(fieldValue, numericEnum);
  }

  /**
   * Serializes an object to a query parameter Handles the case of a message such as Duration,
   * FieldMask or Int32Value to prevent wrong formatting that String.valueOf() would make
   *
   * @param fieldValue a field value to serialize
   */
  public String toQueryParamValue(Object fieldValue) {
    // This will match with message types that are serializable (e.g. FieldMask)
    if (fieldValue instanceof GeneratedMessageV3 && !isNonSerializableMessageValue(fieldValue)) {
      return toJson(((GeneratedMessageV3) fieldValue).toBuilder(), false)
          .replaceAll("^\"", "")
          .replaceAll("\"$", "");
    }
    if (fieldValue instanceof ByteString) {
      return ((ByteString) fieldValue).toStringUtf8();
    }
    return String.valueOf(fieldValue);
  }
}
