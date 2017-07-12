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
package com.google.api.gax.grpc;

import com.google.api.core.ApiFutures;
import com.google.api.gax.paging.FixedSizeCollection;
import com.google.api.gax.paging.Page;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.PagedCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakePagedApi.ListIntegersPagedResponse;
import com.google.api.gax.rpc.testing.FakePagedApi.ListIntegersPagedResponseFactory;
import com.google.api.pathtemplate.ValidationException;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class PagingTest {

  @SuppressWarnings("unchecked")
  UnaryCallable<Integer, List<Integer>> callIntList = Mockito.mock(UnaryCallable.class);

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
