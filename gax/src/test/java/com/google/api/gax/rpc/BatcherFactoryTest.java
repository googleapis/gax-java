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

import static com.google.api.gax.rpc.testing.FakeBatchableApi.SquarerBatchingDescriptor;

import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.batching.FlowController.LimitExceededBehavior;
import com.google.api.gax.batching.PartitionKey;
import com.google.api.gax.batching.ThresholdBatcher;
import com.google.api.gax.rpc.testing.FakeBatchableApi.LabeledIntList;
import com.google.common.truth.Truth;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class BatcherFactoryTest {
  private ScheduledExecutorService batchingExecutor;

  @Before
  public void setUp() {
    batchingExecutor = new ScheduledThreadPoolExecutor(1);
  }

  @After
  public void tearDown() {
    batchingExecutor.shutdownNow();
  }

  @Test
  public void testGetPushingBatcher() {
    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder()
            .setDelayThreshold(Duration.ofSeconds(1))
            .setElementCountThreshold(2L)
            .setRequestByteThreshold(1000L)
            .build();
    FlowControlSettings flowControlSettings =
        FlowControlSettings.newBuilder()
            .setLimitExceededBehavior(LimitExceededBehavior.Ignore)
            .build();
    FlowController flowController = new FlowController(flowControlSettings);
    BatcherFactory<LabeledIntList, List<Integer>> batcherFactory =
        new BatcherFactory<>(
            new SquarerBatchingDescriptor(), batchingSettings, batchingExecutor, flowController);
    Truth.assertThat(batcherFactory.getBatchingSettings()).isSameInstanceAs(batchingSettings);

    ThresholdBatcher<Batch<LabeledIntList, List<Integer>>> batcherFoo =
        batcherFactory.getPushingBatcher(new PartitionKey("foo"));

    ThresholdBatcher<Batch<LabeledIntList, List<Integer>>> batcherFoo2 =
        batcherFactory.getPushingBatcher(new PartitionKey("foo"));

    ThresholdBatcher<Batch<LabeledIntList, List<Integer>>> batcherBar =
        batcherFactory.getPushingBatcher(new PartitionKey("bar"));

    Truth.assertThat(batcherFoo).isSameInstanceAs(batcherFoo2);
    Truth.assertThat(batcherFoo).isNotSameInstanceAs(batcherBar);
  }
}
