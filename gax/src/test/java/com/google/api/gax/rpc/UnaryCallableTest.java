/*
 * Copyright 2016, Google Inc. All rights reserved.
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

import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeSimpleApi.StashCallable;
import com.google.auth.Credentials;
import com.google.common.truth.Truth;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Tests for {@link UnaryCallable}. */
@RunWith(JUnit4.class)
public class UnaryCallableTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void simpleCall() throws Exception {
    StashCallable<Integer, Integer> stashCallable = new StashCallable<>(1);

    Integer response = stashCallable.call(2, FakeCallContext.createDefault());
    Truth.assertThat(response).isEqualTo(Integer.valueOf(1));
    FakeCallContext callContext = (FakeCallContext) stashCallable.getContext();
    Truth.assertThat(callContext.getChannel()).isNull();
    Truth.assertThat(callContext.getCredentials()).isNull();
  }

  @Test
  public void call() throws Exception {
    ApiCallContext defaultCallContext = FakeCallContext.createDefault();
    StashCallable<Integer, Integer> stashCallable = new StashCallable<>(1);
    UnaryCallable<Integer, Integer> callable =
        stashCallable.withDefaultCallContext(defaultCallContext);

    Integer response = callable.call(2);
    Truth.assertThat(response).isEqualTo(Integer.valueOf(1));
    Truth.assertThat(stashCallable.getContext()).isNotNull();
    Truth.assertThat(stashCallable.getContext()).isSameAs(defaultCallContext);
  }

  @Test
  public void callWithContext() throws Exception {
    FakeChannel channel = new FakeChannel();
    Credentials credentials = Mockito.mock(Credentials.class);
    ApiCallContext context =
        FakeCallContext.createDefault().withChannel(channel).withCredentials(credentials);
    StashCallable<Integer, Integer> stashCallable = new StashCallable<>(1);
    UnaryCallable<Integer, Integer> callable =
        stashCallable.withDefaultCallContext(FakeCallContext.createDefault());

    Integer response = callable.call(2, context);
    Truth.assertThat(response).isEqualTo(Integer.valueOf(1));
    FakeCallContext actualContext = (FakeCallContext) stashCallable.getContext();
    Truth.assertThat(actualContext.getChannel()).isSameAs(channel);
    Truth.assertThat(actualContext.getCredentials()).isSameAs(credentials);
  }
}
