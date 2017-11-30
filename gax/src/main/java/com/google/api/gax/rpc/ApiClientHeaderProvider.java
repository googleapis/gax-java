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
import java.util.Map;

/**
 * Implementation of HeaderProvider that provides headers describing the API client library making
 * API calls.
 */
@BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
public class ApiClientHeaderProvider implements HeaderProvider {
  private final Map<String, String> headers;

  private ApiClientHeaderProvider(Map<String, String> headers) {
    this.headers = headers;
  }

  @Override
  public Map<String, String> getHeaders() {
    return headers;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    private String apiClientHeaderKey;
    private String jvmToken;
    private String clientLibToken;
    private String generatedLibToken;
    private String generatedRuntimeToken;
    private String transportToken;

    private String resourceHeaderKey;
    private String resourceToken;

    private Builder() {
      // Initialize with default values
      apiClientHeaderKey = "x-goog-api-client";
      jvmToken = "gl-java/" + GaxProperties.getJavaVersion();
      clientLibToken = null;
      generatedLibToken = null;
      generatedRuntimeToken = "gax/" + GaxProperties.getGaxVersion();
      transportToken = null;

      resourceHeaderKey = "google-cloud-resource-prefix";
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

    public Builder setJvmToken(String jvmToken) {
      this.jvmToken = jvmToken;
      return this;
    }

    public String getClientLibToken() {
      return clientLibToken;
    }

    public Builder setClientLibToken(String clientLibToken) {
      this.clientLibToken = clientLibToken;
      return this;
    }

    public String getGeneratedLibToken() {
      return generatedLibToken;
    }

    public Builder setGeneratedLibToken(String generatedLibToken) {
      this.generatedLibToken = generatedLibToken;
      return this;
    }

    public String getGeneratedRuntimeToken() {
      return generatedRuntimeToken;
    }

    public Builder setGeneratedRuntimeToken(String generatedRuntimeToken) {
      this.generatedRuntimeToken = generatedRuntimeToken;
      return this;
    }

    public String getTransportToken() {
      return transportToken;
    }

    public Builder setTransportToken(String transportToken) {
      this.transportToken = transportToken;
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

    public ApiClientHeaderProvider build() {
      ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();

      StringBuilder apiClientHeaderValue = new StringBuilder();
      // Order of tokens matters!!!
      appendToken(apiClientHeaderValue, getJvmToken());
      appendToken(apiClientHeaderValue, getClientLibToken());
      appendToken(apiClientHeaderValue, getGeneratedLibToken());
      appendToken(apiClientHeaderValue, getGeneratedRuntimeToken());
      appendToken(apiClientHeaderValue, getTransportToken());

      if (apiClientHeaderKey != null && apiClientHeaderValue.length() > 0) {
        headers.put(apiClientHeaderKey, apiClientHeaderValue.toString());
      }

      if (resourceHeaderKey != null && resourceToken != null) {
        headers.put(resourceHeaderKey, resourceToken);
      }

      return new ApiClientHeaderProvider(headers.build());
    }

    private void appendToken(StringBuilder sb, String token) {
      if (token != null) {
        if (sb.length() > 0) {
          sb.append(' ');
        }
        sb.append(token);
      }
    }
  }
}
