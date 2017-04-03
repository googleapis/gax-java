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

import com.google.api.client.util.Lists;
import com.google.api.gax.core.FixedSizeCollection;
import com.google.api.gax.grpc.AbstractPage.PageFactory;
import com.google.api.gax.protobuf.ValidationException;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractFixedSizeCollection<RequestT, ResponseT, ResourceT>
    implements FixedSizeCollection<ResourceT> {

  private List<? extends AbstractPage<RequestT, ResponseT, ResourceT>> pageList;
  private int collectionSize;

  protected AbstractFixedSizeCollection(
      Iterable<? extends AbstractPage<RequestT, ResponseT, ResourceT>> pages, int collectionSize) {
    Preconditions.checkState(collectionSize > 0);
    this.pageList = Lists.newArrayList(Preconditions.checkNotNull(pages));
    Preconditions.checkState(pageList.size() > 0);
    this.collectionSize = collectionSize;
  }

  @Override
  public Iterable<ResourceT> getValues() {
    return new Iterable<ResourceT>() {
      @Override
      public Iterator<ResourceT> iterator() {
        return new ResourceIterator();
      }
    };
  }

  @Override
  public boolean hasNextCollection() {
    return getLastPage().hasNextPage();
  }

  @Override
  public String getNextPageToken() {
    return getLastPage().getNextPageToken();
  }

  @Override
  public int getCollectionSize() {
    int size = 0;
    for (AbstractPage<?, ?, ResourceT> page : pageList) {
      size += page.getPageElementCount();
    }
    return size;
  }

  protected interface CollectionProvider<CollectionT, PageT> {
    CollectionT createCollection(Iterable<PageT> pages, int collectionSize);
  }

  /**
   * Construct a FixedSizeCollection from a Page object.
   *
   * <p>
   * If the collectionSize parameter is greater than the number of elements in the Page object,
   * additional pages will be retrieved from the underlying API. It is an error to choose a value of
   * collectionSize that is less that the number of elements that already exist in the Page object.
   */
  protected static <
          RequestT,
          ResponseT,
          ResourceT,
          PageT extends AbstractPage<RequestT, ResponseT, ResourceT>,
          CollectionT>
      CollectionT expandPage(
          final CollectionProvider<CollectionT, PageT> collectionProvider,
          final PageFactory<RequestT, ResponseT, ResourceT, PageT> provider,
          final PageT page,
          final int collectionSize) {
    Integer requestPageSize = page.getPageDescriptor().extractPageSize(page.getRequest());
    if (requestPageSize == null) {
      throw new ValidationException(
          "Error while expanding Page to FixedSizeCollection: No pageSize "
              + "parameter found. The pageSize parameter must be set on the request "
              + "object, and must be less than the collectionSize "
              + "parameter, in order to create a FixedSizeCollection object.");
    }
    if (requestPageSize > collectionSize) {
      throw new ValidationException(
          "Error while expanding Page to FixedSizeCollection: collectionSize "
              + "parameter is less than the pageSize optional argument specified on "
              + "the request object. collectionSize: "
              + collectionSize
              + ", pageSize: "
              + requestPageSize);
    }
    return collectionProvider.createCollection(
        new Iterable<PageT>() {
          @Override
          public Iterator<PageT> iterator() {
            return new AbstractFixedSizeCollection.PageCollectionIterator<>(
                provider, page, collectionSize);
          }
        },
        collectionSize);
  }

  protected <PageT extends AbstractPage<RequestT, ResponseT, ResourceT>> Iterable<PageT> getPages(
      final PageFactory<RequestT, ResponseT, ResourceT, PageT> provider, final PageT page) {
    return new Iterable<PageT>() {
      @Override
      public Iterator<PageT> iterator() {
        return new AbstractFixedSizeCollection.PageCollectionIterator<>(
            provider, page, collectionSize);
      }
    };
  }

  protected <PageT extends AbstractPage<RequestT, ResponseT, ResourceT>> Iterable<PageT> getPages(
      final PageFactory<RequestT, ResponseT, ResourceT, PageT> provider) {
    return getPages(provider, getLastPage().getNextPage(provider));
  }

  protected <CollectionT, PageT extends AbstractPage<RequestT, ResponseT, ResourceT>>
      CollectionT getNextCollection(
          CollectionProvider<CollectionT, PageT> collectionProvider,
          PageFactory<RequestT, ResponseT, ResourceT, PageT> pageFactory) {
    if (hasNextCollection()) {
      return collectionProvider.createCollection(getPages(pageFactory), collectionSize);
    } else {
      return null;
    }
  }

  protected AbstractPage<RequestT, ResponseT, ResourceT> getLastPage() {
    return pageList.get(pageList.size() - 1);
  }

  protected static class PageCollectionIterator<
          RequestT,
          ResponseT,
          ResourceT,
          PageT extends AbstractPage<RequestT, ResponseT, ResourceT>>
      extends AbstractIterator<PageT> {

    private final PageFactory<RequestT, ResponseT, ResourceT, PageT> pageFactory;
    private PageT currentPage;
    private final int collectionSize;
    private int remainingCount;
    private boolean computeFirst = true;

    public PageCollectionIterator(
        PageFactory<RequestT, ResponseT, ResourceT, PageT> pageFactory,
        PageT firstPage,
        int collectionSize) {
      this.pageFactory = Preconditions.checkNotNull(pageFactory);
      this.currentPage = Preconditions.checkNotNull(firstPage);
      this.collectionSize = collectionSize;
      Preconditions.checkState(collectionSize > 0);
      this.remainingCount = collectionSize - firstPage.getPageElementCount();
      if (firstPage.getPageElementCount() > collectionSize) {
        throw new ValidationException(
            "Cannot construct a FixedSizeCollection with collectionSize less than the number of "
                + "elements in the first page");
      }
    }

    @Override
    protected PageT computeNext() {
      if (computeFirst) {
        computeFirst = false;
        return currentPage;
      } else if (remainingCount <= 0) {
        return endOfData();
      } else {
        currentPage = currentPage.getNextPage(pageFactory, remainingCount);
        if (currentPage == null) {
          return endOfData();
        } else {
          int rxElementCount = currentPage.getPageElementCount();
          if (rxElementCount > remainingCount) {
            throw new ValidationException(
                "API returned a number of elements exceeding the specified page_size limit. "
                    + "page_size: "
                    + collectionSize
                    + ", elements received: "
                    + rxElementCount);
          }
          remainingCount -= rxElementCount;
          return currentPage;
        }
      }
    }
  }

  protected static <
          RequestT,
          ResponseT,
          ResourceT,
          PageT extends AbstractPage<RequestT, ResponseT, ResourceT>,
          CollectionT extends AbstractFixedSizeCollection<RequestT, ResponseT, ResourceT>>
      Iterable<CollectionT> iterate(
          final CollectionProvider<CollectionT, PageT> provider,
          final PageFactory<RequestT, ResponseT, ResourceT, PageT> pageFactory,
          final CollectionT firstCollection) {
    return new Iterable<CollectionT>() {
      @Override
      public Iterator<CollectionT> iterator() {
        return new AbstractFixedSizeCollection.CollectionIterator<>(
            provider, pageFactory, firstCollection);
      }
    };
  }

  private static class CollectionIterator<
          RequestT,
          ResponseT,
          ResourceT,
          PageT extends AbstractPage<RequestT, ResponseT, ResourceT>,
          CollectionT extends AbstractFixedSizeCollection<RequestT, ResponseT, ResourceT>>
      extends AbstractIterator<CollectionT> {

    private final CollectionProvider<CollectionT, PageT> collectionProvider;
    private final PageFactory<RequestT, ResponseT, ResourceT, PageT> provider;
    private CollectionT currentCollection;
    private boolean computeFirst = true;

    private CollectionIterator(
        CollectionProvider<CollectionT, PageT> collectionProvider,
        PageFactory<RequestT, ResponseT, ResourceT, PageT> provider,
        CollectionT firstCollection) {
      this.collectionProvider = Preconditions.checkNotNull(collectionProvider);
      this.provider = Preconditions.checkNotNull(provider);
      this.currentCollection = Preconditions.checkNotNull(firstCollection);
    }

    @Override
    protected CollectionT computeNext() {
      if (computeFirst) {
        computeFirst = false;
        return currentCollection;
      } else {
        currentCollection = currentCollection.getNextCollection(collectionProvider, provider);
        if (currentCollection == null) {
          return endOfData();
        } else {
          return currentCollection;
        }
      }
    }
  }

  private class ResourceIterator extends AbstractIterator<ResourceT> {

    private final Iterator<? extends AbstractPage<?, ?, ResourceT>> pageIterator =
        pageList.iterator();
    private Iterator<ResourceT> resourceIterator = pageIterator.next().getValues().iterator();

    @Override
    protected ResourceT computeNext() {
      while (true) {
        if (resourceIterator.hasNext()) {
          return resourceIterator.next();
        } else if (pageIterator.hasNext()) {
          resourceIterator = pageIterator.next().getValues().iterator();
        } else {
          return endOfData();
        }
      }
    }
  }
}
