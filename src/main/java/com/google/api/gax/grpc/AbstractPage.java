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
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterables;
import java.util.Iterator;

public abstract class AbstractPage<RequestT, ResponseT, ResourceT> implements Page<ResourceT> {

  protected interface PageFactory<RequestT, ResponseT, ResourceT, PageT> {
    PageT createPage(
        UnaryCallable<RequestT, ResponseT> callable,
        PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor,
        RequestT request,
        CallContext context,
        ResponseT response);
  }

  private final UnaryCallable<RequestT, ResponseT> callable;
  private final PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;
  private final RequestT request;
  private final CallContext context;
  private final ResponseT response;

  protected static <RequestT, ResponseT, ResourceT, PageT> PageT callApiAndCreate(
      PageFactory<RequestT, ResponseT, ResourceT, PageT> factory,
      UnaryCallable<RequestT, ResponseT> callable,
      PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor,
      RequestT request,
      CallContext context) {
    ResponseT response =
        ApiExceptions.callAndTranslateApiException(callable.futureCall(request, context));
    return factory.createPage(callable, pageDescriptor, request, context, response);
  }

  protected AbstractPage(
      UnaryCallable<RequestT, ResponseT> callable,
      PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor,
      RequestT request,
      CallContext context,
      ResponseT response) {
    this.callable = callable;
    this.pageDescriptor = pageDescriptor;
    this.request = request;
    this.context = context;
    this.response = response;
  }

  @Override
  public boolean hasNextPage() {
    return !getNextPageToken().equals(pageDescriptor.emptyToken());
  }

  @Override
  public String getNextPageToken() {
    return pageDescriptor.extractNextToken(response);
  }

  @Override
  public abstract AbstractPage<RequestT, ResponseT, ResourceT> getNextPage();

  @Override
  public Iterable<ResourceT> iterateAll() {
    return new Iterable<ResourceT>() {
      @Override
      public Iterator<ResourceT> iterator() {
        return new AllResourcesIterator();
      }
    };
  }

  @Override
  public Iterable<ResourceT> getValues() {
    return pageDescriptor.extractResources(response);
  }

  public ResponseT getResponse() {
    return response;
  }

  public RequestT getRequest() {
    return request;
  }

  public int getPageElementCount() {
    return Iterables.size(pageDescriptor.extractResources(response));
  }

  public abstract AbstractPage<RequestT, ResponseT, ResourceT> getNextPage(int pageSize);

  protected <PageT> PageT getNextPage(PageFactory<RequestT, ResponseT, ResourceT, PageT> provider) {
    if (hasNextPage()) {
      RequestT nextRequest = pageDescriptor.injectToken(request, getNextPageToken());
      return callApiAndCreate(provider, callable, pageDescriptor, nextRequest, context);
    } else {
      return null;
    }
  }

  protected <PageT> PageT getNextPage(
      PageFactory<RequestT, ResponseT, ResourceT, PageT> provider, int pageSize) {
    if (hasNextPage()) {
      RequestT nextRequest = pageDescriptor.injectToken(request, getNextPageToken());
      nextRequest = pageDescriptor.injectPageSize(nextRequest, pageSize);
      return callApiAndCreate(provider, callable, pageDescriptor, nextRequest, context);
    } else {
      return null;
    }
  }

  PagedListDescriptor<RequestT, ResponseT, ResourceT> getPageDescriptor() {
    return pageDescriptor;
  }

  protected <PageT extends AbstractPage<RequestT, ResponseT, ResourceT>> Iterable<PageT> iterate(
      final PageFactory<RequestT, ResponseT, ResourceT, PageT> provider, final PageT firstPage) {
    return new Iterable<PageT>() {
      @Override
      public Iterator<PageT> iterator() {
        return new AllPagesIterator<>(provider, firstPage);
      }
    };
  }

  private class AllResourcesIterator extends AbstractIterator<ResourceT> {
    AbstractPage<RequestT, ResponseT, ResourceT> currentPage = AbstractPage.this;
    Iterator<ResourceT> currentIterator = currentPage.getValues().iterator();

    @Override
    protected ResourceT computeNext() {
      while (true) {
        if (currentIterator.hasNext()) {
          return currentIterator.next();
        }
        currentPage = currentPage.getNextPage();
        if (currentPage == null) {
          return endOfData();
        }
        currentIterator = currentPage.getValues().iterator();
      }
    }
  }

  private class AllPagesIterator<PageT extends AbstractPage<RequestT, ResponseT, ResourceT>>
      extends AbstractIterator<PageT> {

    private final PageFactory<RequestT, ResponseT, ResourceT, PageT> provider;
    private PageT currentPage;
    private boolean computeFirst = true;

    private AllPagesIterator(
        PageFactory<RequestT, ResponseT, ResourceT, PageT> provider, PageT firstPage) {
      this.provider = Preconditions.checkNotNull(provider);
      this.currentPage = Preconditions.checkNotNull(firstPage);
    }

    @Override
    protected PageT computeNext() {
      if (computeFirst) {
        computeFirst = false;
        return currentPage;
      } else {
        currentPage = currentPage.getNextPage(provider);
        if (currentPage == null) {
          return endOfData();
        } else {
          return currentPage;
        }
      }
    }
  }
}
