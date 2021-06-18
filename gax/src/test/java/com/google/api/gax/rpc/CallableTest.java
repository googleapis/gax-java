/*
 * Copyright 2021 Google LLC
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.testing.FakeCallContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.threeten.bp.Duration;

public class CallableTest {

  @Rule
  public final MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock private UnaryCallable<String, String> innerCallable;
  private SettableApiFuture<String> innerResult;

  @Mock private ServerStreamingCallable<Object, Object> innerServerStreamingCallable;

  private RetrySettings retrySettings =
      RetrySettings.newBuilder()
          .setInitialRpcTimeout(Duration.ofMillis(5L))
          .setMaxRpcTimeout(Duration.ofMillis(5L))
          .setTotalTimeout(Duration.ofMillis(10L))
          .build();

  @Spy private ApiCallContext callContext = FakeCallContext.createDefault();

  @Spy
  private ApiCallContext callContextWithRetrySettings =
      FakeCallContext.createDefault().withRetrySettings(retrySettings);

  private ClientContext clientContext =
      ClientContext.newBuilder().setDefaultCallContext(callContext).build();

  @Test
  public void testNonRetriedCallable() throws Exception {
    innerResult = SettableApiFuture.create();
    when(innerCallable.futureCall(anyString(), any(ApiCallContext.class))).thenReturn(innerResult);
    Duration timeout = Duration.ofMillis(5L);

    UnaryCallSettings<Object, Object> callSettings =
        UnaryCallSettings.newUnaryCallSettingsBuilder().setSimpleTimeoutNoRetries(timeout).build();
    UnaryCallable<String, String> callable =
        Callables.retrying(innerCallable, callSettings, clientContext);
    innerResult.set("No, my refrigerator is not running!");

    callable.futureCall("Is your refrigerator running?", callContext);
    verify(callContext, atLeastOnce()).getRetrySettings();
    verify(callContext).getTimeout();
    verify(callContext).withTimeout(timeout);
  }

  @Test
  public void testNonRetriedCallableWithRetrySettings() throws Exception {
    innerResult = SettableApiFuture.create();
    when(innerCallable.futureCall(anyString(), any(ApiCallContext.class))).thenReturn(innerResult);

    UnaryCallSettings<Object, Object> callSettings =
        UnaryCallSettings.newUnaryCallSettingsBuilder()
            .setSimpleTimeoutNoRetries(Duration.ofMillis(10L))
            .build();
    UnaryCallable<String, String> callable =
        Callables.retrying(innerCallable, callSettings, clientContext);
    innerResult.set("No, my refrigerator is not running!");

    Duration timeout = retrySettings.getInitialRpcTimeout();

    callable.futureCall("Is your refrigerator running?", callContextWithRetrySettings);

    verify(callContextWithRetrySettings, atLeastOnce()).getRetrySettings();
    verify(callContextWithRetrySettings).getTimeout();
    verify(callContextWithRetrySettings).withTimeout(timeout);
  }

  @Test
  public void testNonRetriedServerStreamingCallable() throws Exception {
    Duration timeout = Duration.ofMillis(5L);
    ServerStreamingCallSettings<Object, Object> callSettings =
        ServerStreamingCallSettings.newBuilder().setSimpleTimeoutNoRetries(timeout).build();
    ServerStreamingCallable<Object, Object> callable =
        Callables.retrying(innerServerStreamingCallable, callSettings, clientContext);

    callable.call("Is your refrigerator running?", callContext);

    verify(callContext, atLeastOnce()).getRetrySettings();
    verify(callContext).getTimeout();
    verify(callContext).withTimeout(timeout);
  }

  @Test
  public void testNonRetriedServerStreamingCallableWithRetrySettings() throws Exception {
    ServerStreamingCallSettings<Object, Object> callSettings =
        ServerStreamingCallSettings.newBuilder()
            .setSimpleTimeoutNoRetries(Duration.ofMillis(10L))
            .build();
    ServerStreamingCallable<Object, Object> callable =
        Callables.retrying(innerServerStreamingCallable, callSettings, clientContext);

    Duration timeout = retrySettings.getInitialRpcTimeout();

    callable.call("Is your refrigerator running?", callContextWithRetrySettings);

    verify(callContextWithRetrySettings, atLeastOnce()).getRetrySettings();
    verify(callContextWithRetrySettings).getTimeout();
    verify(callContextWithRetrySettings).withTimeout(timeout);
  }
}
