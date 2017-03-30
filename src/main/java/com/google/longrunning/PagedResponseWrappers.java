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

import com.google.api.gax.grpc.AbstractPage;
import com.google.api.gax.grpc.AbstractPagedListResponse;
import com.google.api.gax.grpc.ApiExceptions;
import com.google.api.gax.grpc.CallContext;
import com.google.api.gax.grpc.PagedListDescriptor;
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

  public static class ListOperationsPagedResponse
      extends AbstractPagedListResponse<ListOperationsRequest, ListOperationsResponse, Operation> {

    private final ListOperationsPage page;

    public static ListOperationsPagedResponse callApiAndCreate(
        UnaryCallable<ListOperationsRequest, ListOperationsResponse> callable,
        PagedListDescriptor<ListOperationsRequest, ListOperationsResponse, Operation>
            pageDescriptor,
        ListOperationsRequest request,
        CallContext callContext) {
      return new ListOperationsPagedResponse(
          ListOperationsPage.callApiAndCreate(callable, pageDescriptor, request, callContext));
    }

    private ListOperationsPagedResponse(ListOperationsPage page) {
      this.page = page;
    }

    @Override
    public ListOperationsPage getPage() {
      return page;
    }

    @Override
    public Iterable<ListOperationsPage> iteratePages() {
      return new Iterable<ListOperationsPage>() {
        @Override
        public Iterator<ListOperationsPage> iterator() {
          return new AbstractPage.PageIterator<ListOperationsPage>(
              new AbstractPage.PageFetcher<ListOperationsPage>() {
                @Override
                public ListOperationsPage getNextPage(ListOperationsPage currentPage) {
                  return currentPage.getNextPage();
                }
              },
              page);
        }
      };
    }
  }

  public static class ListOperationsPage
      extends AbstractPage<ListOperationsRequest, ListOperationsResponse, Operation> {

    public static ListOperationsPage callApiAndCreate(
        UnaryCallable<ListOperationsRequest, ListOperationsResponse> callable,
        PagedListDescriptor<ListOperationsRequest, ListOperationsResponse, Operation>
            pageDescriptor,
        ListOperationsRequest request,
        CallContext context) {
      ListOperationsResponse response =
          ApiExceptions.callAndTranslateApiException(callable.futureCall(request, context));
      return new ListOperationsPage(callable, pageDescriptor, request, context, response);
    }

    private ListOperationsPage(
        UnaryCallable<ListOperationsRequest, ListOperationsResponse> callable,
        PagedListDescriptor<ListOperationsRequest, ListOperationsResponse, Operation>
            pageDescriptor,
        ListOperationsRequest request,
        CallContext context,
        ListOperationsResponse response) {
      super(callable, pageDescriptor, request, context, response);
    }

    @Override
    public ListOperationsPage getNextPage() {
      if (hasNextPage()) {
        return ListOperationsPage.callApiAndCreate(
            getCallable(), getPageDescriptor(), getNextPageRequest(), getCallContext());
      } else {
        return null;
      }
    }

    public ListOperationsPage getNextPage(int pageSize) {
      if (hasNextPage()) {
        return ListOperationsPage.callApiAndCreate(
            getCallable(), getPageDescriptor(), getNextPageRequest(pageSize), getCallContext());
      } else {
        return null;
      }
    }
  }
}
