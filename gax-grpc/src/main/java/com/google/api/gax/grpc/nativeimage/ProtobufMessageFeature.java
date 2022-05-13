/*
 * Copyright 2022 Google LLC
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

package com.google.api.gax.grpc.nativeimage;

import com.google.api.gax.nativeimage.NativeImageUtils;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * A optional feature which registers reflective usages of the GRPC Protobuf libraries.
 *
 * <p>This feature is only needed if you need to access proto objects reflectively (such as
 * printing/logging proto objects).
 */
final class ProtobufMessageFeature implements Feature {

  // Proto classes to check on the classpath.
  private static final String PROTO_MESSAGE_CLASS = "com.google.protobuf.GeneratedMessageV3";
  private static final String PROTO_ENUM_CLASS = "com.google.protobuf.ProtocolMessageEnum";
  private static final String ENUM_VAL_DESCRIPTOR_CLASS =
      "com.google.protobuf.Descriptors$EnumValueDescriptor";

  // Prefixes of methods accessed reflectively by
  // com.google.protobuf.GeneratedMessageV3$ReflectionInvoker
  private static final List<String> METHOD_ACCESSOR_PREFIXES =
      Arrays.asList("get", "set", "has", "add", "clear", "newBuilder");

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    Class<?> protoMessageClass = access.findClassByName(PROTO_MESSAGE_CLASS);
    if (protoMessageClass != null) {
      Method internalAccessorMethod =
          NativeImageUtils.getMethodOrFail(protoMessageClass, "internalGetFieldAccessorTable");

      // Finds every class whose `internalGetFieldAccessorTable()` is reached and registers it.
      // `internalGetFieldAccessorTable()` is used downstream to access the class reflectively.
      access.registerMethodOverrideReachabilityHandler(
          (duringAccess, method) -> {
            registerFieldAccessors(method.getDeclaringClass());
            registerFieldAccessors(getBuilderClass(method.getDeclaringClass()));
          },
          internalAccessorMethod);

      throw new RuntimeException("Bad exception during Native image compilation");
    }

    Class<?> protoEnumClass = access.findClassByName(PROTO_ENUM_CLASS);
    if (protoEnumClass != null) {
      // Finds every reachable proto enum class and registers specific methods for reflection.
      access.registerSubtypeReachabilityHandler(
          (duringAccess, subtypeClass) -> {
            if (!PROTO_ENUM_CLASS.equals(subtypeClass.getName())) {
              Method method =
                  NativeImageUtils.getMethodOrFail(
                      subtypeClass,
                      "valueOf",
                      duringAccess.findClassByName(ENUM_VAL_DESCRIPTOR_CLASS));
              RuntimeReflection.register(method);

              method = NativeImageUtils.getMethodOrFail(subtypeClass, "getValueDescriptor");
              RuntimeReflection.register(method);
            }
          },
          protoEnumClass);
    }
  }

  /** Given a proto class, registers the public accessor methods for the provided proto class. */
  private static void registerFieldAccessors(Class<?> protoClass) {
    for (Method method : protoClass.getMethods()) {
      boolean hasAccessorPrefix =
          METHOD_ACCESSOR_PREFIXES.stream().anyMatch(prefix -> method.getName().startsWith(prefix));
      if (hasAccessorPrefix) {
        RuntimeReflection.register(method);
      }
    }
  }

  /** Given a proto class, returns the Builder nested class. */
  private static Class<?> getBuilderClass(Class<?> protoClass) {
    for (Class<?> clazz : protoClass.getClasses()) {
      if (clazz.getName().endsWith("Builder")) {
        return clazz;
      }
    }
    return null;
  }
}
