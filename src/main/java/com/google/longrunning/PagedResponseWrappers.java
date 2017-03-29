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

import com.google.api.gax.core.FixedSizeCollection;
import com.google.api.gax.core.Page;
import com.google.api.gax.core.PagedListResponse;
import com.google.api.gax.grpc.CallContext;
import com.google.api.gax.grpc.PageContext;
import com.google.api.gax.grpc.PagedListDescriptor;
import com.google.api.gax.grpc.PagedListResponseContext;
import com.google.api.gax.grpc.UnaryCallable;
import com.google.protobuf.ExperimentalApi;
import java.util.Iterator;
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

  public static class ListOperationsPagedResponse implements PagedListResponse<Operation> {

    private final PagedListResponseContext<ListOperationsRequest, ListOperationsResponse, Operation>
        context;
    private final ListOperationsPage page;

    public ListOperationsPagedResponse(
        UnaryCallable<ListOperationsRequest, ListOperationsResponse> callable,
        PagedListDescriptor<ListOperationsRequest, ListOperationsResponse, Operation>
            pageDescriptor,
        ListOperationsRequest request,
        CallContext callContext) {
      this.context = new PagedListResponseContext<>(callable, pageDescriptor, request, callContext);
      this.page = new ListOperationsPage(this.context);
    }

    public Iterable<Operation> iterateAll() {
      return context.iterateAll();
    }

    public Page<Operation> getPage() {
      return page;
    }

    public Iterable<ListOperationsPage> iteratePages() {
      return new Iterable<ListOperationsPage>() {
        @Override
        public Iterator<ListOperationsPage> iterator() {
          return new PageContext.PageIterator<ListOperationsPage>(
              new PageContext.PageFetcher<ListOperationsPage>() {
                @Override
                public ListOperationsPage getNextPage(ListOperationsPage currentPage) {
                  return currentPage.getNextPage();
                }
              },
              page);
        }
      };
    }

    public String getNextPageToken() {
      return context.getNextPageToken();
    }

    public FixedSizeCollection<Operation> expandToFixedSizeCollection(int collectionSize) {
      return context.expandToFixedSizeCollection(collectionSize);
    }

    public Iterable<FixedSizeCollection<Operation>> iterateFixedSizeCollections(
        int collectionSize) {
      return context.iterateFixedSizeCollections(collectionSize);
    }
  }

  public static class ListOperationsPage implements Page<Operation> {
    private final PageContext<ListOperationsRequest, ListOperationsResponse, Operation> context;

    public ListOperationsPage(
        PageContext<ListOperationsRequest, ListOperationsResponse, Operation> context) {
      this.context = context;
    }

    @Override
    public Iterator<Operation> iterator() {
      return context.getResourceIterable().iterator();
    }

    @Override
    public boolean hasNextPage() {
      return context.hasNextPage();
    }

    @Override
    public String getNextPageToken() {
      return context.getNextPageToken();
    }

    @Override
    public ListOperationsPage getNextPage() {
      return new ListOperationsPage(context.getNextPageContext());
    }

    public ListOperationsPage getNextPage(int pageSize) {
      return new ListOperationsPage(context.getNextPageContext(pageSize));
    }

    @Override
    public Iterable<Operation> iterateAll() {
      return context.iterateAll();
    }

    public ListOperationsResponse getResponse() {
      return context.getResponse();
    }

    public ListOperationsRequest getRequest() {
      return context.getRequest();
    }
  }
}
