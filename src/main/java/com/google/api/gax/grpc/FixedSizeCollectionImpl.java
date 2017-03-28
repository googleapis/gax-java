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

import com.google.api.gax.core.FixedSizeCollection;
import com.google.api.gax.protobuf.ValidationException;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class FixedSizeCollectionImpl<ResourceT> implements FixedSizeCollection<ResourceT> {

  private List<PageContext<?, ?, ResourceT>> pageList;
  private int collectionSize;

  private FixedSizeCollectionImpl(List<PageContext<?, ?, ResourceT>> pageList, int collectionSize) {
    this.pageList = Preconditions.checkNotNull(pageList);
    Preconditions.checkState(pageList.size() > 0);
    this.collectionSize = collectionSize;
    Preconditions.checkState(collectionSize > 0);
  }

  /**
   * Construct a FixedSizeCollection from a Page object.
   *
   * <p>
   * If the collectionSize parameter is greater than the number of elements in the Page object,
   * additional pages will be retrieved from the underlying API. It is an error to choose a value of
   * collectionSize that is less that the number of elements that already exist in the Page object.
   */
  public static <ResourceT> FixedSizeCollection<ResourceT> expandPage(
      PageContext<?, ?, ResourceT> firstPage, int collectionSize) {
    if (firstPage.getPageElementCount() > collectionSize) {
      throw new ValidationException(
          "Cannot construct a FixedSizeCollection with collectionSize less than the number of "
              + "elements in the first page");
    }
    return new FixedSizeCollectionImpl<>(
        createPageArray(firstPage, collectionSize), collectionSize);
  }

  @Override
  public Iterator<ResourceT> iterator() {
    return new ResourceIterator();
  }

  @Override
  public boolean hasNextCollection() {
    return getLastPage().hasNextPage();
  }

  @Override
  public Object getNextPageToken() {
    return getLastPage().getNextPageToken();
  }

  @Override
  public int getCollectionSize() {
    int size = 0;
    for (PageContext<?, ?, ResourceT> page : pageList) {
      size += page.getPageElementCount();
    }
    return size;
  }

  @Override
  public FixedSizeCollection<ResourceT> getNextCollection() {
    if (!hasNextCollection()) {
      return null;
    }
    return FixedSizeCollectionImpl.expandPage(
        getLastPage().getNextPageContext(collectionSize), collectionSize);
  }

  @Override
  public Iterator<FixedSizeCollection<ResourceT>> iterateCollections() {
    return new FixedSizeCollectionIterator();
  }

  private PageContext<?, ?, ResourceT> getLastPage() {
    return pageList.get(pageList.size() - 1);
  }

  private static <ResourceT> List<PageContext<?, ?, ResourceT>> createPageArray(
      PageContext<?, ?, ResourceT> initialPage, int collectionSize) {
    List<PageContext<?, ?, ResourceT>> pageList = new ArrayList<>();
    pageList.add(initialPage);

    PageContext<?, ?, ResourceT> currentPage = initialPage;

    int itemCount = currentPage.getPageElementCount();
    while (itemCount < collectionSize && currentPage.hasNextPage()) {
      int remainingCount = collectionSize - itemCount;
      currentPage = currentPage.getNextPageContext(remainingCount);
      int rxElementCount = currentPage.getPageElementCount();
      if (rxElementCount > remainingCount) {
        throw new ValidationException(
            "API returned a number of elements exceeding the specified page_size limit. "
                + "page_size: "
                + collectionSize
                + ", elements received: "
                + rxElementCount);
      }
      pageList.add(currentPage);
      itemCount += rxElementCount;
    }

    return pageList;
  }

  private class ResourceIterator extends AbstractIterator<ResourceT> {

    private final Iterator<PageContext<?, ?, ResourceT>> pageIterator = pageList.iterator();
    private Iterator<ResourceT> resourceIterator = pageIterator.next().getResourceIterator();

    @Override
    protected ResourceT computeNext() {
      while (true) {
        if (resourceIterator.hasNext()) {
          return resourceIterator.next();
        } else if (pageIterator.hasNext()) {
          resourceIterator = pageIterator.next().getResourceIterator();
        } else {
          return endOfData();
        }
      }
    }
  }

  private class FixedSizeCollectionIterator
      extends AbstractIterator<FixedSizeCollection<ResourceT>> {
    private FixedSizeCollection<ResourceT> currentCollection = FixedSizeCollectionImpl.this;
    boolean firstCompute = false;

    @Override
    protected FixedSizeCollection<ResourceT> computeNext() {
      if (firstCompute) {
        firstCompute = false;
        return currentCollection;
      } else {
        currentCollection = currentCollection.getNextCollection();
        if (currentCollection == null) {
          return endOfData();
        } else {
          return currentCollection;
        }
      }
    }
  }
}
