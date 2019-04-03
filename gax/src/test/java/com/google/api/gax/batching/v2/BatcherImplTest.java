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
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakeBatchableApi.LabeledIntList;
import com.google.api.gax.rpc.testing.FakeBatchableApi.LabeledIntSquarerCallable;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class BatcherImplTest {

  private Batcher<Integer, Integer> underTest;
  private LabeledIntList labeledIntList = new LabeledIntList("Default");
  @Mock private UnaryCallable<LabeledIntList, List<Integer>> unaryCallable;
  @Mock private BatchingDescriptor<Integer, Integer, LabeledIntList, List<Integer>> mockDescriptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    underTest =
        BatcherImpl.<Integer, Integer, LabeledIntList, List<Integer>>newBuilder()
            .setPrototype(labeledIntList)
            .setUnaryCallable(unaryCallable)
            .setBatchingDescriptor(mockDescriptor)
            .build();
  }

  @After
  public void tearDown() throws Exception {
    underTest.close();
  }

  /** Verifies element result futures are resolved once RPC is completed. */
  @Test
  public void testBatchingSuccess() throws Exception {
    when(mockDescriptor.newRequestBuilder(any(LabeledIntList.class)))
        .thenReturn(SQUARER_BATCHING_DESC_V2.newRequestBuilder(labeledIntList));
    when(unaryCallable.futureCall(any(LabeledIntList.class)))
        .thenReturn(ApiFutures.immediateFuture(Collections.singletonList(16)));

    doAnswer(
            new Answer<Void>() {
              @Override
              public Void answer(InvocationOnMock invocation) {
                List<Integer> rpcResponse = invocation.getArgument(0);
                List<SettableApiFuture<Integer>> apiFutures = invocation.getArgument(1);
                apiFutures.get(0).set(rpcResponse.get(0));
                return null;
              }
            })
        .when(mockDescriptor)
        .splitResponse(Mockito.<Integer>anyList(), Mockito.<SettableApiFuture<Integer>>anyList());

    ApiFuture<Integer> result = underTest.add(4);
    underTest.flush();
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(16);

    verify(mockDescriptor, times(1)).newRequestBuilder(labeledIntList);
    verify(mockDescriptor, times(1))
        .splitResponse(Mockito.<Integer>anyList(), Mockito.<SettableApiFuture<Integer>>anyList());
    verify(unaryCallable, times(1)).futureCall(any(LabeledIntList.class));
  }

  /** Verifies exception occurred at RPC is propagated to element results */
  @Test(expected = ExecutionException.class)
  public void testBatchingFailed() throws Exception {
    final Exception exception = new RuntimeException();
    when(mockDescriptor.newRequestBuilder(any(LabeledIntList.class)))
        .thenReturn(SQUARER_BATCHING_DESC_V2.newRequestBuilder(labeledIntList));
    when(unaryCallable.futureCall(any(LabeledIntList.class)))
        .thenReturn(ApiFutures.<List<Integer>>immediateFailedFuture(exception));
    doAnswer(
            new Answer<Void>() {
              @Override
              public Void answer(InvocationOnMock invocation) {
                List<SettableApiFuture<Integer>> apiFutures = invocation.getArgument(1);
                apiFutures.get(0).setException(exception);
                return null;
              }
            })
        .when(mockDescriptor)
        .splitException(any(Throwable.class), Mockito.<SettableApiFuture<Integer>>anyList());
    ApiFuture<Integer> failedResult = underTest.add(5);
    underTest.flush();
    verify(mockDescriptor, times(1)).newRequestBuilder(labeledIntList);

    verify(mockDescriptor, times(1))
        .splitException(any(Throwable.class), Mockito.<SettableApiFuture<Integer>>anyList());
    verify(unaryCallable, times(1)).futureCall(any(LabeledIntList.class));
    assertThat(failedResult.isDone()).isTrue();
    try {
      failedResult.get();
    } catch (InterruptedException | ExecutionException e) {
      Truth.assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
      throw e;
    }
  }

  /** Tests accumulated element are resolved when {@link Batcher#flush()} is called. */
  @Test
  public void testBatchingWithCallable() throws Exception {
    underTest = newBatcherInstance();
    int limit = 100;
    int batch = 10;
    List<ApiFuture<Integer>> resultList = new ArrayList<>(limit);
    for (int i = 0; i <= limit; i++) {
      resultList.add(underTest.add(i));
      if (i % batch == 0) {
        underTest.flush();
        for (int j = i - batch; j >= 0 && j < i; j++) {
          Truth.assertThat(resultList.get(j).isDone()).isTrue();
          Truth.assertThat(resultList.get(j).get()).isEqualTo(j * j);
        }
      }
    }
  }

  /** Element results are resolved after batch is closed. */
  @Test
  public void testBatcherClose() throws Exception {
    ApiFuture<Integer> result;
    try (Batcher<Integer, Integer> batcher = newBatcherInstance()) {
      result = batcher.add(5);
    }
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(25);
  }

  /** Validates exception when batch is called after {@link Batcher#close()}. */
  @Test(expected = IllegalStateException.class)
  public void testWhenNoElementAdded() throws Exception {
    underTest.close();
    underTest.add(1);
  }

  private Batcher<Integer, Integer> newBatcherInstance() {
    return BatcherImpl.<Integer, Integer, LabeledIntList, List<Integer>>newBuilder()
        .setPrototype(labeledIntList)
        .setUnaryCallable(new LabeledIntSquarerCallable())
        .setBatchingDescriptor(SQUARER_BATCHING_DESC_V2)
        .build();
  }
}
