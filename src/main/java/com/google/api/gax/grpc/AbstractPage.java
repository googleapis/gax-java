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

import com.google.api.gax.core.ApiFunction;
import com.google.api.gax.core.ApiFuture;
import com.google.api.gax.core.ApiFutures;
import com.google.api.gax.core.AsyncPage;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterables;
import java.util.Iterator;

public abstract class AbstractPage<
        RequestT,
        ResponseT,
        ResourceT,
        PageT extends AbstractPage<RequestT, ResponseT, ResourceT, PageT>>
    implements AsyncPage<ResourceT> {

  private final PageContext<RequestT, ResponseT, ResourceT> context;
  private final ResponseT response;

  protected AbstractPage(PageContext<RequestT, ResponseT, ResourceT> context, ResponseT response) {
    this.context = context;
    this.response = response;
  }

  protected abstract PageT createPage(
      PageContext<RequestT, ResponseT, ResourceT> context, ResponseT response);

  @Override
  public boolean hasNextPage() {
    return !getNextPageToken().equals(context.pageDescriptor().emptyToken());
  }

  @Override
  public String getNextPageToken() {
    return context.pageDescriptor().extractNextToken(response);
  }

  @Override
  public PageT getNextPage() {
    if (hasNextPage()) {
      RequestT nextRequest =
          context.pageDescriptor().injectToken(context.request(), getNextPageToken());
      ResponseT response =
          ApiExceptions.callAndTranslateApiException(
              context.callable().futureCall(context.request(), context.callContext()));
      return createPage(context.withRequest(nextRequest), response);
    } else {
      return null;
    }
  }

  @Override
  public ApiFuture<PageT> getNextPageAsync() {
    if (hasNextPage()) {
      RequestT nextRequest =
          context.pageDescriptor().injectToken(context.request(), getNextPageToken());
      final PageContext<RequestT, ResponseT, ResourceT> newContext =
          getContext().withRequest(nextRequest);
      return ApiFutures.transform(
          context.callable().futureCall(context.request(), context.callContext()),
          new ApiFunction<ResponseT, PageT>() {
            @Override
            public PageT apply(ResponseT input) {
              return createPage(newContext, input);
            }
          });
    } else {
      return ApiFutures.immediateFuture(null);
    }
  }

  public PageT getNextPage(int pageSize) {
    if (hasNextPage()) {
      RequestT nextRequest =
          context.pageDescriptor().injectToken(context.request(), getNextPageToken());
      nextRequest = context.pageDescriptor().injectPageSize(nextRequest, pageSize);
      ResponseT response =
          ApiExceptions.callAndTranslateApiException(
              context.callable().futureCall(nextRequest, context.callContext()));
      return createPage(context.withRequest(nextRequest), response);
    } else {
      return null;
    }
  }

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
    return context.pageDescriptor().extractResources(response);
  }

  public ResponseT getResponse() {
    return response;
  }

  public RequestT getRequest() {
    return context.request();
  }

  public int getPageElementCount() {
    return Iterables.size(context.pageDescriptor().extractResources(response));
  }

  PageContext<RequestT, ResponseT, ResourceT> getContext() {
    return context;
  }

  Iterable<PageT> iterate(final PageT firstPage) {
    return new Iterable<PageT>() {
      @Override
      public Iterator<PageT> iterator() {
        return new AllPagesIterator(firstPage);
      }
    };
  }

  private class AllResourcesIterator extends AbstractIterator<ResourceT> {
    private AbstractPage<RequestT, ResponseT, ResourceT, PageT> currentPage;
    private Iterator<ResourceT> currentIterator;

    private AllResourcesIterator() {
      this.currentPage = AbstractPage.this;
      this.currentIterator = this.currentPage.getValues().iterator();
    }

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

  private class AllPagesIterator extends AbstractIterator<PageT> {

    private PageT currentPage;
    private boolean computeFirst = true;

    private AllPagesIterator(PageT firstPage) {
      this.currentPage = Preconditions.checkNotNull(firstPage);
    }

    @Override
    protected PageT computeNext() {
      if (computeFirst) {
        computeFirst = false;
        return currentPage;
      } else {
        currentPage = currentPage.getNextPage();
        if (currentPage == null) {
          return endOfData();
        } else {
          return currentPage;
        }
      }
    }
  }
}
