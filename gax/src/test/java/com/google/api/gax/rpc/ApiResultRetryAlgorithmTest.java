/*
 * Copyright 2020 Google LLC
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.gax.rpc.StatusCode.Code;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.internal.util.collections.Sets;

@RunWith(JUnit4.class)
public class ApiResultRetryAlgorithmTest {

  @Test
  public void testShouldRetryNoContext() {
    ApiException nonRetryable = mock(ApiException.class);
    when(nonRetryable.isRetryable()).thenReturn(false);
    ApiException retryable = mock(ApiException.class);
    when(retryable.isRetryable()).thenReturn(true);

    ApiResultRetryAlgorithm<String> algorithm = new ApiResultRetryAlgorithm<>();
    assertThat(algorithm.shouldRetry(nonRetryable, null)).isFalse();
    assertThat(algorithm.shouldRetry(retryable, null)).isTrue();
  }

  @Test
  public void testShouldRetryWithContextWithoutRetryableCodes() {
    ApiCallContext context = mock(ApiCallContext.class);
    // No retryable codes in the call context, means that the retry algorithm should fall back to
    // its default implementation.
    when(context.getRetryableCodes()).thenReturn(null);

    StatusCode unavailable = mock(StatusCode.class);
    when(unavailable.getCode()).thenReturn(Code.UNAVAILABLE);
    ApiException nonRetryable = mock(ApiException.class);
    when(nonRetryable.isRetryable()).thenReturn(false);
    when(nonRetryable.getStatusCode()).thenReturn(unavailable);

    ApiException retryable = mock(ApiException.class);
    when(retryable.isRetryable()).thenReturn(true);
    when(retryable.getStatusCode()).thenReturn(unavailable);

    ApiResultRetryAlgorithm<String> algorithm = new ApiResultRetryAlgorithm<>();
    assertThat(algorithm.shouldRetry(context, nonRetryable, null)).isFalse();
    assertThat(algorithm.shouldRetry(context, retryable, null)).isTrue();
  }

  @Test
  public void testShouldRetryWithContextWithRetryableCodes() {
    ApiCallContext context = mock(ApiCallContext.class);
    when(context.getRetryableCodes())
        .thenReturn(Sets.newSet(StatusCode.Code.DEADLINE_EXCEEDED, StatusCode.Code.UNAVAILABLE));

    StatusCode unavailable = mock(StatusCode.class);
    when(unavailable.getCode()).thenReturn(Code.UNAVAILABLE);
    StatusCode dataLoss = mock(StatusCode.class);
    when(dataLoss.getCode()).thenReturn(Code.DATA_LOSS);

    ApiException unavailableException = mock(ApiException.class);
    // The return value of isRetryable() will be ignored, as UNAVAILABLE has been added as a
    // retryable code to the call context.
    when(unavailableException.isRetryable()).thenReturn(false);
    when(unavailableException.getStatusCode()).thenReturn(unavailable);

    ApiException dataLossException = mock(ApiException.class);
    when(dataLossException.isRetryable()).thenReturn(true);
    when(dataLossException.getStatusCode()).thenReturn(dataLoss);

    ApiResultRetryAlgorithm<String> algorithm = new ApiResultRetryAlgorithm<>();
    assertThat(algorithm.shouldRetry(context, unavailableException, null)).isTrue();
    assertThat(algorithm.shouldRetry(context, dataLossException, null)).isFalse();
  }

  @Test
  public void testShouldRetryWithContextWithEmptyRetryableCodes() {
    ApiCallContext context = mock(ApiCallContext.class);
    // This will effectively make the RPC non-retryable.
    when(context.getRetryableCodes()).thenReturn(Collections.<Code>emptySet());

    StatusCode unavailable = mock(StatusCode.class);
    when(unavailable.getCode()).thenReturn(Code.UNAVAILABLE);

    ApiException unavailableException = mock(ApiException.class);
    when(unavailableException.isRetryable()).thenReturn(true);
    when(unavailableException.getStatusCode()).thenReturn(unavailable);

    ApiResultRetryAlgorithm<String> algorithm = new ApiResultRetryAlgorithm<>();
    assertThat(algorithm.shouldRetry(context, unavailableException, null)).isFalse();
  }
}
