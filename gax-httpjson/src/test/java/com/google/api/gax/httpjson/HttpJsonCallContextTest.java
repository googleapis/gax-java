/*
 * Copyright 2017 Google LLC
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
package com.google.api.gax.httpjson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.api.gax.tracing.ApiTracer;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class HttpJsonCallContextTest {

  @Test
  public void testNullToSelfWrongType() {
    try {
      HttpJsonCallContext.createDefault().nullToSelf(FakeCallContext.createDefault());
      Assert.fail("HttpJsonCallContext should have thrown an exception");
    } catch (IllegalArgumentException expected) {
      Truth.assertThat(expected)
          .hasMessageThat()
          .contains("context must be an instance of HttpJsonCallContext");
    }
  }

  @Test
  public void testWithCredentials() {
    Credentials credentials = Mockito.mock(Credentials.class);
    HttpJsonCallContext emptyContext = HttpJsonCallContext.createDefault();
    assertNull(emptyContext.getCredentials());
    HttpJsonCallContext context = emptyContext.withCredentials(credentials);
    assertNotNull(context.getCredentials());
  }

  @Test
  public void testWithTransportChannel() {
    ManagedHttpJsonChannel channel = Mockito.mock(ManagedHttpJsonChannel.class);

    HttpJsonCallContext context =
        HttpJsonCallContext.createDefault()
            .withTransportChannel(
                HttpJsonTransportChannel.newBuilder().setManagedChannel(channel).build());
    Truth.assertThat(context.getChannel()).isSameInstanceAs(channel);
  }

  @Test
  public void testWithTransportChannelWrongType() {
    try {
      FakeChannel channel = new FakeChannel();
      HttpJsonCallContext.createDefault()
          .withTransportChannel(FakeTransportChannel.create(channel));
      Assert.fail("HttpJsonCallContext should have thrown an exception");
    } catch (IllegalArgumentException expected) {
      Truth.assertThat(expected).hasMessageThat().contains("Expected HttpJsonTransportChannel");
    }
  }

  @Test
  public void testMergeWrongType() {
    try {
      HttpJsonCallContext.createDefault().merge(FakeCallContext.createDefault());
      Assert.fail("HttpJsonCallContext should have thrown an exception");
    } catch (IllegalArgumentException expected) {
      Truth.assertThat(expected)
          .hasMessageThat()
          .contains("context must be an instance of HttpJsonCallContext");
    }
  }

  @Test
  public void testWithTimeout() {
    assertNull(HttpJsonCallContext.createDefault().withTimeout(null).getTimeout());
  }

  @Test
  public void testWithNegativeTimeout() {
    assertNull(
        HttpJsonCallContext.createDefault().withTimeout(Duration.ofSeconds(-1L)).getTimeout());
  }

  @Test
  public void testWithZeroTimeout() {
    assertNull(
        HttpJsonCallContext.createDefault().withTimeout(Duration.ofSeconds(0L)).getTimeout());
  }

  @Test
  public void testWithShorterTimeout() {
    HttpJsonCallContext ctxWithLongTimeout =
        HttpJsonCallContext.createDefault().withTimeout(Duration.ofSeconds(10));

    // Sanity check
    Truth.assertThat(ctxWithLongTimeout.getTimeout()).isEqualTo(Duration.ofSeconds(10));

    // Shorten the timeout and make sure it changed
    HttpJsonCallContext ctxWithShorterTimeout =
        ctxWithLongTimeout.withTimeout(Duration.ofSeconds(5));
    Truth.assertThat(ctxWithShorterTimeout.getTimeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  public void testWithLongerTimeout() {
    HttpJsonCallContext ctxWithShortTimeout =
        HttpJsonCallContext.createDefault().withTimeout(Duration.ofSeconds(5));

    // Sanity check
    Truth.assertThat(ctxWithShortTimeout.getTimeout()).isEqualTo(Duration.ofSeconds(5));

    // Try to extend the timeout and verify that it was ignored
    HttpJsonCallContext ctxWithUnchangedTimeout =
        ctxWithShortTimeout.withTimeout(Duration.ofSeconds(10));
    Truth.assertThat(ctxWithUnchangedTimeout.getTimeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  public void testMergeWithNullTimeout() {
    Duration timeout = Duration.ofSeconds(10);
    HttpJsonCallContext baseContext = HttpJsonCallContext.createDefault().withTimeout(timeout);

    HttpJsonCallContext defaultOverlay = HttpJsonCallContext.createDefault();
    Truth.assertThat(baseContext.merge(defaultOverlay).getTimeout()).isEqualTo(timeout);

    HttpJsonCallContext explicitNullOverlay = HttpJsonCallContext.createDefault().withTimeout(null);
    Truth.assertThat(baseContext.merge(explicitNullOverlay).getTimeout()).isEqualTo(timeout);
  }

  @Test
  public void testMergeWithTimeout() {
    Duration timeout = Duration.ofSeconds(19);
    HttpJsonCallContext ctx1 = HttpJsonCallContext.createDefault();
    HttpJsonCallContext ctx2 = HttpJsonCallContext.createDefault().withTimeout(timeout);

    Truth.assertThat(ctx1.merge(ctx2).getTimeout()).isEqualTo(timeout);
  }

  @Test
  public void testMergeWithTracer() {
    ApiTracer explicitTracer = Mockito.mock(ApiTracer.class);
    HttpJsonCallContext ctxWithExplicitTracer =
        HttpJsonCallContext.createDefault().withTracer(explicitTracer);

    HttpJsonCallContext ctxWithDefaultTracer = HttpJsonCallContext.createDefault();
    ApiTracer defaultTracer = ctxWithDefaultTracer.getTracer();

    // Explicit tracer overrides the default tracer.
    Truth.assertThat(ctxWithDefaultTracer.merge(ctxWithExplicitTracer).getTracer())
        .isSameInstanceAs(explicitTracer);

    // Default tracer does not override an explicit tracer.
    Truth.assertThat(ctxWithExplicitTracer.merge(ctxWithDefaultTracer).getTracer())
        .isSameInstanceAs(explicitTracer);

    // Default tracer does not override another default tracer.
    Truth.assertThat(ctxWithDefaultTracer.merge(HttpJsonCallContext.createDefault()).getTracer())
        .isSameInstanceAs(defaultTracer);
  }

  @Test
  public void testWithRetrySettings() {
    RetrySettings retrySettings = Mockito.mock(RetrySettings.class);
    HttpJsonCallContext emptyContext = HttpJsonCallContext.createDefault();
    assertNull(emptyContext.getRetrySettings());
    HttpJsonCallContext context = emptyContext.withRetrySettings(retrySettings);
    assertNotNull(context.getRetrySettings());
  }

  @Test
  public void testWithRetryableCodes() {
    Set<StatusCode.Code> codes = Collections.singleton(StatusCode.Code.UNAVAILABLE);
    HttpJsonCallContext emptyContext = HttpJsonCallContext.createDefault();
    assertNull(emptyContext.getRetryableCodes());
    HttpJsonCallContext context = emptyContext.withRetryableCodes(codes);
    assertNotNull(context.getRetryableCodes());
  }

  @Test
  public void testWithExtraHeaders() {
    Map<String, List<String>> headers = ImmutableMap.of("k", Arrays.asList("v"));
    ApiCallContext emptyContext = HttpJsonCallContext.createDefault();
    assertTrue(emptyContext.getExtraHeaders().isEmpty());
    ApiCallContext context = emptyContext.withExtraHeaders(headers);
    assertEquals(headers, context.getExtraHeaders());
  }

  @Test
  public void testWithOptions() {
    ApiCallContext emptyCallContext = HttpJsonCallContext.createDefault();
    ApiCallContext.Key<String> contextKey1 = ApiCallContext.Key.create("testKey1");
    ApiCallContext.Key<String> contextKey2 = ApiCallContext.Key.create("testKey2");
    String testContext1 = "test1";
    String testContext2 = "test2";
    String testContextOverwrite = "test1Overwrite";
    ApiCallContext context =
        emptyCallContext
            .withOption(contextKey1, testContext1)
            .withOption(contextKey2, testContext2);
    assertEquals(testContext1, context.getOption(contextKey1));
    assertEquals(testContext2, context.getOption(contextKey2));
    ApiCallContext newContext = context.withOption(contextKey1, testContextOverwrite);
    assertEquals(testContextOverwrite, newContext.getOption(contextKey1));
  }

  @Test
  public void testMergeOptions() {
    ApiCallContext emptyCallContext = HttpJsonCallContext.createDefault();
    ApiCallContext.Key<String> contextKey1 = ApiCallContext.Key.create("testKey1");
    ApiCallContext.Key<String> contextKey2 = ApiCallContext.Key.create("testKey2");
    ApiCallContext.Key<String> contextKey3 = ApiCallContext.Key.create("testKey3");
    String testContext1 = "test1";
    String testContext2 = "test2";
    String testContext3 = "test3";
    String testContextOverwrite = "test1Overwrite";
    ApiCallContext context1 =
        emptyCallContext
            .withOption(contextKey1, testContext1)
            .withOption(contextKey2, testContext2);
    ApiCallContext context2 =
        emptyCallContext
            .withOption(contextKey1, testContextOverwrite)
            .withOption(contextKey3, testContext3);
    ApiCallContext mergedContext = context1.merge(context2);
    assertEquals(testContextOverwrite, mergedContext.getOption(contextKey1));
    assertEquals(testContext2, mergedContext.getOption(contextKey2));
    assertEquals(testContext3, mergedContext.getOption(contextKey3));
  }
}
