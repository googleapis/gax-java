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
package com.google.api.gax.grpc;

import com.google.common.truth.Truth;
import io.grpc.CallOptions;
import io.grpc.Channel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class GrpcChannelCallContextEnhancerTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNullInnerEnhancer() {
    Channel channel = Mockito.mock(Channel.class);
    GrpcCallContext inputContext = GrpcCallContext.createDefault();
    GrpcChannelCallContextEnhancer enhancer = new GrpcChannelCallContextEnhancer(channel);

    GrpcCallContext expectedContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    Truth.assertThat(enhancer.enhance(null)).isEqualTo(expectedContext);
    Truth.assertThat(enhancer.enhance(inputContext)).isEqualTo(expectedContext);
  }

  @Test
  public void testWithInnerEnhancerNoChannel() {
    Channel channel = Mockito.mock(Channel.class);
    GrpcCallContext inputContext = GrpcCallContext.createDefault();
    GrpcChannelCallContextEnhancer enhancer = new GrpcChannelCallContextEnhancer(channel);

    GrpcCallContext expectedContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    Truth.assertThat(enhancer.enhance(null)).isEqualTo(expectedContext);
    Truth.assertThat(enhancer.enhance(inputContext)).isEqualTo(expectedContext);
  }

  @Test
  public void testWithInnerEnhancerHavingChannel() {
    Channel channel = Mockito.mock(Channel.class);
    Channel channel2 = Mockito.mock(Channel.class);
    GrpcCallContext inputContext = GrpcCallContext.of(channel, CallOptions.DEFAULT);
    GrpcChannelCallContextEnhancer enhancer = new GrpcChannelCallContextEnhancer(channel2);

    GrpcCallContext expectedContext = GrpcCallContext.of(channel2, CallOptions.DEFAULT);
    Truth.assertThat(enhancer.enhance(null)).isEqualTo(expectedContext);
    Truth.assertThat(enhancer.enhance(inputContext)).isEqualTo(inputContext);
  }

  @Test
  public void testNullChannel() {
    thrown.expect(NullPointerException.class);
    new GrpcChannelCallContextEnhancer(null);
  }
}
