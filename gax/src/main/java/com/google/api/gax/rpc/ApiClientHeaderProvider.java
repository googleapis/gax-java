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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of HeaderProvider that provides headers describing the API client library making
 * API calls.
 */
@BetaApi
public class ApiClientHeaderProvider implements HeaderProvider {
  private static final String DEFAULT_VERSION = "";

  private final String apiClientHeaderLineKey;
  private final String clientLibName;
  private final String clientLibVersion;
  private final String generatorName;
  private final String generatorVersion;
  private final List<String> apiClientHeaderLineData;
  private final String googleCloudResourcePrefix;
  private final Map<String, String> headers;

  private ApiClientHeaderProvider(
      String apiClientHeaderLineKey,
      String clientLibName,
      String clientLibVersion,
      String generatorName,
      String generatorVersion,
      List<String> apiClientHeaderLineData,
      String googleCloudResourcePrefix) {
    this.apiClientHeaderLineKey = apiClientHeaderLineKey;
    this.clientLibName = clientLibName;
    this.clientLibVersion = clientLibVersion;
    this.generatorName = generatorName;
    this.generatorVersion = generatorVersion;
    this.apiClientHeaderLineData = apiClientHeaderLineData;
    this.googleCloudResourcePrefix = googleCloudResourcePrefix;
    this.headers = generateHeaders();
  }

  @Override
  public Map<String, String> getHeaders() {
    return headers;
  }

  private Map<String, String> generateHeaders() {
    ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();

    headers.put(apiClientHeaderLineKey, getApiClientHeaderLineData());
    if (googleCloudResourcePrefix != null) {
      headers.put("google-cloud-resource-prefix", googleCloudResourcePrefix);
    }

    return headers.build();
  }

  private String getApiClientHeaderLineData() {
    List<String> headerLineParts = Lists.newArrayList();

    headerLineParts.add("gl-java/" + getJavaVersion());
    if (clientLibName != null && clientLibVersion != null) {
      headerLineParts.add(clientLibName + "/" + clientLibVersion);
    }
    headerLineParts.add(generatorName + "/" + generatorVersion);
    headerLineParts.add("gax/" + GaxProperties.getGaxVersion());
    headerLineParts.addAll(apiClientHeaderLineData);

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

    private String apiClientHeaderLineKey;
    private String clientLibName;
    private String clientLibVersion;
    private String generatorName;
    private String generatorVersion;
    private List<String> apiClientHeaderLineData = new ArrayList<>();
    private String googleCloudResourcePrefix;

    private Builder() {
      generatorName = DEFAULT_GENERATOR_NAME;
      generatorVersion = DEFAULT_VERSION;
    }

    private Builder(ApiClientHeaderProvider provider) {
      this.apiClientHeaderLineKey = provider.apiClientHeaderLineKey;
      this.clientLibName = provider.clientLibName;
      this.clientLibVersion = provider.clientLibVersion;
      this.generatorName = provider.generatorName;
      this.generatorVersion = provider.generatorVersion;
      this.apiClientHeaderLineData = provider.apiClientHeaderLineData;
      this.googleCloudResourcePrefix = provider.googleCloudResourcePrefix;
    }

    public Builder setApiClientHeaderLineKey(String key) {
      this.apiClientHeaderLineKey = key;
      return this;
    }

    /** Sets the client library name and version for the custom header. */
    public Builder setClientLibHeader(String name, String version) {
      this.clientLibName = name;
      this.clientLibVersion = version;
      return this;
    }

    /** Sets the generator name and version for the custom header. */
    public Builder setGeneratorHeader(String name, String version) {
      this.generatorName = name;
      this.generatorVersion = version;
      return this;
    }

    /**
     * Adds a new piece of data to the end of the x-goog-api-client header key. Needs to be in the
     * format "key/value" (without quotes).
     */
    public Builder addApiClientHeaderLineData(List<String> data) {
      for (String datum : data) {
        Preconditions.checkArgument(datum.contains("/"));
      }
      apiClientHeaderLineData.addAll(data);
      return this;
    }

    /** Sets the google-cloud-resource-prefix header. */
    @BetaApi("This API and its semantics are likely to change in the future.")
    public Builder setGoogleCloudResourcePrefix(String resourcePrefix) {
      this.googleCloudResourcePrefix = resourcePrefix;
      return this;
    }

    public String getApiClientHeaderLineKey() {
      return this.apiClientHeaderLineKey;
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

    public List<String> getApiClientHeaderLineData() {
      return apiClientHeaderLineData;
    }

    /** The google-cloud-resource-prefix header provided previously. */
    @BetaApi("This API and its semantics are likely to change in the future.")
    public String getGoogleCloudResourcePrefixHeader() {
      return googleCloudResourcePrefix;
    }

    public ApiClientHeaderProvider build() {
      return new ApiClientHeaderProvider(
          apiClientHeaderLineKey,
          clientLibName,
          clientLibVersion,
          generatorName,
          generatorVersion,
          apiClientHeaderLineData,
          googleCloudResourcePrefix);
    }
  }
}
