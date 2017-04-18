/*
 * Copyright 2017, Google Inc. All rights reserved.
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
package com.google.api.gax.core;

import com.google.api.core.BetaApi;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides meta-data properties stored in a properties file.
 */
@BetaApi
public class PropertiesProvider {

  private static final Properties gaxProperties = new Properties();
  private static final String GAX_PROPERTY_FILE = "/com/google/api/gax/gax.properties";
  private static final String DEFAULT_VERSION = "";

  /**
   * Returns the current version of GAX
   */
  public static String getGaxVersion() {
    String gaxVersion = loadGaxProperty("version");
    return gaxVersion != null ? gaxVersion : DEFAULT_VERSION;
  }

  /**
   * Returns the current version of gRPC
   */
  public static String getGrpcVersion() {
    String grpcVersion = loadGaxProperty("grpc_version");
    return grpcVersion != null ? grpcVersion : DEFAULT_VERSION;
  }

  /**
   * Utility method of retrieving the property value of the given key from a property file in the
   * package.
   *
   * @param loadedClass The class used to get the resource path
   * @param propertyFilePath The relative file path to the property file
   * @param key Key string of the property
   */
  public static String loadProperty(Class<?> loadedClass, String propertyFilePath, String key) {
    try {
      InputStream inputStream = loadedClass.getResourceAsStream(propertyFilePath);
      if (inputStream != null) {
        Properties properties = new Properties();
        properties.load(inputStream);
        return properties.getProperty(key);
      } else {
        printMissingProperties(loadedClass, propertyFilePath);
      }
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
    return null;
  }

  private static String loadGaxProperty(String key) {
    try {
      if (gaxProperties.isEmpty()) {
        InputStream inputStream = PropertiesProvider.class.getResourceAsStream(GAX_PROPERTY_FILE);
        if (inputStream != null) {
          gaxProperties.load(inputStream);
        } else {
          printMissingProperties(PropertiesProvider.class, GAX_PROPERTY_FILE);
          return null;
        }
      }
      return gaxProperties.getProperty(key);
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
    return null;
  }

  private static void printMissingProperties(Class<?> loadedClass, String propertyFilePath) {
    System.err.format(
        "Warning: Failed to open properties resource at '%s' of the given class '%s'\n",
        propertyFilePath,
        loadedClass.getName());
  }
}
