/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedExecutionException;

import org.joda.time.Duration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;
import org.mockito.Mockito;

import io.grpc.Channel;
import io.grpc.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.List;

/**
 * Tests for {@link ApiCallable}.
 */
@RunWith(JUnit4.class)
public class ApiCallableTest {
  FutureCallable<Integer, Integer> callInt = Mockito.mock(FutureCallable.class);

  private static final RetrySettings testRetryParams =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.millis(2L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.millis(2L))
          .setInitialRpcTimeout(Duration.millis(2L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.millis(2L))
          .setTotalTimeout(Duration.millis(10L))
          .build();

  private static final FakeNanoClock FAKE_CLOCK = new FakeNanoClock(0);
  private static final RecordingScheduler EXECUTOR = new RecordingScheduler(FAKE_CLOCK);

  @Before
  public void resetClock() {
    FAKE_CLOCK.setCurrentNanoTime(System.nanoTime());
    EXECUTOR.sleepDurations.clear();
  }

  private static class RecordingScheduler implements ApiCallable.Scheduler {
    final ArrayList<Duration> sleepDurations = new ArrayList<>();
    final FakeNanoClock clock;

    RecordingScheduler(FakeNanoClock clock) {
      this.clock = clock;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
      sleepDurations.add(new Duration(TimeUnit.MILLISECONDS.convert(delay, unit)));
      clock.setCurrentNanoTime(clock.nanoTime() + TimeUnit.NANOSECONDS.convert(delay, unit));
      MoreExecutors.newDirectExecutorService().submit(runnable);
      // OK to return null, retry doesn't use this value.
      return null;
    }
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  // Bind
  // ====
  private static class StashCallable<ReqT, RespT> implements FutureCallable<ReqT, RespT> {
    CallContext<ReqT> context;

    @Override
    public ListenableFuture<RespT> futureCall(CallContext<ReqT> context) {
      this.context = context;
      return null;
    }
  }

  @Test
  public void bind() {
    Channel channel = Mockito.mock(Channel.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>();
    ApiCallable.<Integer, Integer>create(stash).bind(channel).futureCall(0);
    Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
  }

  // Retry
  // =====

  private static class FakeNanoClock implements NanoClock {

    private volatile long currentNanoTime;

    public FakeNanoClock(long initialNanoTime) {
      currentNanoTime = initialNanoTime;
    }

    @Override
    public long nanoTime() {
      return currentNanoTime;
    }

    public void setCurrentNanoTime(long nanoTime) {
      currentNanoTime = nanoTime;
    }
  }

  @Test
  public void retry() {
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    Throwable throwable = Status.UNAVAILABLE.asException();
    Mockito.when(callInt.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFuture(2));
    ApiCallable<Integer, Integer> callable =
        ApiCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(testRetryParams, EXECUTOR, FAKE_CLOCK);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnStatusUnknown() {
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNKNOWN);
    Throwable throwable = Status.UNKNOWN.asException();
    Mockito.when(callInt.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFuture(2));
    ApiCallable<Integer, Integer> callable =
        ApiCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(testRetryParams, EXECUTOR, FAKE_CLOCK);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnUnexpectedException() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNKNOWN);
    Throwable throwable = new RuntimeException("foobar");
    Mockito.when(callInt.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable));
    ApiCallable<Integer, Integer> callable =
        ApiCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(testRetryParams, EXECUTOR, FAKE_CLOCK);
    callable.call(1);
  }

  @Test
  public void retryNoRecover() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(
            Futures.<Integer>immediateFailedFuture(
                Status.FAILED_PRECONDITION.withDescription("foobar").asException()))
        .thenReturn(Futures.immediateFuture(2));
    ApiCallable<Integer, Integer> callable =
        ApiCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(testRetryParams, EXECUTOR, FAKE_CLOCK);
    callable.call(1);
  }

  @Test
  public void retryKeepFailing() {
    thrown.expect(UncheckedExecutionException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(
            Futures.<Integer>immediateFailedFuture(
                Status.UNAVAILABLE.withDescription("foobar").asException()));
    ApiCallable<Integer, Integer> callable =
        ApiCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(testRetryParams, EXECUTOR, FAKE_CLOCK);
    // Need to advance time inside the call.
    ListenableFuture<Integer> future = callable.futureCall(1);
    Futures.getUnchecked(future);
  }

  @Test
  public void noSleepOnRetryTimeout() {
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(
            Futures.<Integer>immediateFailedFuture(
                Status.DEADLINE_EXCEEDED.withDescription("DEADLINE_EXCEEDED").asException()));
    ApiCallable<Integer, Integer> callable =
        ApiCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(testRetryParams, EXECUTOR, FAKE_CLOCK);
    ApiException gotException = null;
    try {
      callable.call(1);
    } catch (ApiException e) {
      gotException = e;
    }
    Truth.assertThat(gotException.getStatusCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
    for (Duration d : EXECUTOR.sleepDurations) {
      Truth.assertThat(d).isEqualTo(RetryingCallable.DEADLINE_SLEEP_DURATION);
    }
  }

  // Page streaming
  // ==============
  FutureCallable<Integer, List<Integer>> callIntList = Mockito.mock(FutureCallable.class);

  private class StreamingDescriptor
      implements PageStreamingDescriptor<Integer, List<Integer>, Integer> {
    @Override
    public Object emptyToken() {
      return 0;
    }

    @Override
    public Integer injectToken(Integer payload, Object token) {
      return (Integer) token;
    }

    @Override
    public Object extractNextToken(List<Integer> payload) {
      int size = payload.size();
      return size == 0 ? emptyToken() : payload.get(size - 1);
    }

    @Override
    public Iterable<Integer> extractResources(List<Integer> payload) {
      return payload;
    }
  }

  @Test
  public void pageStreaming() {
    Mockito.when(callIntList.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(0, 1, 2)))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(3, 4)))
        .thenReturn(Futures.immediateFuture(Collections.<Integer>emptyList()));
    Truth.assertThat(
            ApiCallable.<Integer, List<Integer>>create(callIntList)
                .pageStreaming(new StreamingDescriptor())
                .call(0))
        .containsExactly(0, 1, 2, 3, 4)
        .inOrder();
  }

  // Bundling
  // ========
  private static class LabeledIntList {
    public String label;
    public List<Integer> ints;

    public LabeledIntList(String label, Integer... numbers) {
      this(label, Arrays.asList(numbers));
    }

    public LabeledIntList(String label, List<Integer> ints) {
      this.label = label;
      this.ints = ints;
    }
  }

  private static FutureCallable<LabeledIntList, List<Integer>> callLabeledIntSquarer =
      new FutureCallable<LabeledIntList, List<Integer>>() {
        @Override
        public ListenableFuture<List<Integer>> futureCall(CallContext<LabeledIntList> context) {
          List<Integer> result = new ArrayList<>();
          for (Integer i : context.getRequest().ints) {
            result.add(i * i);
          }
          return Futures.immediateFuture(result);
        }
      };

  private static BundlingDescriptor<LabeledIntList, List<Integer>> SQUARER_BUNDLING_DESC =
      new BundlingDescriptor<LabeledIntList, List<Integer>>() {

        @Override
        public String getBundlePartitionKey(LabeledIntList request) {
          return request.label;
        }

        @Override
        public LabeledIntList mergeRequests(Collection<LabeledIntList> requests) {
          LabeledIntList firstRequest = requests.iterator().next();

          List<Integer> messages = new ArrayList<>();
          for (LabeledIntList request : requests) {
            messages.addAll(request.ints);
          }

          LabeledIntList bundleRequest = new LabeledIntList(firstRequest.label, messages);
          return bundleRequest;
        }

        @Override
        public void splitResponse(
            List<Integer> bundleResponse,
            Collection<? extends RequestIssuer<LabeledIntList, List<Integer>>> bundle) {
          int bundleMessageIndex = 0;
          for (RequestIssuer<LabeledIntList, List<Integer>> responder : bundle) {
            List<Integer> messageIds = new ArrayList<>();
            int messageCount = responder.getRequest().ints.size();
            for (int i = 0; i < messageCount; i++) {
              messageIds.add(bundleResponse.get(bundleMessageIndex));
              bundleMessageIndex += 1;
            }
            responder.setResponse(messageIds);
          }
        }

        @Override
        public void splitException(
            Throwable throwable,
            Collection<? extends RequestIssuer<LabeledIntList, List<Integer>>> bundle) {
          for (RequestIssuer<LabeledIntList, List<Integer>> responder : bundle) {
            responder.setException(throwable);
          }
        }

        @Override
        public long countElements(LabeledIntList request) {
          return request.ints.size();
        }

        @Override
        public long countBytes(LabeledIntList request) {
          return 0;
        }
      };

  @Test
  public void bundling() throws Exception {
    BundlingSettings bundlingSettings =
        BundlingSettings.newBuilder()
            .setDelayThreshold(Duration.standardSeconds(1))
            .setElementCountThreshold(2)
            .setBlockingCallCountThreshold(0)
            .build();
    BundlerFactory<LabeledIntList, List<Integer>> bundlerFactory =
        new BundlerFactory<>(SQUARER_BUNDLING_DESC, bundlingSettings);
    try {
      ApiCallable<LabeledIntList, List<Integer>> callable =
          ApiCallable.<LabeledIntList, List<Integer>>create(callLabeledIntSquarer)
              .bundling(SQUARER_BUNDLING_DESC, bundlerFactory);
      ListenableFuture<List<Integer>> f1 = callable.futureCall(new LabeledIntList("one", 1, 2));
      ListenableFuture<List<Integer>> f2 = callable.futureCall(new LabeledIntList("one", 3, 4));
      Truth.assertThat(f1.get()).isEqualTo(Arrays.asList(1, 4));
      Truth.assertThat(f2.get()).isEqualTo(Arrays.asList(9, 16));
    } finally {
      bundlerFactory.close();
    }
  }

  public void bundlingWithBlockingCallThreshold() throws Exception {
    BundlingSettings bundlingSettings =
        BundlingSettings.newBuilder()
            .setDelayThreshold(Duration.standardSeconds(1))
            .setElementCountThreshold(2)
            .setBlockingCallCountThreshold(1)
            .build();
    BundlerFactory<LabeledIntList, List<Integer>> bundlerFactory =
        new BundlerFactory<>(SQUARER_BUNDLING_DESC, bundlingSettings);
    try {
      ApiCallable<LabeledIntList, List<Integer>> callable =
          ApiCallable.<LabeledIntList, List<Integer>>create(callLabeledIntSquarer)
              .bundling(SQUARER_BUNDLING_DESC, bundlerFactory);
      ListenableFuture<List<Integer>> f1 = callable.futureCall(new LabeledIntList("one", 1));
      ListenableFuture<List<Integer>> f2 = callable.futureCall(new LabeledIntList("one", 3));
      Truth.assertThat(f1.get()).isEqualTo(Arrays.asList(1));
      Truth.assertThat(f2.get()).isEqualTo(Arrays.asList(9));
    } finally {
      bundlerFactory.close();
    }
  }

  private static FutureCallable<LabeledIntList, List<Integer>> callLabeledIntExceptionThrower =
      new FutureCallable<LabeledIntList, List<Integer>>() {
        @Override
        public ListenableFuture<List<Integer>> futureCall(CallContext<LabeledIntList> context) {
          return Futures.immediateFailedFuture(new IllegalArgumentException("I FAIL!!"));
        }
      };

  @Test
  public void bundlingException() throws Exception {
    BundlingSettings bundlingSettings =
        BundlingSettings.newBuilder()
            .setDelayThreshold(Duration.standardSeconds(1))
            .setElementCountThreshold(2)
            .setBlockingCallCountThreshold(0)
            .build();
    BundlerFactory<LabeledIntList, List<Integer>> bundlerFactory =
        new BundlerFactory<>(SQUARER_BUNDLING_DESC, bundlingSettings);
    try {
      ApiCallable<LabeledIntList, List<Integer>> callable =
          ApiCallable.<LabeledIntList, List<Integer>>create(callLabeledIntExceptionThrower)
              .bundling(SQUARER_BUNDLING_DESC, bundlerFactory);
      ListenableFuture<List<Integer>> f1 = callable.futureCall(new LabeledIntList("one", 1, 2));
      ListenableFuture<List<Integer>> f2 = callable.futureCall(new LabeledIntList("one", 3, 4));
      try {
        f1.get();
        Assert.fail("Expected exception from bundling call");
      } catch (ExecutionException e) {
        // expected
      }
      try {
        f2.get();
        Assert.fail("Expected exception from bundling call");
      } catch (ExecutionException e) {
        // expected
      }
    } finally {
      bundlerFactory.close();
    }
  }

  // ApiException
  // ============

  @Test
  public void testKnownStatusCode() {
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(
            Futures.<Integer>immediateFailedFuture(
                Status.FAILED_PRECONDITION.withDescription("known").asException()));
    ApiCallable<Integer, Integer> callable =
        ApiCallable.<Integer, Integer>create(callInt).retryableOn(retryable);
    try {
      callable.call(1);
    } catch (ApiException exception) {
      Truth.assertThat(exception.getStatusCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
      Truth.assertThat(exception.getMessage())
          .isEqualTo("io.grpc.StatusException: FAILED_PRECONDITION: known");
    }
  }

  @Test
  public void testUnknownStatusCode() {
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of();
    Mockito.when(callInt.futureCall((CallContext<Integer>) Mockito.any()))
        .thenReturn(Futures.<Integer>immediateFailedFuture(new RuntimeException("unknown")));
    ApiCallable<Integer, Integer> callable =
        ApiCallable.<Integer, Integer>create(callInt).retryableOn(retryable);
    try {
      callable.call(1);
    } catch (ApiException exception) {
      Truth.assertThat(exception.getStatusCode()).isEqualTo(Status.Code.UNKNOWN);
      Truth.assertThat(exception.getMessage()).isEqualTo("java.lang.RuntimeException: unknown");
    }
  }
}
