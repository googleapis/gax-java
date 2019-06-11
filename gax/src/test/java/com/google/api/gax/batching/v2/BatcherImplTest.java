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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakeBatchableApi.LabeledIntList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class BatcherImplTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock private UnaryCallable<LabeledIntList, List<Integer>> mockUnaryCallable;
  @Mock private BatchingDescriptor<Integer, Integer, LabeledIntList, List<Integer>> mockDescriptor;

  private Batcher<Integer, Integer> underTest;
  private LabeledIntList labeledIntList = new LabeledIntList("Default");

  /** The accumulated results in the test are resolved when {@link Batcher#flush()} is called. */
  @Test
  public void testResultsAreResolvedAfterFlush() throws Exception {
    underTest = new BatcherImpl<>(SQUARER_BATCHING_DESC_V2, callLabeledIntSquarer, labeledIntList);
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
        new BatcherImpl<>(SQUARER_BATCHING_DESC_V2, callLabeledIntSquarer, labeledIntList)) {
      result = batcher.add(5);
    }
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(25);
  }

  /** Validates exception when batch is called after {@link Batcher#close()}. */
  @Test
  public void testNoElementAdditionAfterClose() throws Exception {
    underTest = new BatcherImpl<>(SQUARER_BATCHING_DESC_V2, callLabeledIntSquarer, labeledIntList);
    underTest.close();
    Throwable actualError = null;
    try {
      underTest.add(1);
    } catch (Exception ex) {
      actualError = ex;
    }
    assertThat(actualError).isInstanceOf(IllegalStateException.class);
    assertThat(actualError.getMessage()).matches("Cannot add elements on a closed batcher");
  }

  /** Verifies unaryCallable is being called with a batch. */
  @Test
  public void testResultsAfterRPCSucceed() throws Exception {
    underTest = new BatcherImpl<>(SQUARER_BATCHING_DESC_V2, mockUnaryCallable, labeledIntList);
    when(mockUnaryCallable.futureCall(any(LabeledIntList.class)))
        .thenReturn(ApiFutures.immediateFuture(Arrays.asList(16, 25)));

    Future<Integer> result = underTest.add(4);
    Future<Integer> anotherResult = underTest.add(5);
    underTest.flush();

    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(16);
    assertThat(anotherResult.get()).isEqualTo(25);
    verify(mockUnaryCallable, times(1)).futureCall(any(LabeledIntList.class));
  }

  /** Verifies exception occurred at RPC is propagated to element results */
  @Test
  public void testResultFailureAfterRPCFailure() throws Exception {
    underTest = new BatcherImpl<>(SQUARER_BATCHING_DESC_V2, mockUnaryCallable, labeledIntList);
    final Exception fakeError = new RuntimeException();

    when(mockUnaryCallable.futureCall(any(LabeledIntList.class)))
        .thenReturn(ApiFutures.<List<Integer>>immediateFailedFuture(fakeError));

    Future<Integer> failedResult = underTest.add(5);
    underTest.flush();
    assertThat(failedResult.isDone()).isTrue();
    Throwable actualError = null;
    try {
      failedResult.get();
    } catch (InterruptedException | ExecutionException ex) {
      actualError = ex;
    }

    assertThat(actualError.getCause()).isSameAs(fakeError);
    verify(mockUnaryCallable, times(1)).futureCall(any(LabeledIntList.class));
  }

  /** Resolves future results when {@link BatchingDescriptor#splitResponse} throws exception. */
  @Test
  public void testExceptionInDescriptor() throws InterruptedException {
    underTest = new BatcherImpl<>(mockDescriptor, callLabeledIntSquarer, labeledIntList);

    final RuntimeException fakeError = new RuntimeException("internal exception");
    when(mockDescriptor.newRequestBuilder(any(LabeledIntList.class)))
        .thenReturn(SQUARER_BATCHING_DESC_V2.newRequestBuilder(labeledIntList));
    doThrow(fakeError)
        .when(mockDescriptor)
        .splitResponse(Mockito.<Integer>anyList(), Mockito.<SettableApiFuture<Integer>>anyList());
    doThrow(fakeError)
        .when(mockDescriptor)
        .splitException(Mockito.<Exception>any(), Mockito.<SettableApiFuture<Integer>>anyList());

    Future<Integer> result = underTest.add(2);
    underTest.flush();
    Throwable actualError = null;
    try {
      result.get();
    } catch (ExecutionException ex) {
      actualError = ex;
    }

    assertThat(actualError.getCause()).isSameAs(fakeError);
    verify(mockDescriptor)
        .splitResponse(Mockito.<Integer>anyList(), Mockito.<SettableApiFuture<Integer>>anyList());
  }

  /** Resolves future results when {@link BatchingDescriptor#splitException} throws exception */
  @Test
  public void testExceptionInDescriptorErrorHandling() throws InterruptedException {
    underTest = new BatcherImpl<>(mockDescriptor, mockUnaryCallable, labeledIntList);

    final RuntimeException fakeRpcError = new RuntimeException("RPC error");
    final RuntimeException fakeError = new RuntimeException("internal exception");
    when(mockUnaryCallable.futureCall(any(LabeledIntList.class)))
        .thenReturn(ApiFutures.<List<Integer>>immediateFailedFuture(fakeRpcError));
    when(mockDescriptor.newRequestBuilder(any(LabeledIntList.class)))
        .thenReturn(SQUARER_BATCHING_DESC_V2.newRequestBuilder(labeledIntList));
    doThrow(fakeError)
        .when(mockDescriptor)
        .splitException(any(Throwable.class), Mockito.<SettableApiFuture<Integer>>anyList());

    Future<Integer> result = underTest.add(2);
    underTest.flush();
    Throwable actualError = null;
    try {
      result.get();
    } catch (ExecutionException ex) {
      actualError = ex;
    }

    assertThat(actualError.getCause()).isSameAs(fakeError);
    verify(mockDescriptor)
        .splitException(any(Throwable.class), Mockito.<SettableApiFuture<Integer>>anyList());
  }
}
