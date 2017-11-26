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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.testing.FakeStreamingApi.ServerStreamingStashCallable;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpoolingCallableTest {
  private ServerStreamingStashCallable<String, String> upstream;
  private SpoolingCallable<String, String> callable;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    upstream = new ServerStreamingStashCallable<>();
    callable = new SpoolingCallable<>(upstream);
  }

  @Test
  public void testHappyPath() throws InterruptedException, ExecutionException {
    ApiFuture<List<String>> result = callable.futureCall("request");
    ServerStreamingStashCallable<String, String>.Call call = upstream.getCall();

    assertThat(call.isAutoFlowControlEnabled()).isTrue();

    call.getObserver().onResponse("response1");
    call.getObserver().onResponse("response2");
    call.getObserver().onComplete();

    assertThat(result.get()).containsAllOf("response1", "response2").inOrder();
  }

  @Test
  public void testEarlyTermination() throws Exception {
    ApiFuture<List<String>> result = callable.futureCall("request");
    ServerStreamingStashCallable<String, String>.Call call = upstream.getCall();

    call.getObserver().onResponse("response1");
    result.cancel(true);
    call.getObserver().onResponse("response2");

    expectedException.expect(CancellationException.class);
    result.get();
  }

  @Test
  public void testNoResults() throws Exception {
    ApiFuture<List<String>> result = callable.futureCall("request");
    ServerStreamingStashCallable<String, String>.Call call = upstream.getCall();

    call.getObserver().onComplete();

    assertThat(result.get()).isEmpty();
  }
}
