/*
 * Copyright 2015, Google Inc.
 * All rights reserved.
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

package com.google.api.gax.protobuf;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;

/**
 * Represents the type of a protocol buffer value. Reflects the type system used by
 * {@link Expression}, and is able to represented primitive types, repeated types,
 * and maps.
 */
public abstract class Type {

  /**
   * The kind of a type. 32 bit integers are folded into 64 bit, and float into double.
   */
  @SuppressWarnings("hiding")
  public enum Kind {
    INT64,
    UINT64,
    DOUBLE,
    BOOL,
    STRING,
    BYTES,
    MESSAGE,
    ENUM,
    REPEATED,
    MAP
  }

  /**
   * Constants for representing primitive types.
   */
  public static final Type BOOL = new AutoValue_Type_Primitive(Kind.BOOL);
  public static final Type INT64 = new AutoValue_Type_Primitive(Kind.INT64);
  public static final Type UINT64 = new AutoValue_Type_Primitive(Kind.UINT64);
  public static final Type STRING = new AutoValue_Type_Primitive(Kind.STRING);
  public static final Type BYTES = new AutoValue_Type_Primitive(Kind.BYTES);
  public static final Type DOUBLE = new AutoValue_Type_Primitive(Kind.DOUBLE);


  /**
   * Deliver a message type.
   */
  public static Type forMessage(Descriptors.Descriptor message) {
    return new AutoValue_Type_Message(message);
  }

  /**
   * Deliver an enum type.
   */
  public static Type forEnum(Descriptors.EnumDescriptor enumType) {
    return new AutoValue_Type_Enum(enumType);
  }

  /**
   * Deliver a repeated type.
   */
  public static Type forRepeated(Type elemType) {
    Preconditions.checkArgument(!elemType.isRepeated() && !elemType.isMap());
    return new AutoValue_Type_Repeated(elemType);
  }

  /**
   * Deliver a map type.
   */
  public static Type forMap(Type keyType, Type valueType) {
    Preconditions.checkArgument(keyType.isPrimitive() && keyType.getKind() != Kind.BYTES
        && !valueType.isRepeated() && !valueType.isMap());
    return new AutoValue_Type_Map(keyType, valueType);
  }

  /**
   * Deliver the type of a field.
   */
  public static Type forField(Descriptors.FieldDescriptor descriptor) {
    if (descriptor.isMapField()) {
      Descriptors.Descriptor message = descriptor.getMessageType();
      return forMap(forField(message.findFieldByNumber(1)), forField(message.findFieldByNumber(2)));
    }
    Type type = internalForField(descriptor);
    if (descriptor.isRepeated()) {
      return forRepeated(type);
    }
    return type;
  }

  /**
   * Creates a type from the given Java type.
   */
  public static Type forJavaType(Class<?> type) {
    if (com.google.protobuf.Message.class.isAssignableFrom(type)) {
      @SuppressWarnings("unchecked")
      Class<? extends com.google.protobuf.Message> messageType =
          (Class<? extends com.google.protobuf.Message>) type;
      return forMessage(ProtoReflectionUtil.getDefaultInstance(messageType).getDescriptorForType());
    }
    if (type.equals(String.class)) {
      return STRING;
    }
    if (type.equals(Integer.class) || type.equals(Long.class)) {
      return INT64;
    }
    if (type.equals(Boolean.class)) {
      return BOOL;
    }
    if (type.equals(Double.class) || type.equals(Float.class)) {
      return DOUBLE;
    }
    if (type.equals(ByteString.class)) {
      return BYTES;
    }
    // TODO(wrwg): enums
    throw new IllegalArgumentException("cannot convert to proto type: " + type.getName());
  }

  private static Type internalForField(Descriptors.FieldDescriptor field) {
    switch (field.getType()) {
      case BOOL:
        return BOOL;
      case SFIXED32:
      case INT32:
      case SINT32:
      case SFIXED64:
      case INT64:
      case SINT64:
        return INT64;
      case FIXED32:
      case UINT32:
      case FIXED64:
      case UINT64:
        return UINT64;
      case FLOAT:
      case DOUBLE:
        return DOUBLE;
      case STRING:
        return STRING;
      case BYTES:
        return BYTES;
      case MESSAGE:
        return forMessage(field.getMessageType());
      case ENUM:
        return forEnum(field.getEnumType());
      default:
        throw new IllegalArgumentException("unknown field type: " + field.getType());
    }
  }

  /**
   * Gets the kind of this type.
   */
  public abstract Kind getKind();

  /**
   * Returns true if this is a primitive type.
   */
  public boolean isPrimitive() {
    return this instanceof Primitive;
  }

  /**
   * Returns true of this is a repeated type.
   */
  public boolean isRepeated() {
    return getKind() == Kind.REPEATED;
  }

  /**
   * Returns the element type of a repeated type.
   */
  public Type getRepeatedElemType() {
    Preconditions.checkArgument(isRepeated());
    return ((Repeated) this).elemType();
  }

  /**
   * Returns true if this is a map type.
   */
  public boolean isMap() {
    return getKind() == Kind.MAP;
  }

  /**
   * Returns the key type of a map type.
   */
  public Type getMapKeyType() {
    Preconditions.checkArgument(isMap());
    return ((Map) this).keyType();
  }

  /**
   * Returns the value type of a map type.
   */
  public Type getMapValueType() {
    Preconditions.checkArgument(isMap());
    return ((Map) this).valueType();
  }

  /**
   * Returns true if this is a message type.
   */
  public boolean isMessage() {
    return getKind() == Kind.MESSAGE;
  }

  /**
   * Returns the descriptor of a message type.
   */
  public Descriptors.Descriptor getMessageDescriptor() {
    Preconditions.checkArgument(isMessage());
    return ((Message) this).descriptor();
  }

  /**
   * Returns true if this is an enum type.
   */
  public boolean isEnum() {
    return getKind() == Kind.ENUM;
  }

  /**
   * Returns the descriptor of an enum type.
   */
  public Descriptors.EnumDescriptor getEnumDescriptor() {
    Preconditions.checkArgument(isEnum());
    return ((Enum) this).descriptor();
  }

  // Internal Representation
  // =======================

  /**
   * Represents a primitive type.
   */
  @AutoValue
  abstract static class Primitive extends Type {
    @Override public abstract Kind getKind();

    @Override
    public String toString() {
      switch (getKind()) {
        case INT64:
          return "int64";
        case UINT64:
          return "uint64";
        case DOUBLE:
          return "double";
        case BOOL:
          return "bool";
        case STRING:
          return "string";
        case BYTES:
          return "bytes";
        default:
          return getKind().toString();
      }
    }
  }

  /**
   * Represents a message type.
   */
  @AutoValue
  abstract static class Message extends Type {
    abstract Descriptors.Descriptor descriptor();

    @Override
    public Kind getKind() {
      return Kind.MESSAGE;
    }

    @Override
    public String toString() {
      return descriptor().getFullName();
    }
  }

  /**
   * Represents an enum type.
   */
  @AutoValue
  abstract static class Enum extends Type {
    abstract Descriptors.EnumDescriptor descriptor();

    @Override
    public Kind getKind() {
      return Kind.ENUM;
    }

    @Override
    public String toString() {
      return descriptor().getFullName();
    }
  }

  /**
   * Represents a repeated (list) type.
   */
  @AutoValue
  abstract static class Repeated extends Type {
    abstract Type elemType();

    @Override
    public Kind getKind() {
      return Kind.REPEATED;
    }

    @Override
    public String toString() {
      return "repeated " + elemType().toString();
    }
  }

  /**
   * Represents a map type.
   */
  @AutoValue
  abstract static class Map extends Type {
    abstract Type keyType();
    abstract Type valueType();

    @Override
    public Kind getKind() {
      return Kind.MAP;
    }

    @Override
    public String toString() {
      return String.format("map<%s, %s>", keyType(), valueType());
    }
  }
}
