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
package com.google.api.gax.rpc;

import com.google.api.core.BetaApi;
import com.google.api.gax.core.GaxProperties;
import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.Map;

/**
 * Implementation of HeaderProvider that provides headers describing the API client library making
 * API calls.
 */
@BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
public class ApiClientHeaderProvider implements HeaderProvider, Serializable {
  private static final long serialVersionUID = -8876627296793342119L;

  private final Map<String, String> headers;

  protected ApiClientHeaderProvider(Builder builder) {
    ImmutableMap.Builder<String, String> headersBuilder = ImmutableMap.builder();

    if (builder.getApiClientHeaderKey() != null) {
      StringBuilder apiClientHeaderValue = new StringBuilder();
      // Order of tokens matters!!!
      appendToken(apiClientHeaderValue, builder.getJvmToken());
      appendToken(apiClientHeaderValue, builder.getClientLibToken());
      appendToken(apiClientHeaderValue, builder.getGeneratedLibToken());
      appendToken(apiClientHeaderValue, builder.getGeneratedRuntimeToken());
      appendToken(apiClientHeaderValue, builder.getTransportToken());
      if (apiClientHeaderValue.length() > 0) {
        headersBuilder.put(builder.getApiClientHeaderKey(), apiClientHeaderValue.toString());
      }
    }

    if (builder.getResourceHeaderKey() != null && builder.getResourceToken() != null) {
      headersBuilder.put(builder.getResourceHeaderKey(), builder.getResourceToken());
    }

    this.headers = headersBuilder.build();
  }

  @Override
  public Map<String, String> getHeaders() {
    return headers;
  }

  protected static void appendToken(StringBuilder sb, String token) {
    if (token != null) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(token);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static String getDefaultApiClientHeaderKey() {
    return "x-goog-api-client";
  }

  public static String getDefaultResourceHeaderKey() {
    return "google-cloud-resource-prefix";
  }

  public static class Builder {
    private String apiClientHeaderKey;
    private String jvmToken;
    private String clientLibToken;
    private String generatedLibToken;
    private String generatedRuntimeToken;
    private String transportToken;

    private String resourceHeaderKey;
    private String resourceToken;

    protected Builder() {
      // Initialize with default values
      apiClientHeaderKey = getDefaultApiClientHeaderKey();
      setJvmToken(GaxProperties.getJavaVersion());
      clientLibToken = null;
      generatedLibToken = null;
      setClientRuntimeToken(GaxProperties.getGaxVersion());
      transportToken = null;

      resourceHeaderKey = getDefaultResourceHeaderKey();
      resourceToken = null;
    }

    public String getApiClientHeaderKey() {
      return apiClientHeaderKey;
    }

    public Builder setApiClientHeaderKey(String apiClientHeaderKey) {
      this.apiClientHeaderKey = apiClientHeaderKey;
      return this;
    }

    public String getJvmToken() {
      return jvmToken;
    }

    public Builder setJvmToken(String version) {
      this.jvmToken = constructToken("gl-java", version);
      return this;
    }

    public String getClientLibToken() {
      return clientLibToken;
    }

    public Builder setClientLibToken(String name, String version) {
      this.clientLibToken = constructToken(name, version);
      return this;
    }

    public String getGeneratedLibToken() {
      return generatedLibToken;
    }

    public Builder setGeneratedLibToken(String name, String version) {
      this.generatedLibToken = constructToken(name, version);
      return this;
    }

    public String getGeneratedRuntimeToken() {
      return generatedRuntimeToken;
    }

    public Builder setClientRuntimeToken(String version) {
      this.generatedRuntimeToken = constructToken("gax", version);
      return this;
    }

    public String getTransportToken() {
      return transportToken;
    }

    public Builder setTransportToken(String name, String version) {
      this.transportToken = constructToken(name, version);
      return this;
    }

    public String getResourceHeaderKey() {
      return resourceHeaderKey;
    }

    public Builder setResourceHeaderKey(String resourceHeaderKey) {
      this.resourceHeaderKey = resourceHeaderKey;
      return this;
    }

    public String getResourceToken() {
      return resourceToken;
    }

    public Builder setResourceToken(String resourceToken) {
      this.resourceToken = resourceToken;
      return this;
    }

    private String constructToken(String name, String version) {
      if (version == null) {
        return null;
      }
      if (name == null) {
        throw new IllegalArgumentException("Token name cannot be null");
      }
      return name + '/' + version;
    }

    public ApiClientHeaderProvider build() {
      return new ApiClientHeaderProvider(this);
    }
  }
}
