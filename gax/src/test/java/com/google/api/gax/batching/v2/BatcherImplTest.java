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
package com.google.api.gax.batching.v2;

import static com.google.api.gax.rpc.testing.FakeBatchableApi.SQUARER_BATCHING_DESC_V2;
import static com.google.api.gax.rpc.testing.FakeBatchableApi.callLabeledIntSquarer;
import static com.google.common.truth.Truth.assertThat;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.batching.v2.FlowControlException.MaxOutstandingElementCountReachedException;
import com.google.api.gax.batching.v2.FlowControlException.MaxOutstandingRequestBytesReachedException;
import com.google.api.gax.batching.v2.FlowControlSettings.LimitExceededBehavior;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakeBatchableApi.LabeledIntList;
import com.google.api.gax.rpc.testing.FakeBatchableApi.SquarerBatchingDescriptorV2;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class BatcherImplTest {

  private static final ScheduledExecutorService EXECUTOR =
      Executors.newSingleThreadScheduledExecutor();

  private Batcher<Integer, Integer> underTest;
  private final LabeledIntList labeledIntList = new LabeledIntList("Default");
  private final BatchingSettings batchingSettings =
      BatchingSettings.newBuilder()
          .setRequestByteThreshold(1000L)
          .setElementCountThreshold(1000)
          .setDelayThreshold(Duration.ofSeconds(1))
          .setFlowControlSettings(
              FlowControlSettings.newBuilder()
                  .setLimitExceededBehavior(LimitExceededBehavior.Block)
                  .setMaxOutstandingElementCount(500)
                  .setMaxOutstandingRequestBytes(500L)
                  .build())
          .build();

  @After
  public void tearDown() throws InterruptedException {
    if (underTest != null) {
      underTest.close();
    }
  }

  @AfterClass
  public static void tearDownExecutor() throws InterruptedException {
    EXECUTOR.shutdown();
    EXECUTOR.awaitTermination(100, TimeUnit.MILLISECONDS);
  }
  /** The accumulated results in the test are resolved when {@link Batcher#flush()} is called. */
  @Test
  public void testResultsAreResolvedAfterFlush() throws Exception {
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2,
            callLabeledIntSquarer,
            labeledIntList,
            batchingSettings,
            EXECUTOR);
    Future<Integer> result = underTest.add(4);
    assertThat(result.isDone()).isFalse();
    underTest.flush();
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(16);

    Future<Integer> anotherResult = underTest.add(5);
    assertThat(anotherResult.isDone()).isFalse();
  }

  /** Element results are resolved after batch is closed. */
  @Test
  public void testWhenBatcherIsClose() throws Exception {
    Future<Integer> result;
    try (Batcher<Integer, Integer> batcher =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2,
            callLabeledIntSquarer,
            labeledIntList,
            batchingSettings,
            EXECUTOR)) {
      result = batcher.add(5);
    }
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(25);
  }

  /** Validates exception when batch is called after {@link Batcher#close()}. */
  @Test
  public void testNoElementAdditionAfterClose() throws Exception {
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2,
            callLabeledIntSquarer,
            labeledIntList,
            batchingSettings,
            EXECUTOR);
    underTest.close();
    Throwable addOnClosedError = null;
    try {
      underTest.add(1);
    } catch (Exception ex) {
      addOnClosedError = ex;
    }
    assertThat(addOnClosedError).isInstanceOf(IllegalStateException.class);
    assertThat(addOnClosedError)
        .hasMessageThat()
        .matches("Cannot add elements on a closed batcher");
  }

  /** Verifies exception occurred at RPC is propagated to element results */
  @Test
  public void testResultFailureAfterRPCFailure() throws Exception {
    final Exception fakeError = new RuntimeException();
    UnaryCallable<LabeledIntList, List<Integer>> unaryCallable =
        new UnaryCallable<LabeledIntList, List<Integer>>() {
          @Override
          public ApiFuture<List<Integer>> futureCall(
              LabeledIntList request, ApiCallContext context) {
            return ApiFutures.immediateFailedFuture(fakeError);
          }
        };
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2, unaryCallable, labeledIntList, batchingSettings, EXECUTOR);
    Future<Integer> failedResult = underTest.add(5);
    underTest.flush();
    assertThat(failedResult.isDone()).isTrue();
    Throwable actualError = null;
    try {
      failedResult.get();
    } catch (InterruptedException | ExecutionException ex) {
      actualError = ex;
    }

    assertThat(actualError).hasCauseThat().isSameInstanceAs(fakeError);
  }

  /** Resolves future results when {@link BatchingDescriptor#splitResponse} throws exception. */
  @Test
  public void testExceptionInDescriptor() throws InterruptedException {
    final RuntimeException fakeError = new RuntimeException("internal exception");
    BatchingDescriptor<Integer, Integer, LabeledIntList, List<Integer>> descriptor =
        new SquarerBatchingDescriptorV2() {
          @Override
          public void splitResponse(
              List<Integer> batchResponse, List<SettableApiFuture<Integer>> batch) {
            throw fakeError;
          }
        };
    underTest =
        new BatcherImpl<>(
            descriptor, callLabeledIntSquarer, labeledIntList, batchingSettings, EXECUTOR);

    Future<Integer> result = underTest.add(2);
    underTest.flush();
    Throwable actualError = null;
    try {
      result.get();
    } catch (ExecutionException ex) {
      actualError = ex;
    }

    assertThat(actualError).hasCauseThat().isSameInstanceAs(fakeError);
  }

  /** Resolves future results when {@link BatchingDescriptor#splitException} throws exception */
  @Test
  public void testExceptionInDescriptorErrorHandling() throws InterruptedException {
    final RuntimeException fakeError = new RuntimeException("internal exception");
    BatchingDescriptor<Integer, Integer, LabeledIntList, List<Integer>> descriptor =
        new SquarerBatchingDescriptorV2() {
          @Override
          public void splitResponse(
              List<Integer> batchResponse, List<SettableApiFuture<Integer>> batch) {
            throw fakeError;
          }

          @Override
          public void splitException(Throwable throwable, List<SettableApiFuture<Integer>> batch) {
            throw fakeError;
          }
        };
    underTest =
        new BatcherImpl<>(
            descriptor, callLabeledIntSquarer, labeledIntList, batchingSettings, EXECUTOR);

    Future<Integer> result = underTest.add(2);
    underTest.flush();
    Throwable actualError = null;
    try {
      result.get();
    } catch (ExecutionException ex) {
      actualError = ex;
    }

    assertThat(actualError).hasCauseThat().isSameInstanceAs(fakeError);
  }

  @Test
  public void testWhenElementCountExceeds() throws Exception {
    BatchingSettings settings = batchingSettings.toBuilder().setElementCountThreshold(2).build();
    testElementTriggers(settings);
  }

  @Test
  public void testWhenElementBytesExceeds() throws Exception {
    BatchingSettings settings = batchingSettings.toBuilder().setRequestByteThreshold(2L).build();
    testElementTriggers(settings);
  }

  @Test
  public void testWhenThresholdIsDisabled() throws Exception {
    BatchingSettings settings =
        BatchingSettings.newBuilder()
            .setElementCountThreshold(0)
            .setRequestByteThreshold(0)
            .setDelayThreshold(Duration.ofMillis(1))
            .build();
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2, callLabeledIntSquarer, labeledIntList, settings, EXECUTOR);
    Future<Integer> result = underTest.add(2);
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(4);
  }

  @Test
  public void testWhenDelayThresholdExceeds() throws Exception {
    BatchingSettings settings =
        batchingSettings.toBuilder().setDelayThreshold(Duration.ofMillis(200)).build();
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2, callLabeledIntSquarer, labeledIntList, settings, EXECUTOR);
    Future<Integer> result = underTest.add(6);
    assertThat(result.isDone()).isFalse();
    assertThat(result.get()).isEqualTo(36);
  }

  /** Validates that the elements are not leaking to multiple batches */
  @Test(timeout = 500)
  public void testElementsNotLeaking() throws Exception {
    ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    ScheduledExecutorService multiThreadExecutor = Executors.newScheduledThreadPool(20);

    final AtomicBoolean isDuplicateElement = new AtomicBoolean(false);
    final ConcurrentMap<Integer, Boolean> map = new ConcurrentHashMap<>();
    final UnaryCallable<LabeledIntList, List<Integer>> callable =
        new UnaryCallable<LabeledIntList, List<Integer>>() {
          @Override
          public ApiFuture<List<Integer>> futureCall(
              LabeledIntList request, ApiCallContext context) {
            for (int val : request.ints) {
              Boolean isPresent = map.putIfAbsent(val, Boolean.TRUE);
              if (isPresent != null && isPresent) {
                isDuplicateElement.set(true);
                throw new AssertionError("Duplicate Element found");
              }
            }
            return ApiFutures.immediateFuture(request.ints);
          }
        };
    // Disabling flow control
    BatchingSettings settings =
        batchingSettings
            .toBuilder()
            .setDelayThreshold(Duration.ofMillis(50))
            .setFlowControlSettings(FlowControlSettings.getDefaultInstance())
            .build();

    try (final BatcherImpl<Integer, Integer, LabeledIntList, List<Integer>> batcherTest =
        new BatcherImpl<>(SQUARER_BATCHING_DESC_V2, callable, labeledIntList, settings, EXECUTOR)) {

      final Callable<Void> addElement =
          new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              int counter = 0;
              while (!isDuplicateElement.get() && counter < 10_000) {
                batcherTest.add(counter++);
              }
              return null;
            }
          };
      final Callable<Void> sendBatch =
          new Callable<Void>() {
            @Override
            public Void call() throws InterruptedException {
              batcherTest.flush();
              return null;
            }
          };

      // Started sequential element addition
      Future<Void> future = singleThreadExecutor.submit(addElement);
      for (int i = 0; !isDuplicateElement.get() && i < 3_000; i++) {
        multiThreadExecutor.submit(sendBatch);
      }

      // Closing the resources
      future.get();
      assertThat(isDuplicateElement.get()).isFalse();
      singleThreadExecutor.shutdown();
      multiThreadExecutor.shutdown();
    }
  }

  /** Validates ongoing runnable is cancelled once Batcher is GCed. */
  @Test
  public void testPushCurrentBatchRunnable() throws Exception {
    long delay = 100L;
    BatchingSettings settings =
        batchingSettings.toBuilder().setDelayThreshold(Duration.ofMillis(delay)).build();
    BatcherImpl<Integer, Integer, LabeledIntList, List<Integer>> batcher =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2, callLabeledIntSquarer, labeledIntList, settings, EXECUTOR);

    BatcherImpl.PushCurrentBatchRunnable<Integer, Integer, LabeledIntList, List<Integer>>
        pushBatchRunnable = new BatcherImpl.PushCurrentBatchRunnable<>(batcher);
    ScheduledFuture<?> onGoingRunnable =
        EXECUTOR.scheduleWithFixedDelay(pushBatchRunnable, delay, delay, TimeUnit.MILLISECONDS);
    pushBatchRunnable.setScheduledFuture(onGoingRunnable);

    boolean isExecutorCancelled = pushBatchRunnable.isCancelled();

    // ScheduledFuture should be not isCancelled yet.
    assertThat(isExecutorCancelled).isFalse();

    // Batcher present inside runnable should be GCed after following loop.
    batcher.close();
    batcher = null;
    for (int retry = 0; retry < 3; retry++) {
      System.gc();
      System.runFinalization();
      isExecutorCancelled = pushBatchRunnable.isCancelled();
      if (isExecutorCancelled) {
        break;
      }
      Thread.sleep(100L * (1L << retry));
    }
    // ScheduledFuture should be isCancelled now.
    assertThat(pushBatchRunnable.isCancelled()).isTrue();
  }

  @Test
  public void testEmptyBatchesAreNeverSent() throws Exception {
    UnaryCallable<LabeledIntList, List<Integer>> callable =
        new UnaryCallable<LabeledIntList, List<Integer>>() {
          @Override
          public ApiFuture<List<Integer>> futureCall(
              LabeledIntList request, ApiCallContext context) {
            throw new AssertionError("Should not call");
          }
        };
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2, callable, labeledIntList, batchingSettings, EXECUTOR);
    underTest.flush();
  }

  private void testElementTriggers(BatchingSettings settings) throws Exception {
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2, callLabeledIntSquarer, labeledIntList, settings, EXECUTOR);
    Future<Integer> result = underTest.add(4);
    assertThat(result.isDone()).isFalse();
    // After this element is added, the batch triggers sendBatch().
    Future<Integer> anotherResult = underTest.add(5);
    // Both the elements should be resolved now.
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(16);
    assertThat(anotherResult.isDone()).isTrue();
  }

  @Test
  public void testElementAreReleased() throws Exception {
    FlowController flowController =
        Mockito.spy(
            new FlowController(
                FlowControlSettings.newBuilder()
                    .setLimitExceededBehavior(LimitExceededBehavior.Block)
                    .setMaxOutstandingElementCount(10)
                    .setMaxOutstandingRequestBytes(100)
                    .build()));

    ArgumentCaptor<Long> reservedByteCap = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Integer> elementCap = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Long> byteCap = ArgumentCaptor.forClass(Long.class);

    Mockito.doCallRealMethod().when(flowController).reserve(reservedByteCap.capture());
    Mockito.doCallRealMethod()
        .when(flowController)
        .release(elementCap.capture(), byteCap.capture());

    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2,
            callLabeledIntSquarer,
            labeledIntList,
            batchingSettings,
            flowController,
            EXECUTOR);

    underTest.add(4);
    assertThat(reservedByteCap.getValue()).isEqualTo(1);

    underTest.add(3);
    underTest.add(4);
    underTest.add(5);
    underTest.add(6);

    underTest.flush();
    assertThat(elementCap.getValue()).isEqualTo(5);
    assertThat(byteCap.getValue()).isEqualTo(5);

    Mockito.verify(flowController, Mockito.times(5)).reserve(reservedByteCap.getValue());
    Mockito.verify(flowController).release(elementCap.getValue(), byteCap.getValue());
  }

  @Test
  public void testFlowController() throws Exception {
    BatchingSettings settings =
        batchingSettings.toBuilder().setDelayThreshold(Duration.ofMillis(100L)).build();
    FlowController flowController =
        Mockito.spy(
            new FlowController(
                FlowControlSettings.newBuilder()
                    .setLimitExceededBehavior(LimitExceededBehavior.Block)
                    .setMaxOutstandingElementCount(2)
                    .setMaxOutstandingRequestBytes(100L)
                    .build()));
    ArgumentCaptor<Integer> elementCap = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Long> byteCap = ArgumentCaptor.forClass(Long.class);
    Mockito.doCallRealMethod()
        .when(flowController)
        .release(elementCap.capture(), byteCap.capture());

    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2,
            callLabeledIntSquarer,
            labeledIntList,
            settings,
            flowController,
            EXECUTOR);

    underTest.add(10);
    Future result = underTest.add(11); // Max elementSize is 2, till here everything should be fine.
    underTest.add(12); // We expect flow control to be blocked until the auto flush triggered.

    assertThat(result.isDone()).isTrue();
    assertThat(elementCap.getValue()).isEqualTo(2); // auto flush triggers with 2 elements in
    // the batch.
    assertThat(byteCap.getValue()).isEqualTo(2);
    underTest.close();

    flowController =
        Mockito.spy(
            new FlowController(
                FlowControlSettings.newBuilder()
                    .setLimitExceededBehavior(LimitExceededBehavior.Block)
                    .setMaxOutstandingElementCount(2)
                    .setMaxOutstandingRequestBytes(100L)
                    .build()));
    elementCap = ArgumentCaptor.forClass(Integer.class);
    byteCap = ArgumentCaptor.forClass(Long.class);
    Mockito.doCallRealMethod()
        .when(flowController)
        .release(elementCap.capture(), byteCap.capture());

    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2,
            callLabeledIntSquarer,
            labeledIntList,
            settings,
            flowController,
            EXECUTOR);
    underTest.add(10);
    Future byteRes = underTest.add(11); // Max byte size is 2, so here everything should be fine.
    underTest.add(12); // We expect flow control to be blocked until the auto flush triggered.

    assertThat(byteRes.isDone()).isTrue();
    assertThat(elementCap.getValue()).isEqualTo(2); // auto flush triggers with 2 bytes in
    // the batch.
    assertThat(byteCap.getValue()).isEqualTo(2);
  }

  @Test
  public void testBatchingFlowControlExceptionRecovery() {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .setMaxOutstandingElementCount(2)
                .setMaxOutstandingRequestBytes(10)
                .build());
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2,
            callLabeledIntSquarer,
            labeledIntList,
            batchingSettings,
            flowController,
            EXECUTOR);
    Exception actualException = null;
    underTest.add(3);
    underTest.add(4);

    try {
      underTest.add(3);
    } catch (MaxOutstandingElementCountReachedException ex) {
      actualException = ex;
      assertThat(ex.getCurrentMaxBatchElementCount()).isEqualTo(2);
    }
    assertThat(actualException).isInstanceOf(MaxOutstandingElementCountReachedException.class);

    flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .setMaxOutstandingElementCount(10)
                .setMaxOutstandingRequestBytes(3)
                .build());
    underTest =
        new BatcherImpl<>(
            SQUARER_BATCHING_DESC_V2,
            callLabeledIntSquarer,
            labeledIntList,
            batchingSettings,
            flowController,
            EXECUTOR);

    underTest.add(3);
    underTest.add(4);
    underTest.add(5);
    try {
      underTest.add(3);
    } catch (MaxOutstandingRequestBytesReachedException ex) {
      actualException = ex;
      assertThat(ex.getCurrentMaxBatchBytes()).isEqualTo(3);
    }
    assertThat(actualException).isInstanceOf(MaxOutstandingRequestBytesReachedException.class);
  }
}
