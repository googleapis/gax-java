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
import com.google.api.gax.core.Page;
import com.google.api.gax.protobuf.ValidationException;
import com.google.common.collect.AbstractIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class FixedSizeCollectionImpl<RequestT, ResponseT, ResourceT>
    implements FixedSizeCollection<ResourceT> {

  private List<Page<RequestT, ResponseT, ResourceT>> pageList;
  private int collectionSize;

  private FixedSizeCollectionImpl(
      List<Page<RequestT, ResponseT, ResourceT>> pageList, int collectionSize) {
    this.pageList = pageList;
    this.collectionSize = collectionSize;
  }

  /**
   * Construct a FixedSizeCollection from a Page object.
   *
   * <p>If the collectionSize parameter is greater than the number of elements in the Page object,
   * additional pages will be retrieved from the underlying API. It is an error to choose a value of
   * collectionSize that is less that the number of elements that already exist in the Page object.
   */
  public static <RequestT, ResponseT, ResourceT> FixedSizeCollection<ResourceT> expandPage(
      Page<RequestT, ResponseT, ResourceT> firstPage, int collectionSize) {
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
    return new PagedListResponseImpl.ResourceTIterator<>(this.pageList);
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
    for (Page<RequestT, ResponseT, ResourceT> page : pageList) {
      size += page.getPageElementCount();
    }
    return size;
  }

  @Override
  public FixedSizeCollection<ResourceT> getNextCollection() {
    if (!hasNextCollection()) {
      throw new ValidationException(
          "Could not complete getNextCollection operation: "
              + "there are no more collections to retrieve.");
    }
    return FixedSizeCollectionImpl.expandPage(
        getLastPage().getNextPage(collectionSize), collectionSize);
  }

  @Override
  public Iterable<FixedSizeCollection<ResourceT>> iterateCollections() {
    return new Iterable<FixedSizeCollection<ResourceT>>() {
      @Override
      public Iterator<FixedSizeCollection<ResourceT>> iterator() {
        return new FixedSizeCollectionIterator<>(FixedSizeCollectionImpl.this);
      }
    };
  }

  private Page<RequestT, ResponseT, ResourceT> getLastPage() {
    return pageList.get(pageList.size() - 1);
  }

  private static <RequestT, ResponseT, ResourceT>
      List<Page<RequestT, ResponseT, ResourceT>> createPageArray(
          Page<RequestT, ResponseT, ResourceT> initialPage, int collectionSize) {
    List<Page<RequestT, ResponseT, ResourceT>> pageList = new ArrayList<>();
    pageList.add(initialPage);

    Page<RequestT, ResponseT, ResourceT> currentPage = initialPage;

    int itemCount = currentPage.getPageElementCount();
    while (itemCount < collectionSize && currentPage.hasNextPage()) {
      int remainingCount = collectionSize - itemCount;
      currentPage = currentPage.getNextPage(remainingCount);
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

  private static class FixedSizeCollectionIterator<ResourceT>
      extends AbstractIterator<FixedSizeCollection<ResourceT>> {
    private FixedSizeCollection<ResourceT> currentCollection;
    boolean currentCollectionHasBeenViewed;

    private FixedSizeCollectionIterator(FixedSizeCollection<ResourceT> firstCollection) {
      currentCollection = firstCollection;
      currentCollectionHasBeenViewed = false;
    }

    @Override
    protected FixedSizeCollection<ResourceT> computeNext() {
      if (currentCollection == null) {
        endOfData();
      }
      if (!currentCollectionHasBeenViewed) {
        currentCollectionHasBeenViewed = true;
        return currentCollection;
      }

      FixedSizeCollection<ResourceT> oldPage = currentCollection;

      if (oldPage.hasNextCollection()) {
        currentCollection = oldPage.getNextCollection();
      } else {
        currentCollection = null;
      }
      return oldPage;
    }
  }
}
