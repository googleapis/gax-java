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

package com.google.api.gax.nativeimage.substitutions;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;
import java.util.function.BooleanSupplier;

/**
 * This file contains the GaxProperties substitution to correctly set the Java language string in
 * API call headers for Native Image users.
 */
@TargetClass(
    className = "com.google.api.gax.core.GaxProperties",
    onlyWith = GaxPropertiesSubstitutions.OnlyIfInClassPath.class)
final class GaxPropertiesSubstitutions {

  @Alias
  @RecomputeFieldValue(kind = Kind.FromAlias)
  private static String JAVA_VERSION = System.getProperty("java.version") + "-graalvm";

  private GaxPropertiesSubstitutions() {}

  static class OnlyIfInClassPath implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
      try {
        // Note: Set initialize = false to avoid initializing the class when looking it up.
        Class.forName(
            "com.google.api.gax.core.GaxProperties",
            false,
            Thread.currentThread().getContextClassLoader());
        return true;
      } catch (ClassNotFoundException e) {
        return false;
      }
    }
  }
}
