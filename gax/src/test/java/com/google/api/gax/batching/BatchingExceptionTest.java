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

import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BatchingExceptionTest {

  @Test
  public void testBatchingException() {
    Map<Class, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    failureCounts.put(RuntimeException.class, new AtomicInteger(6));
    failureCounts.put(IOException.class, new AtomicInteger(3));

    Map<StatusCode, AtomicInteger> statusCounts = new ConcurrentHashMap<>();
    statusCounts.put(FakeStatusCode.of(StatusCode.Code.UNIMPLEMENTED), new AtomicInteger(34));
    statusCounts.put(FakeStatusCode.of(StatusCode.Code.INVALID_ARGUMENT), new AtomicInteger(324));

    BatchingException underTest = new BatchingException(10, failureCounts, statusCounts);
    assertThat(underTest).isInstanceOf(RuntimeException.class);
    assertThat(underTest.getTotalFailureCount()).isEqualTo(10);
    assertThat(underTest.getFailureTypesCount()).isEqualTo(failureCounts);
    assertThat(underTest.getFailureStatusCodeCount()).isEqualTo(statusCounts);
  }
}
