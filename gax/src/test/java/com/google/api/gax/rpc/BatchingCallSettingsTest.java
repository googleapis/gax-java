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
package com.google.api.gax.rpc;

import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.retrying.RetrySettings;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;
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
    BatchingDescriptor<Integer, Integer> batchingDescriptor =
        Mockito.mock(BatchingDescriptor.class);
    BatchingCallSettings.Builder<Integer, Integer> builder =
        BatchingCallSettings.newBuilder(batchingDescriptor);

    Truth.assertThat(builder.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    Truth.assertThat(builder.getBatchingSettings()).isNull();
    Truth.assertThat(builder.getFlowController()).isNull();
    Truth.assertThat(builder.getRetryableCodes().size()).isEqualTo(0);
    Truth.assertThat(builder.getRetrySettings()).isNotNull();

    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder().setElementCountThreshold(1L).build();
    builder.setBatchingSettings(batchingSettings);
    BatchingCallSettings settings = builder.build();

    Truth.assertThat(settings.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    Truth.assertThat(settings.getBatchingSettings()).isSameAs(batchingSettings);
    Truth.assertThat(settings.getFlowController()).isNotNull();
    Truth.assertThat(settings.getRetryableCodes().size()).isEqualTo(0);
    Truth.assertThat(settings.getRetrySettings()).isNotNull();
  }

  @Test
  public void testBuilder() {
    @SuppressWarnings("unchecked")
    BatchingDescriptor<Integer, Integer> batchingDescriptor =
        Mockito.mock(BatchingDescriptor.class);
    BatchingCallSettings.Builder<Integer, Integer> builder =
        BatchingCallSettings.newBuilder(batchingDescriptor);

    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder().setElementCountThreshold(1L).build();
    FlowController flowController = Mockito.mock(FlowController.class);
    Set<StatusCode> retryCodes = Sets.<StatusCode>newHashSet(new StatusCode() {});
    RetrySettings retrySettings = RetrySettings.newBuilder().build();

    builder.setBatchingSettings(batchingSettings);
    builder.setFlowController(flowController);
    builder.setRetryableCodes(retryCodes);
    builder.setRetrySettings(retrySettings);

    Truth.assertThat(builder.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    Truth.assertThat(builder.getBatchingSettings()).isSameAs(batchingSettings);
    Truth.assertThat(builder.getFlowController()).isSameAs(flowController);
    Truth.assertThat(builder.getRetryableCodes().size()).isEqualTo(1);
    Truth.assertThat(builder.getRetrySettings()).isSameAs(retrySettings);

    BatchingCallSettings settings = builder.build();

    Truth.assertThat(settings.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    Truth.assertThat(settings.getBatchingSettings()).isSameAs(batchingSettings);
    Truth.assertThat(settings.getFlowController()).isSameAs(flowController);
    Truth.assertThat(settings.getRetryableCodes().size()).isEqualTo(1);
    Truth.assertThat(settings.getRetrySettings()).isSameAs(retrySettings);
  }

  @Test
  public void testBuilderFromSettings() throws Exception {
    @SuppressWarnings("unchecked")
    BatchingDescriptor<Integer, Integer> batchingDescriptor =
        Mockito.mock(BatchingDescriptor.class);
    BatchingCallSettings.Builder<Integer, Integer> builder =
        BatchingCallSettings.newBuilder(batchingDescriptor);

    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder().setElementCountThreshold(1L).build();
    FlowController flowController = Mockito.mock(FlowController.class);
    Set<StatusCode> retryCodes = Sets.<StatusCode>newHashSet(new StatusCode() {});
    RetrySettings retrySettings = RetrySettings.newBuilder().build();

    builder.setBatchingSettings(batchingSettings);
    builder.setFlowController(flowController);
    builder.setRetryableCodes(retryCodes);
    builder.setRetrySettings(retrySettings);

    BatchingCallSettings settings = builder.build();
    BatchingCallSettings.Builder newBuilder = settings.toBuilder();

    Truth.assertThat(newBuilder.getBatchingDescriptor()).isSameAs(batchingDescriptor);
    Truth.assertThat(newBuilder.getBatchingSettings()).isSameAs(batchingSettings);
    Truth.assertThat(newBuilder.getFlowController()).isSameAs(flowController);
    Truth.assertThat(newBuilder.getRetryableCodes().size()).isEqualTo(1);
    Truth.assertThat(newBuilder.getRetrySettings()).isSameAs(retrySettings);
  }

  @Test
  public void testNoFlowControlSettings() throws Exception {
    @SuppressWarnings("unchecked")
    BatchingDescriptor<Integer, Integer> batchingDescriptor =
        Mockito.mock(BatchingDescriptor.class);
    BatchingCallSettings.Builder<Integer, Integer> builder =
        BatchingCallSettings.newBuilder(batchingDescriptor);

    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder().setElementCountThreshold(1L).build();

    builder.setBatchingSettings(batchingSettings);

    BatchingCallSettings settings = builder.build();

    Truth.assertThat(settings.getFlowController()).isNotNull();
  }
}
