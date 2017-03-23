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

import com.google.api.gax.core.Page;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

class PageImpl<RequestT, ResponseT, ResourceT> implements Page<ResourceT> {

  private final UnaryCallable<RequestT, ResponseT> callable;
  private final PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;
  private final RequestT request;
  private final CallContext context;
  private ResponseT response;

  public PageImpl(
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
  public Page<ResourceT> getNextPage() {
    if (!hasNextPage()) {
      return null;
    }

    RequestT nextRequest = pageDescriptor.injectToken(request, getNextPageToken());
    return new PageImpl<>(callable, pageDescriptor, nextRequest, context);
  }

  @Override
  public Page<ResourceT> getNextPage(int pageSize) {
    if (!hasNextPage()) {
      return null;
    }

    RequestT nextRequest = pageDescriptor.injectToken(request, getNextPageToken());
    nextRequest = pageDescriptor.injectPageSize(nextRequest, pageSize);
    return new PageImpl<>(callable, pageDescriptor, nextRequest, context);
  }

  @Override
  public int getPageElementCount() {
    return Iterators.size(iterator());
  }

  @Override
  public Iterator<ResourceT> iterateAll() {
    return new ResourceTIterator<>(iteratePages());
  }

  @Override
  public Iterator<Page<ResourceT>> iteratePages() {
    return new PageIterator<>(this);
  }

  private static class PageIterator<ResourceT> extends AbstractIterator<Page<ResourceT>> {
    private Page<ResourceT> currentPage;
    boolean firstPageFlag;

    private PageIterator(Page<ResourceT> firstPage) {
      currentPage = firstPage;
      firstPageFlag = true;
    }

    @Override
    protected Page<ResourceT> computeNext() {
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

  static class ResourceTIterator<ResourceT> extends AbstractIterator<ResourceT> {
    Iterator<Page<ResourceT>> pageIterator;
    Iterator<ResourceT> currentIterator;

    public ResourceTIterator(Iterator<Page<ResourceT>> pageIterator) {
      this.pageIterator = pageIterator;
      this.currentIterator = Collections.emptyIterator();
    }

    @Override
    protected ResourceT computeNext() {
      if (currentIterator.hasNext()) {
        return currentIterator.next();
      }
      if (!pageIterator.hasNext()) {
        return endOfData();
      }
      currentIterator = pageIterator.next().iterator();
      return computeNext();
    }
  }
}
