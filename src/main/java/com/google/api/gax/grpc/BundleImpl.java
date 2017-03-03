/*
 * Copyright 2017, Google Inc. All rights reserved.
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

import com.google.api.gax.bundling.Bundle;
import java.util.ArrayList;
import java.util.List;

public class BundleImpl<RequestT, ResponseT> implements Bundle<BundleImpl<RequestT, ResponseT>> {
  private final List<BundledRequestIssuer<ResponseT>> requestIssuerList;

  private final BundlingDescriptor.RequestBuilder<RequestT> requestBuilder;
  private UnaryCallable<RequestT, ResponseT> callable;

  public BundleImpl(BundlingDescriptor.RequestBuilder<RequestT> requestBuilder) {
    this.requestBuilder = requestBuilder;
    this.requestIssuerList = new ArrayList<>();
  }

  public BundleImpl(
      BundlingDescriptor<RequestT, ResponseT> descriptor,
      RequestT request,
      UnaryCallable<RequestT, ResponseT> callable,
      BundlingFuture<ResponseT> bundlingFuture) {
    this.requestBuilder = descriptor.getRequestBuilder();
    this.requestIssuerList = new ArrayList<>();
    this.requestBuilder.appendRequest(request);
    this.callable = callable;
    this.requestIssuerList.add(
        new BundledRequestIssuer<>(bundlingFuture, descriptor.countElements(request)));
  }

  public RequestT getRequest() {
    return requestBuilder.build();
  }
  
  public UnaryCallable<RequestT, ResponseT> getCallable() {
    return callable;
  }

  public List<BundledRequestIssuer<ResponseT>> getRequestIssuerList() {
    return requestIssuerList;
  }

  @Override
  public void merge(BundleImpl<RequestT, ResponseT> bundle) {
    requestBuilder.appendRequest(bundle.getRequest());
    requestIssuerList.addAll(bundle.requestIssuerList);
    if (this.callable == null) {
      this.callable = bundle.callable;
    }
  }

  @Override
  public long getMergedRequestCount() {
    return requestIssuerList.size();
  }
}
