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
package com.google.api.gax.core;

import static org.graalvm.nativeimage.ImageInfo.PROPERTY_IMAGE_CODE_KEY;
import static org.graalvm.nativeimage.ImageInfo.PROPERTY_IMAGE_CODE_VALUE_RUNTIME;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GaxPropertiesTest {

  @Test
  public void testGaxVersion() {
    String gaxVersion = GaxProperties.getGaxVersion();
    assertTrue(Pattern.compile("^\\d+\\.\\d+\\.\\d+").matcher(gaxVersion).find());
    String[] versionComponents = gaxVersion.split("\\.");
    // This test was added in version 1.56.0, so check that the major and minor numbers are greater
    // than that.
    int major = Integer.parseInt(versionComponents[0]);
    int minor = Integer.parseInt(versionComponents[1]);

    assertTrue(major >= 1);
    if (major == 1) {
      assertTrue(minor >= 56);
    }
  }

  @Test
  public void testGetVersion_nativeImage() {
    System.setProperty(PROPERTY_IMAGE_CODE_KEY, PROPERTY_IMAGE_CODE_VALUE_RUNTIME);
    String javaVersion = GaxProperties.getJavaVersion();
    assertTrue(javaVersion.endsWith("-graalvm"));
  }
}
