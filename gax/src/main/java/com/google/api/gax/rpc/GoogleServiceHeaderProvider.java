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
package com.google.api.gax.rpc;

import com.google.api.client.util.Lists;
import com.google.api.core.BetaApi;
import com.google.api.gax.core.GaxProperties;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

public class GoogleServiceHeaderProvider implements HeaderProvider {
  private static final String DEFAULT_VERSION = "";

  public static String getServiceHeaderLineKey() {
    return "x-goog-api-client";
  }

  private final String clientLibName;
  private final String clientLibVersion;
  private final String generatorName;
  private final String generatorVersion;
  private final String googleCloudResourcePrefix;

  private GoogleServiceHeaderProvider(
      String clientLibName,
      String clientLibVersion,
      String generatorName,
      String generatorVersion,
      String googleCloudResourcePrefix) {
    this.clientLibName = clientLibName;
    this.clientLibVersion = clientLibVersion;
    this.generatorName = generatorName;
    this.generatorVersion = generatorVersion;
    this.googleCloudResourcePrefix = googleCloudResourcePrefix;
  }

  @Override
  public Map<String, String> getHeaders() {
    ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();

    headers.put(getServiceHeaderLineKey(), getServiceHeaderLineData());
    if (googleCloudResourcePrefix != null) {
      headers.put("google-cloud-resource-prefix", googleCloudResourcePrefix);
    }

    return headers.build();
  }

  private String getServiceHeaderLineData() {
    List<String> headerLineParts = Lists.newArrayList();

    headerLineParts.add("gl-java/" + getJavaVersion());
    if (clientLibName != null && clientLibVersion != null) {
      headerLineParts.add(clientLibName + "/" + clientLibVersion);
    }
    headerLineParts.add(generatorName + "/" + generatorVersion);
    headerLineParts.add("gax/" + GaxProperties.getGaxVersion());

    return Joiner.on(" ").join(headerLineParts);
  }

  private static String getJavaVersion() {
    String javaVersion = Runtime.class.getPackage().getImplementationVersion();
    if (javaVersion == null) {
      javaVersion = DEFAULT_VERSION;
    }
    return javaVersion;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {

    // Default names and versions of the service generator.
    private static final String DEFAULT_GENERATOR_NAME = "gapic";

    private String clientLibName;
    private String clientLibVersion;
    private String generatorName;
    private String generatorVersion;
    private String googleCloudResourcePrefix;

    private Builder() {
      generatorName = DEFAULT_GENERATOR_NAME;
      generatorVersion = DEFAULT_VERSION;
    }

    private Builder(GoogleServiceHeaderProvider provider) {
      this.clientLibName = provider.clientLibName;
      this.clientLibVersion = provider.clientLibVersion;
      this.generatorName = provider.generatorName;
      this.generatorVersion = provider.generatorVersion;
    }

    /** Sets the generator name and version for the GRPC custom header. */
    public Builder setGeneratorHeader(String name, String version) {
      this.generatorName = name;
      this.generatorVersion = version;
      return this;
    }

    /** Sets the client library name and version for the GRPC custom header. */
    public Builder setClientLibHeader(String name, String version) {
      this.clientLibName = name;
      this.clientLibVersion = version;
      return this;
    }

    /** Sets the google-cloud-resource-prefix header. */
    @BetaApi("This API and its semantics are likely to change in the future.")
    public Builder setGoogleCloudResourcePrefix(String resourcePrefix) {
      this.googleCloudResourcePrefix = resourcePrefix;
      return this;
    }

    /** The google-cloud-resource-prefix header provided previously. */
    @BetaApi("This API and its semantics are likely to change in the future.")
    public String getGoogleCloudResourcePrefixHeader() {
      return googleCloudResourcePrefix;
    }

    /** The client library name provided previously. */
    public String getClientLibName() {
      return clientLibName;
    }

    /** The client library version provided previously. */
    public String getClientLibVersion() {
      return clientLibVersion;
    }

    /** The generator name provided previously. */
    public String getGeneratorName() {
      return generatorName;
    }

    /** The generator version provided previously. */
    public String getGeneratorVersion() {
      return generatorVersion;
    }

    public GoogleServiceHeaderProvider build() {
      return new GoogleServiceHeaderProvider(
          clientLibName,
          clientLibVersion,
          generatorName,
          generatorVersion,
          googleCloudResourcePrefix);
    }
  }
}
