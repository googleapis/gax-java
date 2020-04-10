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
package com.google.api.gax.rpc;

import com.google.common.truth.Truth;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Test;

public class BatchedRequestIssuerTest {

  @Test
  public void test() throws Exception {
    BatchedFuture<Integer> batchedFuture = BatchedFuture.<Integer>create();
    BatchedRequestIssuer<Integer> issuer = new BatchedRequestIssuer<>(batchedFuture, 2);
    issuer.setResponse(1);

    Truth.assertThat(batchedFuture.isDone()).isFalse();
    issuer.sendResult();

    Truth.assertThat(batchedFuture.isDone()).isTrue();
    Truth.assertThat(batchedFuture.get()).isEqualTo(1);
  }

  @Test
  public void testNullResult() throws Exception {
    BatchedFuture<Integer> batchedFuture = BatchedFuture.<Integer>create();
    BatchedRequestIssuer<Integer> issuer = new BatchedRequestIssuer<>(batchedFuture, 2);
    issuer.setResponse(null);

    Truth.assertThat(batchedFuture.isDone()).isFalse();
    issuer.sendResult();

    Truth.assertThat(batchedFuture.isDone()).isTrue();
    Truth.assertThat(batchedFuture.get()).isNull();
  }

  @Test
  public void testException() throws Exception {
    Exception thrownException = new IllegalArgumentException("bad!");

    BatchedFuture<Integer> batchedFuture = BatchedFuture.<Integer>create();
    BatchedRequestIssuer<Integer> issuer = new BatchedRequestIssuer<>(batchedFuture, 2);
    issuer.setException(thrownException);

    Truth.assertThat(batchedFuture.isDone()).isFalse();
    issuer.sendResult();

    Truth.assertThat(batchedFuture.isDone()).isTrue();
    try {
      batchedFuture.get();
      Assert.fail("BatchedFuture should have thrown an exception");
    } catch (ExecutionException e) {
      Truth.assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void testNoResult() {
    try {
      BatchedFuture<Integer> batchedFuture = BatchedFuture.<Integer>create();
      BatchedRequestIssuer<Integer> issuer = new BatchedRequestIssuer<>(batchedFuture, 2);
      issuer.sendResult();
      Assert.fail("BatchedFuture should have thrown an exception");
    } catch (IllegalStateException expected) {
      Truth.assertThat(expected)
          .hasMessageThat()
          .contains("Neither response nor exception were set in BatchedRequestIssuer");
    }
  }

  @Test
  public void testResponseAndException() {
    try {
      Exception thrownException = new IllegalArgumentException("bad!");
      BatchedFuture<Integer> batchedFuture = BatchedFuture.<Integer>create();
      BatchedRequestIssuer<Integer> issuer = new BatchedRequestIssuer<>(batchedFuture, 2);
      issuer.setResponse(1);
      issuer.setException(thrownException);
      Assert.fail("BatchedFuture should have thrown an exception");
    } catch (IllegalStateException expected) {
      Truth.assertThat(expected)
          .hasMessageThat()
          .contains("Cannot set both exception and response");
    }
  }

  @Test
  public void testExceptionAndResponse() {
    try {
      Exception thrownException = new IllegalArgumentException("bad!");
      BatchedFuture<Integer> batchedFuture = BatchedFuture.<Integer>create();
      BatchedRequestIssuer<Integer> issuer = new BatchedRequestIssuer<>(batchedFuture, 2);
      issuer.setException(thrownException);
      issuer.setResponse(1);
      Assert.fail("BatchedFuture should have thrown an exception");
    } catch (IllegalStateException expected) {
      Truth.assertThat(expected)
          .hasMessageThat()
          .contains("Cannot set both exception and response");
    }
  }
}
