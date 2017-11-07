/*
 * Copyright 2017, Google LLC All rights reserved.
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

import com.google.api.core.BetaApi;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Provides meta-data properties stored in a properties file. */
@BetaApi
public class PropertiesProvider {
  private static final String DEFAULT_VERSION = "";
  private static final Logger logger = Logger.getLogger(PropertiesProvider.class.getName());

  /**
   * Utility method for retrieving the value of the given key from a property file in the package.
   *
   * @param loadedClass The class used to get the resource path
   * @param propertiesPath The relative file path to the property file
   * @param key Key string of the property
   */
  public static String loadProperty(Class<?> loadedClass, String propertiesPath, String key) {
    InputStream inputStream = null;
    try {
      inputStream = loadedClass.getResourceAsStream(propertiesPath);
      if (inputStream != null) {
        Properties properties = new Properties();
        properties.load(inputStream);
        return properties.getProperty(key);
      } else {
        logMissingProperties(loadedClass, propertiesPath);
        return null;
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Exception loading properties at \"" + propertiesPath + "\"", e);
      return null;
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, "Exception closing file at \"" + propertiesPath + "\"", e);
        }
      }
    }
  }

  /**
   * Utility method for retrieving the value of the given key from a property file in the package.
   *
   * @param properties The properties object to cache the properties data in
   * @param propertiesPath The relative file path to the property file
   * @param key Key string of the property
   */
  public static String loadProperty(Properties properties, String propertiesPath, String key) {
    InputStream inputStream = null;
    try {
      if (properties.isEmpty()) {
        inputStream = PropertiesProvider.class.getResourceAsStream(propertiesPath);
        if (inputStream != null) {
          properties.load(inputStream);
          inputStream.close();
        } else {
          logMissingProperties(PropertiesProvider.class, propertiesPath);
          return null;
        }
      }
      return properties.getProperty(key);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Exception loading properties at \"" + propertiesPath + "\"", e);
      return null;
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, "Exception closing file at \"" + propertiesPath + "\"", e);
        }
      }
    }
  }

  private static void logMissingProperties(Class<?> loadedClass, String propertyFilePath) {
    logger.log(
        Level.WARNING,
        "Warning: Failed to open properties resource at '%s' of the given class '%s'\n",
        new Object[] {propertyFilePath, loadedClass.getName()});
  }
}
