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
package com.google.api.gax.grpc;

import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakeSimpleApi.StashCallable;
import com.google.common.truth.Truth;
import io.grpc.CallOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link UnaryCallable}. */
@RunWith(JUnit4.class)
public class GrpcUnaryCallableTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void simpleCall() throws Exception {
    StashCallable<Integer, Integer> stashCallable = new StashCallable<>(1);

    Integer response = stashCallable.call(2, GrpcCallContext.createDefault());
    Truth.assertThat(response).isEqualTo(Integer.valueOf(1));
    GrpcCallContext grpcCallContext = (GrpcCallContext) stashCallable.getContext();
    Truth.assertThat(grpcCallContext.getChannel()).isNull();
    Truth.assertThat(grpcCallContext.getCallOptions()).isEqualTo(CallOptions.DEFAULT);
  }
}
@RunWith(JUnit4.class)
public class RetryingTest {
  @SuppressWarnings("unchecked")
  private GrpcUnaryCallableImpl<Integer, Integer> callInt =
      Mockito.mock(GrpcUnaryCallableImpl.class);

  private RecordingScheduler executor;
  private FakeApiClock fakeClock;
  private ClientContext clientContext;

  private static final RetrySettings FAST_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(2L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofMillis(2L))
          .setInitialRpcTimeout(Duration.ofMillis(2L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofMillis(2L))
          .setTotalTimeout(Duration.ofMillis(10L))
          .build();

  @Before
  public void resetClock() {
    fakeClock = new FakeApiClock(System.nanoTime());
    executor = RecordingScheduler.create(fakeClock);
    clientContext = ClientContext.newBuilder().setExecutor(executor).setClock(fakeClock).build();
  }

  @After
  public void teardown() {
    executor.shutdownNow();
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  static <V> ApiFuture<V> immediateFailedFuture(Throwable t) {
    return ApiFutures.<V>immediateFailedFuture(t);
  }

  @Test
  public void retry() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNAVAILABLE));
    Throwable throwable = Status.UNAVAILABLE.asException();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test(expected = ApiException.class)
  public void retryTotalTimeoutExceeded() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNAVAILABLE));
    Throwable throwable = Status.UNAVAILABLE.asException();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings =
        FAST_RETRY_SETTINGS
            .toBuilder()
            .setInitialRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .setMaxRetryDelay(Duration.ofMillis(Integer.MAX_VALUE))
            .build();
    SimpleCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test(expected = ApiException.class)
  public void retryMaxAttemptsExeeded() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNAVAILABLE));
    Throwable throwable = Status.UNAVAILABLE.asException();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings = FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(2).build();
    SimpleCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryWithinMaxAttempts() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNAVAILABLE));
    Throwable throwable = Status.UNAVAILABLE.asException();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    RetrySettings retrySettings = FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(3).build();
    SimpleCallSettings<Integer, Integer> callSettings = createSettings(retryable, retrySettings);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnStatusUnknown() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNKNOWN));
    Throwable throwable = Status.UNKNOWN.asException();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));
    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnUnexpectedException() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNKNOWN));
    Throwable throwable = new RuntimeException("foobar");
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(throwable));
    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryNoRecover() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNAVAILABLE));
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                Status.FAILED_PRECONDITION.withDescription("foobar").asException()))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));
    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
  }

  @Test
  public void retryKeepFailing() {
    thrown.expect(UncheckedExecutionException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNAVAILABLE));
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                Status.UNAVAILABLE.withDescription("foobar").asException()));
    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    // Need to advance time inside the call.
    ApiFuture<Integer> future = callable.futureCall(1);
    Futures.getUnchecked(future);
  }

  @Test
  public void noSleepOnRetryTimeout() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(
            GrpcStatusCode.of(Status.Code.UNAVAILABLE),
            GrpcStatusCode.of(Status.Code.DEADLINE_EXCEEDED));
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                Status.DEADLINE_EXCEEDED.withDescription("DEADLINE_EXCEEDED").asException()))
        .thenReturn(ApiFutures.<Integer>immediateFuture(2));

    SimpleCallSettings<Integer, Integer> callSettings =
        createSettings(retryable, FAST_RETRY_SETTINGS);
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    callable.call(1);
    Truth.assertThat(executor.getSleepDurations().size()).isEqualTo(1);
    Truth.assertThat(executor.getSleepDurations().get(0))
        .isEqualTo(ApiResultRetryAlgorithm.DEADLINE_SLEEP_DURATION);
  }

  @Test
  public void testKnownStatusCode() {
    ImmutableSet<StatusCode> retryable =
        ImmutableSet.<StatusCode>of(GrpcStatusCode.of(Status.Code.UNAVAILABLE));
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(
            RetryingTest.<Integer>immediateFailedFuture(
                Status.FAILED_PRECONDITION.withDescription("known").asException()));
    SimpleCallSettings<Integer, Integer> callSettings =
        SimpleCallSettings.<Integer, Integer>newBuilder().setRetryableCodes(retryable).build();
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    try {
      callable.call(1);
    } catch (GrpcApiException exception) {
      Truth.assertThat(exception.getStatusCode().getCode())
          .isEqualTo(Status.Code.FAILED_PRECONDITION);
      Truth.assertThat(exception.getMessage())
          .isEqualTo("io.grpc.StatusException: FAILED_PRECONDITION: known");
    }
  }

  @Test
  public void testUnknownStatusCode() {
    ImmutableSet<StatusCode> retryable = ImmutableSet.<StatusCode>of();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(RetryingTest.<Integer>immediateFailedFuture(new RuntimeException("unknown")));
    SimpleCallSettings<Integer, Integer> callSettings =
        SimpleCallSettings.<Integer, Integer>newBuilder().setRetryableCodes(retryable).build();
    UnaryCallable<Integer, Integer> callable =
        GrpcCallableFactory.create(callInt, callSettings, clientContext);
    try {
      callable.call(1);
    } catch (GrpcApiException exception) {
      Truth.assertThat(exception.getStatusCode().getCode()).isEqualTo(Status.Code.UNKNOWN);
      Truth.assertThat(exception.getMessage()).isEqualTo("java.lang.RuntimeException: unknown");
    }
  }

  public static SimpleCallSettings<Integer, Integer> createSettings(
      Set<StatusCode> retryableCodes, RetrySettings retrySettings) {
    return SimpleCallSettings.<Integer, Integer>newBuilder()
        .setRetryableCodes(retryableCodes)
        .setRetrySettings(retrySettings)
        .build();
  }
}
@RunWith(JUnit4.class)
public class PagingTest {

  @SuppressWarnings("unchecked")
  GrpcUnaryCallableImpl<Integer, List<Integer>> callIntList =
      Mockito.mock(GrpcUnaryCallableImpl.class);

  @Test
  public void paged() {
    ArgumentCaptor<Integer> requestCapture = ArgumentCaptor.forClass(Integer.class);
    Mockito.when(callIntList.futureCall(requestCapture.capture(), (ApiCallContext) Mockito.any()))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(0, 1, 2)))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(3, 4)))
        .thenReturn(ApiFutures.immediateFuture(Collections.<Integer>emptyList()));
    UnaryCallable<Integer, ListIntegersPagedResponse> callable =
        GrpcCallableFactory.createPagedVariant(
            callIntList,
            PagedCallSettings.newBuilder(new ListIntegersPagedResponseFactory()).build(),
            ClientContext.newBuilder().build());
    Truth.assertThat(ImmutableList.copyOf(callable.call(0).iterateAll()))
        .containsExactly(0, 1, 2, 3, 4)
        .inOrder();
    Truth.assertThat(requestCapture.getAllValues()).containsExactly(0, 2, 4).inOrder();
  }

  @Test
  public void pagedByPage() {
    ArgumentCaptor<Integer> requestCapture = ArgumentCaptor.forClass(Integer.class);
    Mockito.when(callIntList.futureCall(requestCapture.capture(), (ApiCallContext) Mockito.any()))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(0, 1, 2)))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(3, 4)))
        .thenReturn(ApiFutures.immediateFuture(Collections.<Integer>emptyList()));

    Page<Integer> page =
        GrpcCallableFactory.createPagedVariant(
                callIntList,
                PagedCallSettings.newBuilder(new ListIntegersPagedResponseFactory()).build(),
                ClientContext.newBuilder().build())
            .call(0)
            .getPage();

    Truth.assertThat(page.getValues()).containsExactly(0, 1, 2).inOrder();
    Truth.assertThat(page.hasNextPage()).isTrue();

    page = page.getNextPage();
    Truth.assertThat(page.getValues()).containsExactly(3, 4).inOrder();
    Truth.assertThat(page.hasNextPage()).isTrue();

    page = page.getNextPage();
    Truth.assertThat(page.getValues()).isEmpty();
    Truth.assertThat(page.hasNextPage()).isFalse();
    Truth.assertThat(page.getNextPage()).isNull();
    Truth.assertThat(requestCapture.getAllValues()).containsExactly(0, 2, 4).inOrder();
  }

  @Test
  public void pagedByFixedSizeCollection() {
    ArgumentCaptor<Integer> requestCapture = ArgumentCaptor.forClass(Integer.class);
    Mockito.when(callIntList.futureCall(requestCapture.capture(), (ApiCallContext) Mockito.any()))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(0, 1, 2)))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(3, 4)))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(5, 6, 7)))
        .thenReturn(ApiFutures.immediateFuture(Collections.<Integer>emptyList()));
    FixedSizeCollection<Integer> fixedSizeCollection =
        GrpcCallableFactory.createPagedVariant(
                callIntList,
                PagedCallSettings.newBuilder(new ListIntegersPagedResponseFactory()).build(),
                ClientContext.newBuilder().build())
            .call(0)
            .expandToFixedSizeCollection(5);

    Truth.assertThat(fixedSizeCollection.getValues()).containsExactly(0, 1, 2, 3, 4).inOrder();
    Truth.assertThat(fixedSizeCollection.getNextCollection().getValues())
        .containsExactly(5, 6, 7)
        .inOrder();
    Truth.assertThat(requestCapture.getAllValues()).containsExactly(0, 2, 4, 7).inOrder();
  }

  @Test(expected = ValidationException.class)
  public void pagedFixedSizeCollectionTooManyElements() {
    Mockito.when(callIntList.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(0, 1, 2)))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(3, 4)))
        .thenReturn(ApiFutures.immediateFuture(Collections.<Integer>emptyList()));

    GrpcCallableFactory.createPagedVariant(
            callIntList,
            PagedCallSettings.newBuilder(new ListIntegersPagedResponseFactory()).build(),
            ClientContext.newBuilder().build())
        .call(0)
        .expandToFixedSizeCollection(4);
  }

  @Test(expected = ValidationException.class)
  public void pagedFixedSizeCollectionTooSmallCollectionSize() {
    Mockito.when(callIntList.futureCall((Integer) Mockito.any(), (ApiCallContext) Mockito.any()))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(0, 1)))
        .thenReturn(ApiFutures.immediateFuture(Collections.<Integer>emptyList()));

    GrpcCallableFactory.createPagedVariant(
            callIntList,
            PagedCallSettings.newBuilder(new ListIntegersPagedResponseFactory()).build(),
            ClientContext.newBuilder().build())
        .call(0)
        .expandToFixedSizeCollection(2);
  }
}
@RunWith(JUnit4.class)
public class BatchingTest {

  private ScheduledExecutorService batchingExecutor;

  @Before
  public void startBatchingExecutor() {
    batchingExecutor = new ScheduledThreadPoolExecutor(1);
  }

  @After
  public void teardown() {
    batchingExecutor.shutdownNow();
  }

  @Test
  public void batching() throws Exception {
    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder()
            .setDelayThreshold(Duration.ofSeconds(1))
            .setElementCountThreshold(2L)
            .build();
    BatchingCallSettings<LabeledIntList, List<Integer>> batchingCallSettings =
        BatchingCallSettings.newBuilder(SQUARER_BATCHING_DESC)
            .setBatchingSettings(batchingSettings)
            .build();
    UnaryCallable<LabeledIntList, List<Integer>> callable =
        GrpcCallableFactory.create(
            callLabeledIntSquarer, batchingCallSettings, ClientContext.newBuilder().build());
    ApiFuture<List<Integer>> f1 = callable.futureCall(new LabeledIntList("one", 1, 2));
    ApiFuture<List<Integer>> f2 = callable.futureCall(new LabeledIntList("one", 3, 4));
    Truth.assertThat(f1.get()).isEqualTo(Arrays.asList(1, 4));
    Truth.assertThat(f2.get()).isEqualTo(Arrays.asList(9, 16));
  }

  @Test
  public void batchingWithFlowControl() throws Exception {
    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder()
            .setDelayThreshold(Duration.ofSeconds(1))
            .setElementCountThreshold(4L)
            .setFlowControlSettings(
                FlowControlSettings.newBuilder()
                    .setLimitExceededBehavior(LimitExceededBehavior.Block)
                    .setMaxOutstandingElementCount(10L)
                    .setMaxOutstandingRequestBytes(10L)
                    .build())
            .build();
    TrackedFlowController trackedFlowController =
        new TrackedFlowController(batchingSettings.getFlowControlSettings());

    Truth.assertThat(trackedFlowController.getElementsReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getElementsReleased()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReleased()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getCallsToReserve()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getCallsToRelease()).isEqualTo(0);

    LabeledIntList requestA = new LabeledIntList("one", 1, 2);
    LabeledIntList requestB = new LabeledIntList("one", 3, 4);

    BatchingCallSettings<LabeledIntList, List<Integer>> batchingCallSettings =
        BatchingCallSettings.newBuilder(SQUARER_BATCHING_DESC)
            .setBatchingSettings(batchingSettings)
            .setFlowController(trackedFlowController)
            .build();
    BatchingCreateResult<LabeledIntList, List<Integer>> batchingCreateResult =
        GrpcCallableFactory.internalCreate(
            callLabeledIntSquarer,
            batchingCallSettings,
            ClientContext.newBuilder().setExecutor(batchingExecutor).build());
    ApiFuture<List<Integer>> f1 = batchingCreateResult.unaryCallable.futureCall(requestA);
    ApiFuture<List<Integer>> f2 = batchingCreateResult.unaryCallable.futureCall(requestB);
    Truth.assertThat(f1.get()).isEqualTo(Arrays.asList(1, 4));
    Truth.assertThat(f2.get()).isEqualTo(Arrays.asList(9, 16));

    batchingCreateResult
        .batcherFactory
        .getPushingBatcher(SQUARER_BATCHING_DESC.getBatchPartitionKey(requestA))
        .pushCurrentBatch()
        .get();

    // Check that the number of bytes is correct even when requests are merged, and the merged
    // request consumes fewer bytes.
    Truth.assertThat(trackedFlowController.getElementsReserved()).isEqualTo(4);
    Truth.assertThat(trackedFlowController.getElementsReleased()).isEqualTo(4);
    Truth.assertThat(trackedFlowController.getBytesReserved()).isEqualTo(8);
    Truth.assertThat(trackedFlowController.getBytesReleased()).isEqualTo(8);
    Truth.assertThat(trackedFlowController.getCallsToReserve()).isEqualTo(2);
    Truth.assertThat(trackedFlowController.getCallsToRelease()).isEqualTo(1);
  }

  @Test
  public void batchingDisabled() throws Exception {
    BatchingSettings batchingSettings = BatchingSettings.newBuilder().setIsEnabled(false).build();

    BatchingCallSettings<LabeledIntList, List<Integer>> batchingCallSettings =
        BatchingCallSettings.newBuilder(SQUARER_BATCHING_DESC)
            .setBatchingSettings(batchingSettings)
            .build();
    UnaryCallable<LabeledIntList, List<Integer>> callable =
        GrpcCallableFactory.create(
            callLabeledIntSquarer,
            batchingCallSettings,
            ClientContext.newBuilder().setExecutor(batchingExecutor).build());
    ApiFuture<List<Integer>> f1 = callable.futureCall(new LabeledIntList("one", 1, 2));
    ApiFuture<List<Integer>> f2 = callable.futureCall(new LabeledIntList("one", 3, 4));
    Truth.assertThat(f1.get()).isEqualTo(Arrays.asList(1, 4));
    Truth.assertThat(f2.get()).isEqualTo(Arrays.asList(9, 16));
  }

  public void batchingWithBlockingCallThreshold() throws Exception {
    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder()
            .setDelayThreshold(Duration.ofSeconds(1))
            .setElementCountThreshold(2L)
            .build();
    BatchingCallSettings<LabeledIntList, List<Integer>> batchingCallSettings =
        BatchingCallSettings.newBuilder(SQUARER_BATCHING_DESC)
            .setBatchingSettings(batchingSettings)
            .build();
    UnaryCallable<LabeledIntList, List<Integer>> callable =
        GrpcCallableFactory.create(
            callLabeledIntSquarer,
            batchingCallSettings,
            ClientContext.newBuilder().setExecutor(batchingExecutor).build());
    ApiFuture<List<Integer>> f1 = callable.futureCall(new LabeledIntList("one", 1));
    ApiFuture<List<Integer>> f2 = callable.futureCall(new LabeledIntList("one", 3));
    Truth.assertThat(f1.get()).isEqualTo(Arrays.asList(1));
    Truth.assertThat(f2.get()).isEqualTo(Arrays.asList(9));
  }

  private static GrpcUnaryCallableImpl<LabeledIntList, List<Integer>>
      callLabeledIntExceptionThrower =
          new GrpcUnaryCallableImpl<LabeledIntList, List<Integer>>() {
            @Override
            public ApiFuture<List<Integer>> futureCall(
                LabeledIntList request, GrpcCallContext context) {
              return RetryingTest.<List<Integer>>immediateFailedFuture(
                  new IllegalArgumentException("I FAIL!!"));
            }
          };

  @Test
  public void batchingException() throws Exception {
    BatchingSettings batchingSettings =
        BatchingSettings.newBuilder()
            .setDelayThreshold(Duration.ofSeconds(1))
            .setElementCountThreshold(2L)
            .build();
    BatchingCallSettings<LabeledIntList, List<Integer>> batchingCallSettings =
        BatchingCallSettings.newBuilder(SQUARER_BATCHING_DESC)
            .setBatchingSettings(batchingSettings)
            .build();
    UnaryCallable<LabeledIntList, List<Integer>> callable =
        GrpcCallableFactory.create(
            callLabeledIntExceptionThrower,
            batchingCallSettings,
            ClientContext.newBuilder().setExecutor(batchingExecutor).build());
    ApiFuture<List<Integer>> f1 = callable.futureCall(new LabeledIntList("one", 1, 2));
    ApiFuture<List<Integer>> f2 = callable.futureCall(new LabeledIntList("one", 3, 4));
    try {
      f1.get();
      Assert.fail("Expected exception from batching call");
    } catch (ExecutionException e) {
      // expected
    }
    try {
      f2.get();
      Assert.fail("Expected exception from batching call");
    } catch (ExecutionException e) {
      // expected
    }
  }
}
