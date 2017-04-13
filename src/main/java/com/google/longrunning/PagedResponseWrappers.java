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
package com.google.longrunning;

import com.google.api.gax.core.ApiFunction;
import com.google.api.gax.core.ApiFuture;
import com.google.api.gax.core.ApiFutures;
import com.google.api.gax.grpc.AbstractFixedSizeCollection;
import com.google.api.gax.grpc.AbstractPage;
import com.google.api.gax.grpc.AbstractPagedListResponse;
import com.google.api.gax.grpc.PageContext;
import com.google.protobuf.ExperimentalApi;
import java.util.List;
import javax.annotation.Generated;

// AUTO-GENERATED DOCUMENTATION AND CLASS
/**
 * Wrapper class to contain paged response types for page streaming methods. Each static class
 * inside this wrapper class is used as the return type of one of an API method that implements the
 * page streaming pattern.
 */
@Generated("by GAPIC")
@ExperimentalApi
public class PagedResponseWrappers {

  public static class ListOperationsPagedResponse
      extends AbstractPagedListResponse<
          ListOperationsRequest, ListOperationsResponse, Operation, ListOperationsPage,
          ListOperationsFixedSizeCollection> {

    public static ApiFuture<ListOperationsPagedResponse> createAsync(
        PageContext<ListOperationsRequest, ListOperationsResponse, Operation> context,
        ApiFuture<ListOperationsResponse> futureResponse) {
      ApiFuture<ListOperationsPage> futurePage =
          ListOperationsPage.createEmptyPage().createPageAsync(context, futureResponse);
      return ApiFutures.transform(
          futurePage,
          new ApiFunction<ListOperationsPage, ListOperationsPagedResponse>() {
            @Override
            public ListOperationsPagedResponse apply(ListOperationsPage input) {
              return new ListOperationsPagedResponse(input);
            }
          });
    }

    private ListOperationsPagedResponse(ListOperationsPage page) {
      super(page, ListOperationsFixedSizeCollection.createEmptyCollection());
    }
  }

  public static class ListOperationsPage
      extends AbstractPage<
          ListOperationsRequest, ListOperationsResponse, Operation, ListOperationsPage> {

    private ListOperationsPage(
        PageContext<ListOperationsRequest, ListOperationsResponse, Operation> context,
        ListOperationsResponse response) {
      super(context, response);
    }

    private static ListOperationsPage createEmptyPage() {
      return new ListOperationsPage(null, null);
    }

    @Override
    protected ListOperationsPage createPage(
        PageContext<ListOperationsRequest, ListOperationsResponse, Operation> context,
        ListOperationsResponse response) {
      return new ListOperationsPage(context, response);
    }

    @Override
    protected ApiFuture<ListOperationsPage> createPageAsync(
        PageContext<ListOperationsRequest, ListOperationsResponse, Operation> context,
        ApiFuture<ListOperationsResponse> futureResponse) {
      return super.createPageAsync(context, futureResponse);
    }
  }

  public static class ListOperationsFixedSizeCollection
      extends AbstractFixedSizeCollection<
          ListOperationsRequest, ListOperationsResponse, Operation, ListOperationsPage,
          ListOperationsFixedSizeCollection> {

    private ListOperationsFixedSizeCollection(List<ListOperationsPage> pages, int collectionSize) {
      super(pages, collectionSize);
    }

    private static ListOperationsFixedSizeCollection createEmptyCollection() {
      return new ListOperationsFixedSizeCollection(null, 0);
    }

    @Override
    protected ListOperationsFixedSizeCollection createCollection(
        List<ListOperationsPage> pages, int collectionSize) {
      return new ListOperationsFixedSizeCollection(pages, collectionSize);
    }
  }
}
