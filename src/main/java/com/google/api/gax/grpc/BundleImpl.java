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
  private final BundlingDescriptor<RequestT, ResponseT> descriptor;
  private final List<BundledRequestIssuer<ResponseT>> requestIssuerList;

  private RequestT request;
  private UnaryCallable<RequestT, ResponseT> callable;

  public BundleImpl(BundlingDescriptor<RequestT, ResponseT> descriptor) {
    this.descriptor = descriptor;
    this.requestIssuerList = new ArrayList<>();
  }

  public BundleImpl(
      BundlingDescriptor<RequestT, ResponseT> descriptor,
      RequestT request,
      UnaryCallable<RequestT, ResponseT> callable,
      BundlingFuture<ResponseT> bundlingFuture) {
    this.descriptor = descriptor;
    this.request = request;
    this.callable = callable;
    this.requestIssuerList = new ArrayList<>();
    this.requestIssuerList.add(
        new BundledRequestIssuer<>(bundlingFuture, descriptor.countElements(request)));
  }

  public RequestT getRequest() {
    return request;
  }

  public ResponseT call() {
    return callable.call(request);
  }

  public void splitResponse(ResponseT bundleResponse) {
    descriptor.splitResponse(bundleResponse, requestIssuerList);
  }

  public void splitException(Throwable throwable) {
    descriptor.splitException(throwable, requestIssuerList);
  }

  public void sendResults() {
    for (BundledRequestIssuer<ResponseT> requestIssuer : requestIssuerList) {
      requestIssuer.sendResult();
    }
  }

  @Override
  public void merge(BundleImpl<RequestT, ResponseT> bundle) {
    RequestT newRequest = bundle.request;
    if (this.request == null) {
      this.request = newRequest;
      this.callable = bundle.callable;
    } else {
      descriptor.updateRequest(this.request, newRequest);
    }
    requestIssuerList.addAll(bundle.requestIssuerList);
  }

  @Override
  public long getMergedRequestCount() {
    return requestIssuerList.size();
  }
}
