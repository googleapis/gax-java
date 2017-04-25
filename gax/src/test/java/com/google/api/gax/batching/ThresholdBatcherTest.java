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
package com.google.api.gax.batching;

import com.google.api.gax.batching.FlowController.FlowControlException;
import com.google.api.gax.batching.FlowController.LimitExceededBehavior;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.threeten.bp.Duration;

public class ThresholdBatcherTest {

  private static final ScheduledExecutorService EXECUTOR = new ScheduledThreadPoolExecutor(1);

  private static FlowController getDisabledFlowController() {
    return new FlowController(
        FlowControlSettings.newBuilder()
            .setLimitExceededBehavior(LimitExceededBehavior.Ignore)
            .build());
  }

  private static <T> BatchingFlowController<T> getDisabledBatchingFlowController() {
    return new BatchingFlowController<>(
        getDisabledFlowController(),
        new ElementCounter<T>() {
          @Override
          public long count(T t) {
            return 1;
          }
        },
        new ElementCounter<T>() {
          @Override
          public long count(T t) {
            return 1;
          }
        });
  }

  private static TrackedFlowController trackedFlowController;

  private static BatchingFlowController<SimpleBatch> getTrackedIntegerBatchingFlowController(
      Integer elementCount, Integer byteCount, LimitExceededBehavior limitExceededBehaviour) {
    trackedFlowController =
        new TrackedFlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(elementCount)
                .setMaxOutstandingRequestBytes(byteCount)
                .setLimitExceededBehavior(limitExceededBehaviour)
                .build());
    return new BatchingFlowController<>(
        trackedFlowController,
        new ElementCounter<SimpleBatch>() {
          @Override
          public long count(SimpleBatch t) {
            return t.getIntegers().size();
          }
        },
        new ElementCounter<SimpleBatch>() {
          @Override
          public long count(SimpleBatch t) {
            long counter = 0;
            for (Integer i : t.integers) {
              counter += i;
            }
            return counter;
          }
        });
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static class SimpleBatch {

    private final List<Integer> integers = new ArrayList<>();

    private static SimpleBatch fromInteger(Integer integer) {
      SimpleBatch batch = new SimpleBatch();
      batch.integers.add(integer);
      return batch;
    }

    public void merge(SimpleBatch t) {
      integers.addAll(t.integers);
    }

    private List<Integer> getIntegers() {
      return integers;
    }
  }

  private static class SimpleBatchMerger implements BatchMerger<SimpleBatch> {
    @Override
    public void merge(SimpleBatch batch, SimpleBatch newBatch) {
      batch.merge(newBatch);
    }
  }

  private static ThresholdBatcher.Builder<SimpleBatch> createSimpleBatcherBuidler(
      AccumulatingBatchReceiver<SimpleBatch> receiver) {
    return ThresholdBatcher.<SimpleBatch>newBuilder()
        .setThresholds(BatchingThresholds.<SimpleBatch>of(100))
        .setExecutor(EXECUTOR)
        .setMaxDelay(Duration.ofMillis(10000))
        .setReceiver(receiver)
        .setFlowController(ThresholdBatcherTest.<SimpleBatch>getDisabledBatchingFlowController())
        .setBatchMerger(new SimpleBatchMerger());
  }

  @Test
  public void testAdd() throws Exception {
    AccumulatingBatchReceiver<SimpleBatch> receiver = new AccumulatingBatchReceiver<>();
    ThresholdBatcher<SimpleBatch> batcher = createSimpleBatcherBuidler(receiver).build();
    batcher.add(SimpleBatch.fromInteger(14));
    Truth.assertThat(batcher.isEmpty()).isFalse();
    Truth.assertThat(receiver.getBatches().size()).isEqualTo(0);

    batcher.pushCurrentBatch().get();
    Truth.assertThat(batcher.isEmpty()).isTrue();
    Truth.assertThat(receiver.getBatches().size()).isEqualTo(1);
    Truth.assertThat(receiver.getBatches().get(0).getIntegers()).isEqualTo(Arrays.asList(14));
  }

  @Test
  public void testBatching() throws Exception {
    AccumulatingBatchReceiver<SimpleBatch> receiver = new AccumulatingBatchReceiver<>();
    ThresholdBatcher<SimpleBatch> batcher =
        createSimpleBatcherBuidler(receiver)
            .setThresholds(BatchingThresholds.<SimpleBatch>of(2))
            .build();

    batcher.add(SimpleBatch.fromInteger(3));
    batcher.add(SimpleBatch.fromInteger(5));
    // Give time for the executor to push the batch
    Thread.sleep(100);
    Truth.assertThat(receiver.getBatches().size()).isEqualTo(1);

    batcher.add(SimpleBatch.fromInteger(7));
    batcher.add(SimpleBatch.fromInteger(9));
    // Give time for the executor to push the batch
    Thread.sleep(100);
    Truth.assertThat(receiver.getBatches().size()).isEqualTo(2);

    batcher.add(SimpleBatch.fromInteger(11));

    batcher.pushCurrentBatch().get();

    List<List<Integer>> expected =
        Arrays.asList(Arrays.asList(3, 5), Arrays.asList(7, 9), Arrays.asList(11));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBatch batch : receiver.getBatches()) {
      actual.add(batch.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testBatchingWithDelay() throws Exception {
    AccumulatingBatchReceiver<SimpleBatch> receiver = new AccumulatingBatchReceiver<>();
    ThresholdBatcher<SimpleBatch> batcher =
        createSimpleBatcherBuidler(receiver).setMaxDelay(Duration.ofMillis(100)).build();

    batcher.add(SimpleBatch.fromInteger(3));
    batcher.add(SimpleBatch.fromInteger(5));
    // Give time for the delay to trigger and push the batch
    Thread.sleep(500);
    Truth.assertThat(receiver.getBatches().size()).isEqualTo(1);

    batcher.add(SimpleBatch.fromInteger(11));

    batcher.pushCurrentBatch().get();

    List<List<Integer>> expected = Arrays.asList(Arrays.asList(3, 5), Arrays.asList(11));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBatch batch : receiver.getBatches()) {
      actual.add(batch.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testExceptionWithNullFlowController() {
    thrown.expect(NullPointerException.class);
    ThresholdBatcher.<SimpleBatch>newBuilder()
        .setThresholds(BatchingThresholds.<SimpleBatch>of(100))
        .setExecutor(EXECUTOR)
        .setMaxDelay(Duration.ofMillis(10000))
        .setReceiver(new AccumulatingBatchReceiver<SimpleBatch>())
        .setBatchMerger(new SimpleBatchMerger())
        .build();
  }

  @Test
  public void testBatchingWithFlowControl() throws Exception {
    AccumulatingBatchReceiver<SimpleBatch> receiver = new AccumulatingBatchReceiver<>();
    ThresholdBatcher<SimpleBatch> batcher =
        createSimpleBatcherBuidler(receiver)
            .setThresholds(BatchingThresholds.<SimpleBatch>of(2))
            .setFlowController(
                getTrackedIntegerBatchingFlowController(2, null, LimitExceededBehavior.Block))
            .build();

    Truth.assertThat(trackedFlowController.getElementsReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getElementsReleased()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReleased()).isEqualTo(0);

    batcher.add(SimpleBatch.fromInteger(3));
    batcher.add(SimpleBatch.fromInteger(5));
    batcher.add(
        SimpleBatch.fromInteger(7)); // We expect to block here until the first batch is handled
    Truth.assertThat(receiver.getBatches().size()).isEqualTo(1);
    batcher.add(SimpleBatch.fromInteger(9));
    batcher.add(
        SimpleBatch.fromInteger(11)); // We expect to block here until the second batch is handled
    Truth.assertThat(receiver.getBatches().size()).isEqualTo(2);

    batcher.pushCurrentBatch().get();

    List<List<Integer>> expected =
        Arrays.asList(Arrays.asList(3, 5), Arrays.asList(7, 9), Arrays.asList(11));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBatch batch : receiver.getBatches()) {
      actual.add(batch.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);

    Truth.assertThat(trackedFlowController.getElementsReserved())
        .isEqualTo(trackedFlowController.getElementsReleased());
    Truth.assertThat(trackedFlowController.getBytesReserved())
        .isEqualTo(trackedFlowController.getBytesReleased());
  }

  @Test
  public void testBatchingFlowControlExceptionRecovery() throws Exception {
    AccumulatingBatchReceiver<SimpleBatch> receiver = new AccumulatingBatchReceiver<>();
    ThresholdBatcher<SimpleBatch> batcher =
        createSimpleBatcherBuidler(receiver)
            .setThresholds(BatchingThresholds.<SimpleBatch>of(4))
            .setFlowController(
                getTrackedIntegerBatchingFlowController(
                    3, null, LimitExceededBehavior.ThrowException))
            .build();

    Truth.assertThat(trackedFlowController.getElementsReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getElementsReleased()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReleased()).isEqualTo(0);

    batcher.add(SimpleBatch.fromInteger(3));
    batcher.add(SimpleBatch.fromInteger(5));
    batcher.add(SimpleBatch.fromInteger(7));
    try {
      batcher.add(SimpleBatch.fromInteger(9));
      Truth.assertWithMessage("Failing: expected exception").that(false).isTrue();
    } catch (FlowControlException e) {
    }
    batcher.pushCurrentBatch().get();
    Truth.assertThat(receiver.getBatches().size()).isEqualTo(1);
    batcher.add(SimpleBatch.fromInteger(11));
    batcher.add(SimpleBatch.fromInteger(13));
    batcher.pushCurrentBatch().get();

    List<List<Integer>> expected = Arrays.asList(Arrays.asList(3, 5, 7), Arrays.asList(11, 13));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBatch batch : receiver.getBatches()) {
      actual.add(batch.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);

    Truth.assertThat(trackedFlowController.getElementsReserved())
        .isEqualTo(trackedFlowController.getElementsReleased());
    Truth.assertThat(trackedFlowController.getBytesReserved())
        .isEqualTo(trackedFlowController.getBytesReleased());
  }
}
