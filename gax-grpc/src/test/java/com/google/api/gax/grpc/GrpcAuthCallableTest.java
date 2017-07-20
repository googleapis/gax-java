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

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.SimpleCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.auth.Credentials;
import com.google.common.truth.Truth;
import io.grpc.CallCredentials;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class GrpcAuthCallableTest {
  private static class StashCallable extends UnaryCallable<Integer, Integer> {
    CallCredentials lastCredentials;

    @Override
    public ApiFuture<Integer> futureCall(Integer request, ApiCallContext inputContext) {
      GrpcCallContext context = GrpcCallContext.getAsGrpcCallContextWithDefault(inputContext);
      lastCredentials = context.getCallOptions().getCredentials();
      return ApiFutures.<Integer>immediateFuture(42);
    }
  }

  @Test
  public void testAuth() throws InterruptedException, ExecutionException, CancellationException {
    StashCallable stash = new StashCallable();
    Truth.assertThat(stash.lastCredentials).isNull();

    SimpleCallSettings<Integer, Integer> callSettings =
        SimpleCallSettings.<Integer, Integer>newBuilder().build();
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(
            stash,
            callSettings,
            ClientContext.newBuilder().setCredentials(Mockito.mock(Credentials.class)).build());
    Truth.assertThat(callable.futureCall(0).get()).isEqualTo(42);
    Truth.assertThat(stash.lastCredentials).isNotNull();
  }
}
