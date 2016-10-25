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

import com.google.api.gax.core.FixedSizeCollection;
import com.google.api.gax.core.Page;
import com.google.api.gax.core.RetrySettings;
import com.google.api.gax.protobuf.ValidationException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.grpc.Channel;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Tests for {@link UnaryCallable}. */
@RunWith(JUnit4.class)
public class UnaryCallableTest {
  @SuppressWarnings("unchecked")
  private FutureCallable<Integer, Integer> callInt = Mockito.mock(FutureCallable.class);

  private static final RetrySettings FAST_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.millis(2L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.millis(2L))
          .setInitialRpcTimeout(Duration.millis(2L))
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.millis(2L))
          .setTotalTimeout(Duration.millis(10L))
          .build();

  private FakeNanoClock fakeClock;
  private RecordingScheduler executor;

  @Before
  public void resetClock() {
    fakeClock = new FakeNanoClock(System.nanoTime());
    executor = new RecordingScheduler(fakeClock);
  }

  @After
  public void teardown() {
    executor.shutdownNow();
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  // Bind
  // ====
  private static class StashCallable<RequestT, ResponseT>
      implements FutureCallable<RequestT, ResponseT> {
    CallContext context;
    ResponseT result;

    public StashCallable(ResponseT result) {
      this.result = result;
    }

    @Override
    public ListenableFuture<ResponseT> futureCall(RequestT request, CallContext context) {
      this.context = context;
      return Futures.<ResponseT>immediateFuture(result);
    }
  }

  @Test
  public void bind() {
    Channel channel = Mockito.mock(Channel.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(0);
    UnaryCallable.<Integer, Integer>create(stash).bind(channel).futureCall(0);
    Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
  }

  @Test
  public void retryableBind() throws Exception {
    Channel channel = Mockito.mock(Channel.class);
    StashCallable<Integer, Integer> stash = new StashCallable<>(0);

    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(stash)
            .bind(channel)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);
    callable.call(0);
    Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
  }

  @Test
  public void pagedBind() {
    Channel channel = Mockito.mock(Channel.class);
    StashCallable<Integer, List<Integer>> stash =
        new StashCallable<Integer, List<Integer>>(new ArrayList<Integer>());

    UnaryCallable.<Integer, List<Integer>>create(stash)
        .bind(channel)
        .paged(new PagedFactory())
        .call(0);

    Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
  }

  private static BundlingDescriptor<Integer, List<Integer>> STASH_BUNDLING_DESC =
      new BundlingDescriptor<Integer, List<Integer>>() {

        @Override
        public String getBundlePartitionKey(Integer request) {
          return "";
        }

        @Override
        public Integer mergeRequests(Collection<Integer> requests) {
          return 0;
        }

        @Override
        public void splitResponse(
            List<Integer> bundleResponse,
            Collection<? extends RequestIssuer<Integer, List<Integer>>> bundle) {
          for (RequestIssuer<Integer, List<Integer>> responder : bundle) {
            responder.setResponse(new ArrayList<Integer>());
          }
        }

        @Override
        public void splitException(
            Throwable throwable,
            Collection<? extends RequestIssuer<Integer, List<Integer>>> bundle) {}

        @Override
        public long countElements(Integer request) {
          return 1;
        }

        @Override
        public long countBytes(Integer request) {
          return 0;
        }
      };

  @Test
  public void bundlingBind() throws Exception {
    BundlingSettings bundlingSettings =
        BundlingSettings.newBuilder()
            .setDelayThreshold(Duration.standardSeconds(1))
            .setElementCountThreshold(2)
            .setBlockingCallCountThreshold(0)
            .build();
    BundlerFactory<Integer, List<Integer>> bundlerFactory =
        new BundlerFactory<>(STASH_BUNDLING_DESC, bundlingSettings);
    try {
      Channel channel = Mockito.mock(Channel.class);
      StashCallable<Integer, List<Integer>> stash =
          new StashCallable<Integer, List<Integer>>(new ArrayList<Integer>());
      UnaryCallable<Integer, List<Integer>> callable =
          UnaryCallable.<Integer, List<Integer>>create(stash)
              .bind(channel)
              .bundling(STASH_BUNDLING_DESC, bundlerFactory);
      ListenableFuture<List<Integer>> future = callable.futureCall(0);
      future.get();
      Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
    } finally {
      bundlerFactory.close();
    }
  }

  // Retry
  // =====

  @Test
  public void retry() {
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    Throwable throwable = Status.UNAVAILABLE.asException();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFuture(2));
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnStatusUnknown() {
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNKNOWN);
    Throwable throwable = Status.UNKNOWN.asException();
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable))
        .thenReturn(Futures.<Integer>immediateFuture(2));
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryOnUnexpectedException() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNKNOWN);
    Throwable throwable = new RuntimeException("foobar");
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<Integer>immediateFailedFuture(throwable));
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);
    callable.call(1);
  }

  @Test
  public void retryNoRecover() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(
            Futures.<Integer>immediateFailedFuture(
                Status.FAILED_PRECONDITION.withDescription("foobar").asException()))
        .thenReturn(Futures.immediateFuture(2));
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);
    callable.call(1);
  }

  @Test
  public void retryKeepFailing() {
    thrown.expect(UncheckedExecutionException.class);
    thrown.expectMessage("foobar");
    ImmutableSet<Status.Code> retryable = ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(
            Futures.<Integer>immediateFailedFuture(
                Status.UNAVAILABLE.withDescription("foobar").asException()));
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);
    // Need to advance time inside the call.
    ListenableFuture<Integer> future = callable.futureCall(1);
    Futures.getUnchecked(future);
  }

  @Test
  public void noSleepOnRetryTimeout() {
    ImmutableSet<Status.Code> retryable =
        ImmutableSet.<Status.Code>of(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED);
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(
            Futures.<Integer>immediateFailedFuture(
                Status.DEADLINE_EXCEEDED.withDescription("DEADLINE_EXCEEDED").asException()))
        .thenReturn(Futures.<Integer>immediateFuture(2));

    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt)
            .retryableOn(retryable)
            .retrying(FAST_RETRY_SETTINGS, executor, fakeClock);
    callable.call(1);
    Truth.assertThat(executor.getSleepDurations().size()).isEqualTo(1);
    Truth.assertThat(executor.getSleepDurations().get(0))
        .isEqualTo(RetryingCallable.DEADLINE_SLEEP_DURATION);
  }

  // PagedList
  // ==============
  @SuppressWarnings("unchecked")
  FutureCallable<Integer, List<Integer>> callIntList = Mockito.mock(FutureCallable.class);

  private class StreamingDescriptor
      implements PagedListDescriptor<Integer, List<Integer>, Integer> {
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

    @Override
    public Integer injectPageSize(Integer payload, int pageSize) {
      return payload;
    }

    @Override
    public Integer extractPageSize(Integer payload) {
      return 3;
    }
  }

  private class PagedListResponse extends PagedListResponseImpl<Integer, List<Integer>, Integer> {
    public PagedListResponse(
        UnaryCallable<Integer, List<Integer>> callable,
        PagedListDescriptor<Integer, List<Integer>, Integer> pageDescriptor,
        Integer request,
        CallContext context) {
      super(callable, pageDescriptor, request, context);
    }
  }

  private class PagedFactory
      implements PagedListResponseFactory<Integer, List<Integer>, PagedListResponse> {

    private final StreamingDescriptor streamingDescriptor = new StreamingDescriptor();

    @Override
    public PagedListResponse createPagedListResponse(
        UnaryCallable<Integer, List<Integer>> callable, Integer request, CallContext context) {
      return new PagedListResponse(callable, streamingDescriptor, request, context);
    }
  }

  @Test
  public void paged() {
    Mockito.when(callIntList.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(0, 1, 2)))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(3, 4)))
        .thenReturn(Futures.immediateFuture(Collections.<Integer>emptyList()));
    Truth.assertThat(
            UnaryCallable.<Integer, List<Integer>>create(callIntList)
                .paged(new PagedFactory())
                .call(0)
                .iterateAllElements())
        .containsExactly(0, 1, 2, 3, 4)
        .inOrder();
  }

  @Test
  public void pagedByPage() {
    Mockito.when(callIntList.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(0, 1, 2)))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(3, 4)))
        .thenReturn(Futures.immediateFuture(Collections.<Integer>emptyList()));
    Page<Integer, List<Integer>, Integer> page =
        UnaryCallable.<Integer, List<Integer>>create(callIntList)
            .paged(new PagedFactory())
            .call(0)
            .getPage();

    Truth.assertThat(page).containsExactly(0, 1, 2).inOrder();
    Truth.assertThat(page.getNextPage()).containsExactly(3, 4).inOrder();
  }

  @Test
  public void pagedByFixedSizeCollection() {
    Mockito.when(callIntList.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(0, 1, 2)))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(3, 4)))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(5, 6, 7)))
        .thenReturn(Futures.immediateFuture(Collections.<Integer>emptyList()));
    FixedSizeCollection<Integer> fixedSizeCollection =
        UnaryCallable.<Integer, List<Integer>>create(callIntList)
            .paged(new PagedFactory())
            .call(0)
            .expandToFixedSizeCollection(5);

    Truth.assertThat(fixedSizeCollection).containsExactly(0, 1, 2, 3, 4).inOrder();
    Truth.assertThat(fixedSizeCollection.getNextCollection()).containsExactly(5, 6, 7).inOrder();
  }

  @Test(expected = ValidationException.class)
  public void pagedFixedSizeCollectionTooManyElements() {
    Mockito.when(callIntList.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(0, 1, 2)))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(3, 4)))
        .thenReturn(Futures.immediateFuture(Collections.<Integer>emptyList()));

    UnaryCallable.<Integer, List<Integer>>create(callIntList)
        .paged(new PagedFactory())
        .call(0)
        .expandToFixedSizeCollection(4);
  }

  @Test(expected = ValidationException.class)
  public void pagedFixedSizeCollectionTooSmallCollectionSize() {
    Mockito.when(callIntList.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<List<Integer>>immediateFuture(Lists.newArrayList(0, 1)))
        .thenReturn(Futures.immediateFuture(Collections.<Integer>emptyList()));

    UnaryCallable.<Integer, List<Integer>>create(callIntList)
        .paged(new PagedFactory())
        .call(0)
        .expandToFixedSizeCollection(2);
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
        public ListenableFuture<List<Integer>> futureCall(
            LabeledIntList request, CallContext context) {
          List<Integer> result = new ArrayList<>();
          for (Integer i : request.ints) {
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
      UnaryCallable<LabeledIntList, List<Integer>> callable =
          UnaryCallable.<LabeledIntList, List<Integer>>create(callLabeledIntSquarer)
              .bundling(SQUARER_BUNDLING_DESC, bundlerFactory);
      ListenableFuture<List<Integer>> f1 = callable.futureCall(new LabeledIntList("one", 1, 2));
      ListenableFuture<List<Integer>> f2 = callable.futureCall(new LabeledIntList("one", 3, 4));
      Truth.assertThat(f1.get()).isEqualTo(Arrays.asList(1, 4));
      Truth.assertThat(f2.get()).isEqualTo(Arrays.asList(9, 16));
    } finally {
      bundlerFactory.close();
    }
  }

  private static BundlingDescriptor<LabeledIntList, List<Integer>> DISABLED_BUNDLING_DESC =
      new BundlingDescriptor<LabeledIntList, List<Integer>>() {

        @Override
        public String getBundlePartitionKey(LabeledIntList request) {
          Assert.fail("getBundlePartitionKey should not be invoked while bundling is disabled.");
          return null;
        }

        @Override
        public LabeledIntList mergeRequests(Collection<LabeledIntList> requests) {
          Assert.fail("mergeRequests should not be invoked while bundling is disabled.");
          return null;
        }

        @Override
        public void splitResponse(
            List<Integer> bundleResponse,
            Collection<? extends RequestIssuer<LabeledIntList, List<Integer>>> bundle) {
          Assert.fail("splitResponse should not be invoked while bundling is disabled.");
        }

        @Override
        public void splitException(
            Throwable throwable,
            Collection<? extends RequestIssuer<LabeledIntList, List<Integer>>> bundle) {
          Assert.fail("splitException should not be invoked while bundling is disabled.");
        }

        @Override
        public long countElements(LabeledIntList request) {
          Assert.fail("countElements should not be invoked while bundling is disabled.");
          return 0;
        }

        @Override
        public long countBytes(LabeledIntList request) {
          Assert.fail("countBytes should not be invoked while bundling is disabled.");
          return 0;
        }
      };

  @Test
  public void bundlingDisabled() throws Exception {
    BundlingSettings bundlingSettings = BundlingSettings.newBuilder().setIsEnabled(false).build();
    BundlerFactory<LabeledIntList, List<Integer>> bundlerFactory =
        new BundlerFactory<>(DISABLED_BUNDLING_DESC, bundlingSettings);
    try {
      UnaryCallable<LabeledIntList, List<Integer>> callable =
          UnaryCallable.<LabeledIntList, List<Integer>>create(callLabeledIntSquarer)
              .bundling(DISABLED_BUNDLING_DESC, bundlerFactory);
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
      UnaryCallable<LabeledIntList, List<Integer>> callable =
          UnaryCallable.<LabeledIntList, List<Integer>>create(callLabeledIntSquarer)
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
        public ListenableFuture<List<Integer>> futureCall(
            LabeledIntList request, CallContext context) {
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
      UnaryCallable<LabeledIntList, List<Integer>> callable =
          UnaryCallable.<LabeledIntList, List<Integer>>create(callLabeledIntExceptionThrower)
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
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(
            Futures.<Integer>immediateFailedFuture(
                Status.FAILED_PRECONDITION.withDescription("known").asException()));
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt).retryableOn(retryable);
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
    Mockito.when(callInt.futureCall((Integer) Mockito.any(), (CallContext) Mockito.any()))
        .thenReturn(Futures.<Integer>immediateFailedFuture(new RuntimeException("unknown")));
    UnaryCallable<Integer, Integer> callable =
        UnaryCallable.<Integer, Integer>create(callInt).retryableOn(retryable);
    try {
      callable.call(1);
    } catch (ApiException exception) {
      Truth.assertThat(exception.getStatusCode()).isEqualTo(Status.Code.UNKNOWN);
      Truth.assertThat(exception.getMessage()).isEqualTo("java.lang.RuntimeException: unknown");
    }
  }
}
