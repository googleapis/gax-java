/*
 * Copyright 2015, Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Implements the page streaming functionality used in {@link ApiCallable}.
 *
 * <p>Package-private for internal use.
 */
class PageStreamingCallable<RequestT, ResponseT, ResourceT> implements FutureCallable<RequestT, Iterable<ResourceT>>{
  private final FutureCallable<RequestT, ResponseT> callable;
  private final PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;

  PageStreamingCallable(
      FutureCallable<RequestT, ResponseT> callable,
      PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    this.callable = Preconditions.checkNotNull(callable);
    this.pageDescriptor = Preconditions.checkNotNull(pageDescriptor);
  }

  @Override
  public String toString() {
    return String.format("pageStreaming(%s)", callable);
  }

  public ListenableFuture<Iterable<ResourceT>> futureCall(CallContext<RequestT> context) {
    return Futures.immediateFuture((Iterable<ResourceT>)new StreamingIterable(context));
  }

  private class StreamingIterable implements Iterable<ResourceT> {
    private final CallContext<RequestT> context;

    private StreamingIterable(CallContext<RequestT> context) {
      this.context = context;
    }

    @Override
    public Iterator<ResourceT> iterator() {
      return new StreamingIterator(context.getRequest());
    }

    private class StreamingIterator implements Iterator<ResourceT> {
      private Iterator<ResourceT> currentIter = Collections.emptyIterator();
      private RequestT nextRequest;

      private StreamingIterator(RequestT request) {
        nextRequest = request;
      }

      @Override
      public boolean hasNext() {
        if (currentIter.hasNext()) {
          return true;
        }
        if (nextRequest == null) {
          return false;
        }
        ResponseT newPage =
            Futures.getUnchecked(callable.futureCall(context.withRequest(nextRequest)));
        currentIter = pageDescriptor.extractResources(newPage).iterator();

        Object nextToken = pageDescriptor.extractNextToken(newPage);
        if (nextToken.equals(pageDescriptor.emptyToken())) {
          nextRequest = null;
        } else {
          nextRequest = pageDescriptor.injectToken(nextRequest, nextToken);
        }
        return currentIter.hasNext();
      }

      @Override
      public ResourceT next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return currentIter.next();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    }
  }
}
