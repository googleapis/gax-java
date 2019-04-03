/*
 * Copyright 2016 Google LLC
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
package com.google.api.gax.batching.v2;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.StatusCode;
import com.google.common.collect.Sets;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class BatchingCallSettingsTest {

  @Test
  public void testEmptyBuilder() {
    @SuppressWarnings("unchecked")
    BatchingDescriptor<Integer, Integer, Integer, Integer> batchingDescriptor =
        Mockito.mock(BatchingDescriptor.class);
    BatchingCallSettings.Builder<Integer, Integer, Integer, Integer> builder =
        BatchingCallSettings.newBuilder(batchingDescriptor);

    assertThat(builder.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    assertThat(builder.getBatchingSettings()).isNull();
    assertThat(builder.getRetryableCodes().size()).isEqualTo(0);
    assertThat(builder.getRetrySettings()).isNotNull();

    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder().setElementCountThreshold(1L).build();
    builder.setBatchingSettings(batchingSettings);
    BatchingCallSettings settings = builder.build();

    assertThat(settings.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    assertThat(settings.getBatchingSettings()).isSameAs(batchingSettings);
    assertThat(settings.getRetryableCodes().size()).isEqualTo(0);
    assertThat(settings.getRetrySettings()).isNotNull();
  }

  @Test
  public void testBuilder() {
    @SuppressWarnings("unchecked")
    BatchingDescriptor<Integer, Integer, Integer, Integer> batchingDescriptor =
        Mockito.mock(BatchingDescriptor.class);
    BatchingCallSettings.Builder<Integer, Integer, Integer, Integer> builder =
        BatchingCallSettings.newBuilder(batchingDescriptor);

    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder().setElementCountThreshold(1L).build();
    Set<StatusCode.Code> retryCodes = Sets.newHashSet(StatusCode.Code.UNAVAILABLE);
    RetrySettings retrySettings = RetrySettings.newBuilder().build();

    builder.setBatchingSettings(batchingSettings);
    builder.setRetryableCodes(retryCodes);
    builder.setRetrySettings(retrySettings);

    assertThat(builder.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    assertThat(builder.getBatchingSettings()).isSameAs(batchingSettings);
    assertThat(builder.getRetryableCodes().size()).isEqualTo(1);
    assertThat(builder.getRetrySettings()).isSameAs(retrySettings);

    BatchingCallSettings settings = builder.build();

    assertThat(settings.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    assertThat(settings.getBatchingSettings()).isSameAs(batchingSettings);
    assertThat(settings.getRetryableCodes().size()).isEqualTo(1);
    assertThat(settings.getRetrySettings()).isSameAs(retrySettings);
  }

  @Test
  public void testBuilderFromSettings() throws Exception {
    @SuppressWarnings("unchecked")
    BatchingDescriptor<Integer, Integer, Integer, Integer> batchingDescriptor =
        Mockito.mock(BatchingDescriptor.class);
    BatchingCallSettings.Builder<Integer, Integer, Integer, Integer> builder =
        BatchingCallSettings.newBuilder(batchingDescriptor);

    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder().setElementCountThreshold(1L).build();
    Set<StatusCode.Code> retryCodes = Sets.newHashSet(StatusCode.Code.UNAVAILABLE);
    RetrySettings retrySettings = RetrySettings.newBuilder().build();

    builder.setBatchingSettings(batchingSettings);
    builder.setRetryableCodes(retryCodes);
    builder.setRetrySettings(retrySettings);

    BatchingCallSettings settings = builder.build();
    BatchingCallSettings.Builder newBuilder = settings.toBuilder();

    assertThat(newBuilder.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    assertThat(newBuilder.getBatchingSettings()).isSameAs(batchingSettings);
    assertThat(newBuilder.getRetryableCodes().size()).isEqualTo(1);
    assertThat(newBuilder.getRetrySettings()).isSameAs(retrySettings);
  }
}
