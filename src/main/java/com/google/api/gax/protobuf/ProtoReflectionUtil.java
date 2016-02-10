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

import com.google.common.base.CaseFormat;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Utilities for reflection.
 */
public class ProtoReflectionUtil {

  /**
   * Resolves a proto message name into a Java class.
   *
   * <p>The full algorithm for this requires all of proto file options, proto package name,
   * proto file name, and fully qualified message name.
   *
   * @throws ValidationException if resolution fails.
   */
  @SuppressWarnings("unchecked")
  public static Class<? extends Message> getMessageClass(FileOptions fileOptions,
      String packageName, String fileName, String messageName) {

    messageName = fixTypeName(packageName, messageName);
    String javaName = getJavaName(fileOptions, packageName, fileName, messageName);

    // Resolve the class.
    try {
      Class<?> type = Class.forName(javaName);
      if (!Message.class.isAssignableFrom(type)) {
        throw new ValidationException("The type '%s' is not a message",
            type.getName());
      }
      return (Class<? extends Message>) Class.forName(javaName);
    } catch (ClassNotFoundException e) {
      throw new ValidationException("Cannot resolve type '%s' as mapped to Java type '%s'",
          packageName + "." + messageName.replace('$',  '.'), javaName);
    }
  }

  /**
   * Resolves a proto enum name into a Java class.
   *
   * <p>The full algorithm for this requires all of proto file options, proto package name,
   * proto file name, and fully qualified enum name.
   *
   * @throws ValidationException if resolution fails.
   */
  @SuppressWarnings("unchecked")
  public static Class<? extends ProtocolMessageEnum> getEnumClass(FileOptions fileOptions,
      String packageName, String fileName, String enumName) {

    enumName = fixTypeName(packageName, enumName);
    String javaName = getJavaName(fileOptions, packageName, fileName, enumName);

    // Resolve the class.
    try {
      Class<?> type = Class.forName(javaName);
      //type.
      if (!ProtocolMessageEnum.class.isAssignableFrom(type)) {
        throw new ValidationException("The type '%s' is not a ProtocolMessageEnum",
            type.getName());
      }
      return (Class<? extends ProtocolMessageEnum>) Class.forName(javaName);
    } catch (ClassNotFoundException e) {
      throw new ValidationException("Cannot resolve type '%s' as mapped to Java type '%s'",
          packageName + "." + enumName.replace('$',  '.'), javaName);
    }
  }

  /**
   * Returns a default instance for the given message descriptor. First tries to get it from a
   * concrete class representing the message. If that fails, falls back to use
   * {@link DynamicMessage}, which works, but is significant slower on parsing and such.
   */
  public static Message getDefaultInstance(Descriptor message) {
    Class<? extends Message> type = getMessageClass(message.getFile().getOptions(),
        message.getFile().getPackage(),
        message.getFile().getName(),
        message.getFullName());
    if (type != null) {
      return getDefaultInstance(type);
    }
    return DynamicMessage.getDefaultInstance(message);
  }

  /**
   * Returns a Enum for the given enum value descriptor. It tries to get it from a
   * concrete class representing the Enum. Returns null if cannot find the Enum.
   */
  @SuppressWarnings("unchecked")
  public static ProtocolMessageEnum getEnum(EnumValueDescriptor enumValueDescriptor) {
    EnumDescriptor enumDescriptor = enumValueDescriptor.getType();
    Class<? extends ProtocolMessageEnum> type =
        getEnumClass(enumDescriptor.getFile().getOptions(), enumDescriptor.getFile().getPackage(),
            enumDescriptor.getFile().getName(), enumDescriptor.getFullName());
    if (type != null) {
      try {
        return invoke(ProtocolMessageEnum.class, type.getMethod("valueOf", int.class), null,
            enumValueDescriptor.getNumber());
      } catch (NoSuchMethodException e) {
        return null;
      } catch (SecurityException e) {
        throw Throwables.propagate(e);
      }
    }
    return null;
  }

  /**
   * Returns the default instance for the message type.
   */
  public static Message getDefaultInstance(Class<? extends Message> type) {
    return invoke(Message.class, type, "getDefaultInstance", null);
  }

  /**
   * Invokes a method with receiver and arguments, and casts the result to
   * a specified type.
   */
  public static <T> T invoke(Class<T> returnType, Method method, Object receiver,
      Object... args) {
    try {
      return returnType.cast(method.invoke(receiver, args));
    } catch (InvocationTargetException e) {
      throw Throwables.propagate(e.getCause());
    } catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Invokes a method with receiver and arguments, and casts the result to
   * a list of messages.
   */
  @SuppressWarnings("unchecked")
  public static List<Message> invoke(Method method, Object receiver, Object... args) {
    try {
      Object result = method.invoke(receiver, args);
      if (result instanceof List<?>) {
        return (List<Message>) result;
      } else {
        throw new ValidationException("Method '%s' failed to return list.", method.getName());
      }
    } catch (InvocationTargetException e) {
      throw Throwables.propagate(e.getCause());
    } catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Invokes a named method with receiver and arguments, and casts the result to
   * a specified type.
   */
  public static <T> T invoke(Class<T> returnType, Class<?> type, String name, Object receiver,
      Object... args) {
    try {
      return invoke(returnType, type.getMethod(name, getTypes(args)), receiver, args);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  /**
   * Returns a value of a field. If the field is a map, goes the extra mile to get the real
   * map via reflection.
   */
  public static Object getField(GeneratedMessage target, FieldDescriptor field) {
    if (!field.isMapField()) {
      return target.getField(field);
    }
    return invoke(Object.class, target.getClass(),
        "get" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, field.getName()),
        target);
  }

  private static Class<?>[] getTypes(Object[] args) {
    Class<?>[] result = new Class<?>[args.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = args[i].getClass();
    }
    return result;
  }

  /**
   * Removes the package name from the typeName and also replaces 'dot's with '$' signs.
   */
  private static String fixTypeName(String packageName, String typeName) {
    // Remove the package name prefix from the messageName
    if (typeName.startsWith(packageName + ".")) {
      typeName = typeName.substring(packageName.length() + 1);
    }

    // Replace any nesting from messages by '$', Java's convention for inner classes.
    typeName = typeName.replace('.', '$');
    return typeName;
  }

  /**
   * Resolves a proto element name into a Java class name.
   *
   * <p>The full algorithm for this requires all of proto file options, proto package name,
   * proto file name, and fully qualified proto element name.
   */
  private static String getJavaName(
      FileOptions fileOptions, String packageName, String fileName, String typeName) {
    // Compute the Java class name.
    String javaName = fileOptions.getJavaPackage();
    if (javaName.isEmpty()) {
      javaName = "com.google.protos." + packageName;
    }
    if (!fileOptions.getJavaMultipleFiles()) {
      if (fileOptions.hasJavaOuterClassname()) {
        javaName = javaName + "." + fileOptions.getJavaOuterClassname();
      } else {
        fileName = Files.getNameWithoutExtension(new File(fileName).getName());
        javaName =
            javaName + "." + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fileName);
      }
      javaName = javaName + "$" + typeName;
    } else {
      javaName = javaName + "." + typeName;
    }
    return javaName;
  }
}
