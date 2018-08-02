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
package com.google.api.gax.grpc;

import static org.junit.Assert.assertEquals;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Metadata.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class GrpcCallContextTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullToSelfWrongType() {
    thrown.expect(IllegalArgumentException.class);
    GrpcCallContext.createDefault().nullToSelf(FakeCallContext.createDefault());
  }

  @Test
  public void testWithCredentials() {
    Credentials credentials = Mockito.mock(Credentials.class);
    GrpcCallContext emptyContext = GrpcCallContext.createDefault();
    Truth.assertThat(emptyContext.getCallOptions().getCredentials()).isNull();
    GrpcCallContext context = emptyContext.withCredentials(credentials);
    Truth.assertThat(context.getCallOptions().getCredentials()).isNotNull();
  }

  @Test
  public void testWithTransportChannel() {
    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    GrpcCallContext context =
        GrpcCallContext.createDefault().withTransportChannel(GrpcTransportChannel.create(channel));
    Truth.assertThat(context.getChannel()).isSameAs(channel);
  }

  @Test
  public void testWithTransportChannelWrongType() {
    thrown.expect(IllegalArgumentException.class);
    FakeChannel channel = new FakeChannel();
    GrpcCallContext.createDefault().withTransportChannel(FakeTransportChannel.create(channel));
  }

  @Test
  public void testMergeWrongType() {
    thrown.expect(IllegalArgumentException.class);
    GrpcCallContext.createDefault().merge(FakeCallContext.createDefault());
  }

  @Test
  public void testWithRequestParamsDynamicHeaderOption() {
    String encodedRequestParams = "param1=value&param2.param3=value23";
    GrpcCallContext context =
        GrpcCallContext.createDefault().withRequestParamsDynamicHeaderOption(encodedRequestParams);

    Map<Key<String>, String> headers =
        CallOptionsUtil.getDynamicHeadersOption(context.getCallOptions());

    assertEquals(
        ImmutableMap.of(CallOptionsUtil.REQUEST_PARAMS_HEADER_KEY, encodedRequestParams), headers);
  }

  @Test
  public void testWithTimeout() {
    Truth.assertThat(GrpcCallContext.createDefault().withTimeout(null).getDeadline()).isNull();
  }

  @Test
  public void testWithNegativeTimeout() {
    thrown.expect(DeadlineExceededException.class);
    GrpcCallContext.createDefault().withTimeout(Duration.ofSeconds(-1L));
  }

  @Test
  public void testWithZeroTimeout() {
    thrown.expect(DeadlineExceededException.class);
    GrpcCallContext.createDefault().withTimeout(Duration.ofSeconds(0L));
  }

  @Test
  public void testWithStreamingWaitTimeout() {
    Duration timeout = Duration.ofSeconds(15);
    GrpcCallContext context = GrpcCallContext.createDefault().withStreamWaitTimeout(timeout);
    Truth.assertThat(context.getStreamWaitTimeout()).isEqualTo(timeout);
  }

  @Test
  public void testMergeWithNullStreamingWaitTimeout() {
    Duration timeout = Duration.ofSeconds(10);
    GrpcCallContext baseContext = GrpcCallContext.createDefault().withStreamWaitTimeout(timeout);

    GrpcCallContext defaultOverlay = GrpcCallContext.createDefault();
    Truth.assertThat(baseContext.merge(defaultOverlay).getStreamWaitTimeout()).isEqualTo(timeout);

    GrpcCallContext explicitNullOverlay =
        GrpcCallContext.createDefault().withStreamWaitTimeout(null);
    Truth.assertThat(baseContext.merge(explicitNullOverlay).getStreamWaitTimeout())
        .isEqualTo(timeout);
  }

  @Test
  public void testWithZeroStreamingWaitTimeout() {
    Duration timeout = Duration.ZERO;
    Truth.assertThat(
            GrpcCallContext.createDefault().withStreamWaitTimeout(timeout).getStreamWaitTimeout())
        .isEqualTo(timeout);
  }

  @Test
  public void testMergeWithStreamingWaitTimeout() {
    Duration timeout = Duration.ofSeconds(19);
    GrpcCallContext ctx1 = GrpcCallContext.createDefault();
    GrpcCallContext ctx2 = GrpcCallContext.createDefault().withStreamWaitTimeout(timeout);

    Truth.assertThat(ctx1.merge(ctx2).getStreamWaitTimeout()).isEqualTo(timeout);
  }

  @Test
  public void testWithStreamingIdleTimeout() {
    Duration timeout = Duration.ofSeconds(15);
    GrpcCallContext context = GrpcCallContext.createDefault().withStreamIdleTimeout(timeout);
    Truth.assertThat(context.getStreamIdleTimeout()).isEqualTo(timeout);
  }

  @Test
  public void testMergeWithNullStreamingIdleTimeout() {
    Duration timeout = Duration.ofSeconds(10);
    GrpcCallContext baseContext = GrpcCallContext.createDefault().withStreamIdleTimeout(timeout);

    GrpcCallContext defaultOverlay = GrpcCallContext.createDefault();
    Truth.assertThat(baseContext.merge(defaultOverlay).getStreamIdleTimeout()).isEqualTo(timeout);

    GrpcCallContext explicitNullOverlay =
        GrpcCallContext.createDefault().withStreamIdleTimeout(null);
    Truth.assertThat(baseContext.merge(explicitNullOverlay).getStreamIdleTimeout())
        .isEqualTo(timeout);
  }

  @Test
  public void testWithZeroStreamingIdleTimeout() {
    Duration timeout = Duration.ZERO;
    Truth.assertThat(
            GrpcCallContext.createDefault().withStreamIdleTimeout(timeout).getStreamIdleTimeout())
        .isEqualTo(timeout);
  }

  @Test
  public void testMergeWithStreamingIdleTimeout() {
    Duration timeout = Duration.ofSeconds(19);
    GrpcCallContext ctx1 = GrpcCallContext.createDefault();
    GrpcCallContext ctx2 = GrpcCallContext.createDefault().withStreamIdleTimeout(timeout);

    Truth.assertThat(ctx1.merge(ctx2).getStreamIdleTimeout()).isEqualTo(timeout);
  }

  @Test
  public void testMergeWithCustomCallOptions() {
    CallOptions.Key<String> key = CallOptions.Key.createWithDefault("somekey", "somedefault");
    GrpcCallContext ctx1 = GrpcCallContext.createDefault();
    GrpcCallContext ctx2 =
        GrpcCallContext.createDefault()
            .withCallOptions(CallOptions.DEFAULT.withOption(key, "somevalue"));

    GrpcCallContext merged = (GrpcCallContext) ctx1.merge(ctx2);
    Truth.assertThat(merged.getCallOptions().getOption(key))
        .isNotEqualTo(ctx1.getCallOptions().getOption(key));
    Truth.assertThat(merged.getCallOptions().getOption(key))
        .isEqualTo(ctx2.getCallOptions().getOption(key));
  }

  @Test
  public void testWithExtraHeaders() {
    Map<String, List<String>> extraHeaders =
        createTestExtraHeaders("key1", "value1", "key1", "value2");
    GrpcCallContext ctx = GrpcCallContext.createDefault().withExtraHeaders(extraHeaders);
    Map<String, List<String>> moreExtraHeaders =
        createTestExtraHeaders("key1", "value2", "key2", "value2");
    ctx = ctx.withExtraHeaders(moreExtraHeaders);
    Map<String, List<String>> gotExtraHeaders = ctx.getExtraHeaders();
    Map<String, List<String>> expectedExtraHeaders =
        createTestExtraHeaders(
            "key1", "value1", "key1", "value2", "key1", "value2", "key2", "value2");
    Truth.assertThat(gotExtraHeaders).containsExactlyEntriesIn(expectedExtraHeaders);
  }

  @Test
  public void testMergeWithExtraHeaders() {
    Map<String, List<String>> extraHeaders1 =
        createTestExtraHeaders("key1", "value1", "key1", "value2");
    GrpcCallContext ctx1 = GrpcCallContext.createDefault().withExtraHeaders(extraHeaders1);
    Map<String, List<String>> extraHeaders2 =
        createTestExtraHeaders("key1", "value2", "key2", "value2");
    GrpcCallContext ctx2 = GrpcCallContext.createDefault().withExtraHeaders(extraHeaders2);
    ApiCallContext mergedApiCallContext = ctx1.merge(ctx2);
    Truth.assertThat(mergedApiCallContext).isInstanceOf(GrpcCallContext.class);
    GrpcCallContext mergedGrpcCallContext = (GrpcCallContext) mergedApiCallContext;
    Map<String, List<String>> gotExtraHeaders = mergedGrpcCallContext.getExtraHeaders();
    Map<String, List<String>> expectedExtraHeaders =
        createTestExtraHeaders(
            "key1", "value1", "key1", "value2", "key1", "value2", "key2", "value2");
    Truth.assertThat(gotExtraHeaders).containsExactlyEntriesIn(expectedExtraHeaders);
  }

  private static Map<String, List<String>> createTestExtraHeaders(String... keyValues) {
    Map<String, List<String>> extraHeaders = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      String key = keyValues[i];
      String value = keyValues[i + 1];
      if (!extraHeaders.containsKey(key)) {
        extraHeaders.put(key, new ArrayList<String>());
      }
      extraHeaders.get(key).add(value);
    }
    return extraHeaders;
  }
}
