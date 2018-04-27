/*
 * Copyright 2018 Google LLC
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
package com.google.api.gax.longrunning;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.testing.FakeOperationSnapshot;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class OperationFuturesTest {
  @Test(expected = IllegalArgumentException.class)
  public void testNotDone() {
    OperationFutures.immediateOperationFuture(
        FakeOperationSnapshot.newBuilder()
            .setName("required")
            .setDone(false)
            .setErrorCode(FakeStatusCode.of(StatusCode.Code.OK))
            .build());
  }

  @Test
  public void testCompleted() throws Exception {
    OperationFuture<String, Integer> future =
        OperationFutures.<String, Integer>immediateOperationFuture(
            FakeOperationSnapshot.newBuilder()
                .setName("myName")
                .setDone(true)
                .setResponse("theResponse")
                .setMetadata(42)
                .setErrorCode(FakeStatusCode.of(StatusCode.Code.OK))
                .build());
    assertThat(future.getName()).isEqualTo("myName");
    assertThat(future.get()).isEqualTo("theResponse");
    assertThat(future.getMetadata().get()).isEqualTo(42);
  }

  @Test
  public void testFailed() throws Exception {
    OperationFuture<String, Integer> future =
        OperationFutures.<String, Integer>immediateOperationFuture(
            FakeOperationSnapshot.newBuilder()
                .setName("myName")
                .setDone(true)
                .setMetadata(42)
                .setErrorCode(FakeStatusCode.of(StatusCode.Code.INVALID_ARGUMENT))
                .build());
    assertThat(future.getName()).isEqualTo("myName");
    assertThat(future.getMetadata().get()).isEqualTo(42);
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(ApiException.class);
    }
  }
}
