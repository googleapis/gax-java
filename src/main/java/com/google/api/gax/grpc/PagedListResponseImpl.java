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

import com.google.api.gax.core.FixedSizeCollection;
import com.google.api.gax.core.Page;
import com.google.api.gax.core.PagedListResponse;
import com.google.api.gax.protobuf.ValidationException;
import com.google.common.collect.AbstractIterator;
import java.util.Collections;
import java.util.Iterator;

/**
 * This is an implementation of the PagedListResponse interface. It is public so that generated code
 * can extend it and add additional methods, such as resource name type iteration.
 */
public class PagedListResponseImpl<RequestT, ResponseT, ResourceT>
    implements PagedListResponse<RequestT, ResponseT, ResourceT> {

  private RequestT request;
  private PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;
  private Page<RequestT, ResponseT, ResourceT> currentPage;

  public PagedListResponseImpl(
      UnaryApiCallable<RequestT, ResponseT> callable,
      PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor,
      RequestT request,
      CallContext context) {
    this.pageDescriptor = pageDescriptor;
    this.request = request;
    this.currentPage = new PageImpl<>(callable, pageDescriptor, request, context);
  }

  @Override
  public Iterable<ResourceT> iterateAllElements() {
    return new Iterable<ResourceT>() {

      @Override
      public Iterator<ResourceT> iterator() {
        return new ResourceTIterator<>(PagedListResponseImpl.this.iteratePages());
      }
    };
  }

  @Override
  public Page<RequestT, ResponseT, ResourceT> getPage() {
    return currentPage;
  }

  @Override
  public Iterable<Page<RequestT, ResponseT, ResourceT>> iteratePages() {
    return currentPage.iteratePages();
  }

  @Override
  public Object getNextPageToken() {
    return currentPage.getNextPageToken();
  }

  @Override
  public FixedSizeCollection<ResourceT> expandToFixedSizeCollection(int collectionSize) {
    Integer requestPageSize = pageDescriptor.extractPageSize(request);
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

    return FixedSizeCollectionImpl.expandPage(getPage(), collectionSize);
  }

  @Override
  public Iterable<FixedSizeCollection<ResourceT>> iterateFixedSizeCollections(int collectionSize) {
    return expandToFixedSizeCollection(collectionSize).iterateCollections();
  }

  static class ResourceTIterator<RequestT, ResponseT, ResourceT>
      extends AbstractIterator<ResourceT> {
    Iterator<Page<RequestT, ResponseT, ResourceT>> pageIterator;
    Iterator<ResourceT> currentIterator;

    public ResourceTIterator(Iterable<Page<RequestT, ResponseT, ResourceT>> pageIterable) {
      this.pageIterator = pageIterable.iterator();
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
