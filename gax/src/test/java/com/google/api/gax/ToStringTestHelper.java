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
package com.google.api.gax;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Helper class to verify {@code toString()} implementations. */
public class ToStringTestHelper {

  /**
   * Verifies {@code toString()} implementation. Checks the following assertions:
   *
   * <ul>
   *   <li>toString() does not return null.
   *   <li>toString() returns the same value if invoked twice.
   *   <li>Returned value starts with the simple class name followed by '{'.
   *   <li>Returned value includes all the given fields.
   * </ul>
   *
   * @param instance object under test
   * @param fields field names to be mentioned in {@code toString()}
   */
  public static void checkToString(Object instance, String... fields) {
    String toString = instance.toString();
    assertThat(toString).isNotNull();
    assertThat(toString).isEqualTo(instance.toString());
    Class clazz = instance.getClass();
    assertThat(toString).startsWith(clazz.getSimpleName() + '{');
    for (int i = 0; i < fields.length; i++) {
      String prefix = i == 0 ? "{" : ", ";
      assertThat(toString).contains(prefix + fields[i] + "=");
    }
  }

  private static final List<String> defaultExcludeList =
      Arrays.asList("clone", "toString", "hashCode", "getClass", "newBuilder", "toBuilder");

  /**
   * Returns names of fields/methods of the given class which could be potentially used for
   * toString() implementation. This method helps to develop tests for toString() failing when a new
   * member is added to a class, but toString() is not updated accordingly.
   *
   * @param clazz class to search in
   * @param exclude names to not include in the returned list
   * @return field/method names declared in the class or its superclasses the candidates to be used
   *     in the toString()
   */
  public static String[] getMembers(Class clazz, String... exclude) {
    List<String> excludeList = new ArrayList<>(defaultExcludeList);
    excludeList.addAll(Arrays.asList(exclude));
    Set<String> members = new LinkedHashSet<>();
    while (clazz != null) {
      for (Field f : clazz.getDeclaredFields()) {
        int modifiers = f.getModifiers();
        if ((Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
            && Modifier.isFinal(modifiers)
            && !Modifier.isStatic(modifiers)
            && !excludeList.contains(f.getName())) {
          members.add(f.getName());
        }
      }
      for (Method m : clazz.getDeclaredMethods()) {
        int modifiers = m.getModifiers();
        if ((Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
            && !m.getReturnType().equals(Void.TYPE)
            && m.getParameterTypes().length == 0
            && !excludeList.contains(m.getName())
            && !members.contains(m.getName())) {
          members.add(m.getName());
        }
      }
      clazz = clazz.getSuperclass();
    }
    return members.toArray(new String[0]);
  }
}
