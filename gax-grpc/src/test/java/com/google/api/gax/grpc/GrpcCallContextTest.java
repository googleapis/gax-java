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
package com.google.api.gax.grpc;

import static org.junit.Assert.assertEquals;

import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import io.grpc.ManagedChannel;
import io.grpc.Metadata.Key;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

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
}
