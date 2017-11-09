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

import com.google.common.truth.Truth;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApiClientHeaderProviderTest {

  private static final String X_GOOG_API_CLIENT = "x-goog-api-client";
  private static final String CLOUD_RESOURCE_PREFIX = "google-cloud-resource-prefix";

  @Test
  public void testServiceHeaderDefault() {
    List<String> extraHeaderLineData = Collections.singletonList("grpc/0.0.0");
    ApiClientHeaderProvider provider =
        ApiClientHeaderProvider.newBuilder()
            .setApiClientHeaderLineKey(X_GOOG_API_CLIENT)
            .addApiClientHeaderLineData(extraHeaderLineData)
            .build();
    String expectedHeaderPattern = "^gl-java/.* gapic/ gax/.* grpc/.*$";
    Truth.assertThat(
            Pattern.compile(expectedHeaderPattern)
                .matcher(provider.getHeaders().get(X_GOOG_API_CLIENT))
                .find())
        .isTrue();

    ApiClientHeaderProvider.Builder builder = provider.toBuilder();
    Truth.assertThat(builder.getApiClientHeaderLineKey()).isEqualTo(X_GOOG_API_CLIENT);
    Truth.assertThat(builder.getApiClientHeaderLineData()).containsExactly("grpc/0.0.0");
  }

  @Test
  public void testServiceHeaderGapicVersion() {
    List<String> extraHeaderLineData = Collections.singletonList("grpc/0.0.0");
    ApiClientHeaderProvider provider =
        ApiClientHeaderProvider.newBuilder()
            .setApiClientHeaderLineKey(X_GOOG_API_CLIENT)
            .addApiClientHeaderLineData(extraHeaderLineData)
            .setGeneratorHeader("gapic", "0.0.0")
            .build();
    String expectedHeaderPattern = "^gl-java/.* gapic/0\\.0\\.0 gax/.* grpc/.*$";
    Truth.assertThat(
            Pattern.compile(expectedHeaderPattern)
                .matcher(provider.getHeaders().get(X_GOOG_API_CLIENT))
                .find())
        .isTrue();

    ApiClientHeaderProvider.Builder builder = provider.toBuilder();
    Truth.assertThat(builder.getGeneratorName()).isEqualTo("gapic");
    Truth.assertThat(builder.getGeneratorVersion()).isEqualTo("0.0.0");
  }

  @Test
  public void testServiceHeaderCustomClient() {
    List<String> extraHeaderLineData = Collections.singletonList("grpc/0.0.0");
    ApiClientHeaderProvider provider =
        ApiClientHeaderProvider.newBuilder()
            .setApiClientHeaderLineKey(X_GOOG_API_CLIENT)
            .addApiClientHeaderLineData(extraHeaderLineData)
            .setClientLibHeader("gccl", "0.0.0")
            .build();
    String expectedHeaderPattern = "^gl-java/.* gccl/0\\.0\\.0 gapic/ gax/.* grpc/.*$";
    Truth.assertThat(
            Pattern.compile(expectedHeaderPattern)
                .matcher(provider.getHeaders().get(X_GOOG_API_CLIENT))
                .find())
        .isTrue();

    ApiClientHeaderProvider.Builder builder = provider.toBuilder();
    Truth.assertThat(builder.getClientLibName()).isEqualTo("gccl");
    Truth.assertThat(builder.getClientLibVersion()).isEqualTo("0.0.0");
  }

  @Test
  public void testCloudResourcePrefixHeader() {
    ApiClientHeaderProvider provider =
        ApiClientHeaderProvider.newBuilder()
            .setApiClientHeaderLineKey(X_GOOG_API_CLIENT)
            .setGoogleCloudResourcePrefix("test-prefix")
            .build();
    Truth.assertThat("test-prefix").isEqualTo(provider.getHeaders().get(CLOUD_RESOURCE_PREFIX));

    ApiClientHeaderProvider.Builder builder = provider.toBuilder();
    Truth.assertThat(builder.getGoogleCloudResourcePrefixHeader()).isEqualTo("test-prefix");
  }
}
