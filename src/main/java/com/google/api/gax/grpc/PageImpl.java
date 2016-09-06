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

import com.google.api.gax.core.Page;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.UncheckedExecutionException;

import io.grpc.StatusRuntimeException;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;

class PageImpl<RequestT, ResponseT, ResourceT> implements Page<RequestT, ResponseT, ResourceT> {

  private final FutureCallable<RequestT, ResponseT> callable;
  private final PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;
  private final CallContext<RequestT> context;
  private ResponseT response;

  public PageImpl(
      FutureCallable<RequestT, ResponseT> callable,
      PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor,
      CallContext<RequestT> context) {
    this.callable = callable;
    this.pageDescriptor = pageDescriptor;
    this.context = context;

    // Make the API call eagerly
    this.response = getUnchecked(callable.futureCall(context));
  }

  @Override
  public Iterator<ResourceT> iterator() {
    return pageDescriptor.extractResources(response).iterator();
  }

  @Override
  public boolean hasNextPage() {
    return !getNextPageToken().equals(pageDescriptor.emptyToken());
  }

  @Override
  public Object getNextPageToken() {
    return pageDescriptor.extractNextToken(response);
  }

  @Override
  public Page<RequestT, ResponseT, ResourceT> getNextPage() {
    if (!hasNextPage()) {
      throw new NoSuchElementException(
          "Could not complete getNextPage operation: there are no more pages to retrieve.");
    }

    RequestT nextRequest = pageDescriptor.injectToken(context.getRequest(), getNextPageToken());
    return new PageImpl<>(callable, pageDescriptor, context.withRequest(nextRequest));
  }

  @Override
  public Page<RequestT, ResponseT, ResourceT> getNextPage(int pageSize) {
    if (!hasNextPage()) {
      throw new NoSuchElementException(
          "Could not complete getNextPage operation: there are no more pages to retrieve.");
    }

    RequestT nextRequest = pageDescriptor.injectToken(context.getRequest(), getNextPageToken());
    nextRequest = pageDescriptor.injectPageSize(nextRequest, pageSize);
    return new PageImpl<>(callable, pageDescriptor, context.withRequest(nextRequest));
  }

  @Override
  public int getPageElementCount() {
    return Iterators.size(iterator());
  }

  @Override
  public Iterable<Page<RequestT, ResponseT, ResourceT>> iteratePages() {
    return new Iterable<Page<RequestT, ResponseT, ResourceT>>() {
      @Override
      public Iterator<Page<RequestT, ResponseT, ResourceT>> iterator() {
        return new PageIterator<>(PageImpl.this);
      }
    };
  }

  @Override
  public RequestT getRequestObject() {
    return context.getRequest();
  }

  @Override
  public ResponseT getResponseObject() {
    return response;
  }

  static <ResponseT> ResponseT getUnchecked(Future<ResponseT> listenableFuture) {
    try {
      return Futures.getUnchecked(listenableFuture);
    } catch (UncheckedExecutionException exception) {
      Throwables.propagateIfInstanceOf(exception.getCause(), ApiException.class);
      if (exception.getCause() instanceof StatusRuntimeException) {
        StatusRuntimeException statusException = (StatusRuntimeException) exception.getCause();
        throw new ApiException(statusException, statusException.getStatus().getCode(), false);
      }
      throw exception;
    }
  }

  private static class PageIterator<RequestT, ResponseT, ResourceT>
      extends AbstractIterator<Page<RequestT, ResponseT, ResourceT>> {
    private Page<RequestT, ResponseT, ResourceT> currentPage;
    boolean firstPageFlag;

    private PageIterator(Page<RequestT, ResponseT, ResourceT> firstPage) {
      currentPage = firstPage;
      firstPageFlag = true;
    }

    @Override
    protected Page<RequestT, ResponseT, ResourceT> computeNext() {
      if (firstPageFlag) {
        firstPageFlag = false;
      } else {
        currentPage = currentPage.hasNextPage() ? currentPage.getNextPage() : null;
      }
      if (currentPage == null) {
        return endOfData();
      }
      return currentPage;
    }
  }
}
