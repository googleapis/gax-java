/*
 * Copyright 2019 Google LLC
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
package com.google.api.gax.batching;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BatcherStatsTest {

  @Test
  public void testWhenNoException() {
    BatcherStats batcherStats = new BatcherStats();
    assertThat(batcherStats.asException()).isNull();
  }

  @Test
  public void testRequestFailuresOnly() {
    BatcherStats batcherStats = new BatcherStats();

    batcherStats.recordBatchFailure(
        ApiExceptionFactory.createException(
            new RuntimeException(), FakeStatusCode.of(StatusCode.Code.INVALID_ARGUMENT), false));
    batcherStats.recordBatchFailure(new RuntimeException("Request failed"));

    BatchingException exception = batcherStats.asException();
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).contains("2 batches failed to apply");
    assertThat(exception.getMessage()).contains("1 RuntimeException");
    assertThat(exception.getMessage()).contains("1 ApiException (1 INVALID_ARGUMENT ) ");
  }

  @Test
  public void testRequestAndEntryFailures() {
    BatcherStats batcherStats = new BatcherStats();
    batcherStats.recordBatchFailure(new RuntimeException("Request failed"));

    SettableApiFuture<Void> runTimeFail = SettableApiFuture.create();
    runTimeFail.setException(new IllegalStateException());
    batcherStats.recordBatchElementsCompletion(ImmutableList.of(runTimeFail));

    SettableApiFuture<Void> apiExceptionFuture = SettableApiFuture.create();
    SettableApiFuture<Void> npeFuture = SettableApiFuture.create();
    npeFuture.setException(new NullPointerException());
    apiExceptionFuture.setException(
        ApiExceptionFactory.createException(
            new RuntimeException(), FakeStatusCode.of(StatusCode.Code.UNAVAILABLE), false));

    batcherStats.recordBatchElementsCompletion(ImmutableList.of(npeFuture, apiExceptionFuture));

    BatchingException ex = batcherStats.asException();
    assertThat(ex).isNotNull();
    assertThat(ex.getMessage())
        .contains("1 batches failed to apply due to: 1 RuntimeException and 2 partial failures.");
    assertThat(ex.getMessage()).contains("1 IllegalStateException");
    assertThat(ex.getMessage()).contains("1 NullPointerException");
    assertThat(ex.getMessage()).contains("1 ApiException (1 UNAVAILABLE )");
  }
}
