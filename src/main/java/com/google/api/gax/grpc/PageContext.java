/*
 * Copyright 2016, Google Inc. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import java.util.Iterator;

public class PageContext<RequestT, ResponseT, ResourceT> {

  private final UnaryCallable<RequestT, ResponseT> callable;
  private final PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;
  private final RequestT request;
  private final CallContext context;
  private final ResponseT response;

  public PageContext(
      UnaryCallable<RequestT, ResponseT> callable,
      PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor,
      RequestT request,
      CallContext context) {
    this.callable = callable;
    this.pageDescriptor = pageDescriptor;
    this.request = request;
    this.context = context;

    // Make the API call eagerly
    this.response =
        ApiExceptions.callAndTranslateApiException(callable.futureCall(request, context));
  }

  public Iterator<ResourceT> getResourceIterator() {
    return pageDescriptor.extractResources(response).iterator();
  }

  public boolean hasNextPage() {
    return !getNextPageToken().equals(pageDescriptor.emptyToken());
  }

  public Object getNextPageToken() {
    return pageDescriptor.extractNextToken(response);
  }

  public PageContext<RequestT, ResponseT, ResourceT> getNextPageContext() {
    if (!hasNextPage()) {
      return null;
    }
    RequestT nextRequest = pageDescriptor.injectToken(request, getNextPageToken());
    return new PageContext<>(callable, pageDescriptor, nextRequest, context);
  }

  public PageContext<RequestT, ResponseT, ResourceT> getNextPageContext(int pageSize) {
    if (!hasNextPage()) {
      return null;
    }
    RequestT nextRequest = pageDescriptor.injectToken(request, getNextPageToken());
    nextRequest = pageDescriptor.injectPageSize(nextRequest, pageSize);
    return new PageContext<>(callable, pageDescriptor, nextRequest, context);
  }

  public int getPageElementCount() {
    return Iterators.size(getResourceIterator());
  }

  public Iterator<ResourceT> iterateAll() {
    return new ResourceTIterator();
  }

  public ResponseT getResponse() {
    return response;
  }

  public RequestT getRequest() {
    return request;
  }

  /** Package-private for use by PagedListResponseContext */
  PagedListDescriptor<RequestT, ResponseT, ResourceT> getPageDescriptor() {
    return pageDescriptor;
  }

  private class ResourceTIterator extends AbstractIterator<ResourceT> {
    PageContext<RequestT, ResponseT, ResourceT> currentPage = PageContext.this;
    Iterator<ResourceT> currentIterator = currentPage.getResourceIterator();

    @Override
    protected ResourceT computeNext() {
      while (true) {
        if (currentIterator.hasNext()) {
          return currentIterator.next();
        }
        currentPage = currentPage.getNextPageContext();
        if (currentPage == null) {
          return endOfData();
        }
        currentIterator = currentPage.getResourceIterator();
      }
    }
  }

  public interface PageFetcher<PageT> {
    PageT getNextPage(PageT currentPage);
  }

  public static class PageIterator<PageT> extends AbstractIterator<PageT> {

    private final PageFetcher<PageT> pageFetcher;
    private PageT currentPage;
    private boolean computeFirst = true;

    public PageIterator(PageFetcher<PageT> pageFetcher, PageT firstPage) {
      this.pageFetcher = Preconditions.checkNotNull(pageFetcher);
      this.currentPage = Preconditions.checkNotNull(firstPage);
    }

    @Override
    protected PageT computeNext() {
      if (computeFirst) {
        computeFirst = false;
        return currentPage;
      } else {
        currentPage = pageFetcher.getNextPage(currentPage);
        if (currentPage == null) {
          return endOfData();
        } else {
          return currentPage;
        }
      }
    }
  }
}
