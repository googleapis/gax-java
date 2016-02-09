/*
 * Copyright 2015, Google Inc.
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

package io.gapi.gax.grpc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;

import io.gapi.gax.bundling.BundlingThreshold;
import io.gapi.gax.bundling.BundlingThresholds;
import io.grpc.Channel;
import io.grpc.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.joda.time.Duration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
/**
 * Tests for {@link ApiCallable}.
 */
@RunWith(JUnit4.class)
public class ApiCallableTest {
  FutureCallable<Integer, Integer> callInt = Mockito.mock(FutureCallable.class);

  private static final RetryParams testRetryParams;

  static {
    BackoffParams backoff =
        BackoffParams.newBuilder()
            .setInitialDelayMillis(1)
            .setDelayMultiplier(1)
            .setMaxDelayMillis(1)
            .build();
    testRetryParams =
        RetryParams.newBuilder()
            .setRetryBackoff(backoff)
            .setTimeoutBackoff(backoff)
            .setTotalTimeout(10L)
            .build();
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
    new ApiCallable<Integer, Integer>(stash).bind(channel).futureCall(0);
    Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
  }

  // Retry
  // =====
  @Test
  public void retry() {
    Throwable t = Status.UNAVAILABLE.asException();
    Mockito.when(callInt.futureCall(Mockito.any()))
        .thenReturn(Futures.immediateFailedFuture(t))
        .thenReturn(Futures.immediateFailedFuture(t))
        .thenReturn(Futures.immediateFailedFuture(t))
        .thenReturn(Futures.immediateFuture(2));
    ApiCallable<Integer, Integer> callable = new ApiCallable<>(callInt).retrying(testRetryParams);
    Truth.assertThat(callable.call(1)).isEqualTo(2);
  }

  @Test
  public void retryNoRecover() {
    thrown.expect(UncheckedExecutionException.class);
    thrown.expectMessage("foobar");
    Mockito.when(callInt.futureCall(Mockito.any()))
        .thenReturn(
            Futures.immediateFailedFuture(
                Status.FAILED_PRECONDITION.withDescription("foobar").asException()))
        .thenReturn(Futures.immediateFuture(2));
    ApiCallable<Integer, Integer> callable = new ApiCallable<>(callInt).retrying(testRetryParams);
    callable.call(1);
  }

  @Test
  public void retryKeepFailing() {
    thrown.expect(UncheckedExecutionException.class);
    thrown.expectMessage("foobar");
    Mockito.when(callInt.futureCall(Mockito.any()))
        .thenReturn(
            Futures.immediateFailedFuture(
                Status.UNAVAILABLE.withDescription("foobar").asException()));
    ApiCallable<Integer, Integer> callable = new ApiCallable<>(callInt).retrying(testRetryParams);
    callable.call(1);
  }

  // Page streaming
  // ==============
  FutureCallable<Integer, List<Integer>> callIntList = Mockito.mock(FutureCallable.class);

  private class StreamingDescriptor implements PageDescriptor<Integer, List<Integer>, Integer> {
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
    Mockito.when(callIntList.futureCall(Mockito.any()))
        .thenReturn(Futures.immediateFuture(Lists.newArrayList(0, 1, 2)))
        .thenReturn(Futures.immediateFuture(Lists.newArrayList(3, 4)))
        .thenReturn(Futures.immediateFuture(Collections.emptyList()));
    Truth.assertThat(
            new ApiCallable<Integer, List<Integer>>(callIntList)
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
            result.add(i*i);
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
        public void splitResponse(List<Integer> bundleResponse,
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
        public void splitException(Throwable throwable,
            Collection<? extends RequestIssuer<LabeledIntList, List<Integer>>> bundle) {
          for (RequestIssuer<LabeledIntList, List<Integer>> responder : bundle) {
            responder.setException(throwable);
          }
        }
      };

  private <RequestT, ResponseT> BundlingSettings<RequestT, ResponseT> createBundlingSettings(int messageCountThreshold) {
    return new BundlingSettings<RequestT, ResponseT>() {
      @Override
      public Duration getDelayThreshold() {
        return Duration.standardSeconds(1);
      }
      @Override
      public ImmutableList<BundlingThreshold<BundlingContext<RequestT, ResponseT>>> getThresholds() {
        return BundlingThresholds.of(messageCountThreshold);
      }
    };
  }

  @Test
  public void bundling() throws Exception {
    BundlingSettings<LabeledIntList, List<Integer>> bundlingSettings =
        createBundlingSettings(2);
    BundlerFactory<LabeledIntList, List<Integer>> bundlerFactory =
        new BundlerFactory<>(SQUARER_BUNDLING_DESC, bundlingSettings);
    try {
      ApiCallable<LabeledIntList, List<Integer>> callable =
          new ApiCallable<>(callLabeledIntSquarer)
          .bundling(SQUARER_BUNDLING_DESC, bundlerFactory);
      ListenableFuture<List<Integer>> f1 =
          callable.futureCall(new LabeledIntList("one", 1, 2));
      ListenableFuture<List<Integer>> f2 =
          callable.futureCall(new LabeledIntList("one", 3, 4));
      Truth.assertThat(f1.get()).isEqualTo(Arrays.asList(1, 4));
      Truth.assertThat(f2.get()).isEqualTo(Arrays.asList(9, 16));
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
    BundlingSettings<LabeledIntList, List<Integer>> bundlingSettings =
        createBundlingSettings(2);
    BundlerFactory<LabeledIntList, List<Integer>> bundlerFactory =
        new BundlerFactory<>(SQUARER_BUNDLING_DESC, bundlingSettings);
    try {
      ApiCallable<LabeledIntList, List<Integer>> callable =
          new ApiCallable<>(callLabeledIntExceptionThrower)
          .bundling(SQUARER_BUNDLING_DESC, bundlerFactory);
      ListenableFuture<List<Integer>> f1 =
          callable.futureCall(new LabeledIntList("one", 1, 2));
      ListenableFuture<List<Integer>> f2 =
          callable.futureCall(new LabeledIntList("one", 3, 4));
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
}
