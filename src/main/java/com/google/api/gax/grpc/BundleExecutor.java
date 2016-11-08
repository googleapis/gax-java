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

import com.google.api.gax.bundling.ThresholdBundleReceiver;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * A bundle receiver which uses a provided bundling descriptor to merge the items from the bundle
 * into a single request, invoke the callable from the bundling context to issue the request, split
 * the bundle response into the components matching each incoming request, and finally send the
 * result back to the listener for each request.
 *
 * <p>Package-private for internal use.
 */
class BundleExecutor<RequestT, ResponseT>
    implements ThresholdBundleReceiver<BundlingContext<RequestT, ResponseT>> {

  private final BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
  private final String partitionKey;

  public BundleExecutor(
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor, String partitionKey) {
    this.bundlingDescriptor = Preconditions.checkNotNull(bundlingDescriptor);
    this.partitionKey = Preconditions.checkNotNull(partitionKey);
  }

  @Override
  public void validateItem(BundlingContext<RequestT, ResponseT> item) {
    String itemPartitionKey = bundlingDescriptor.getBundlePartitionKey(item.getRequest());
    if (!itemPartitionKey.equals(partitionKey)) {
      String requestClassName = item.getRequest().getClass().getSimpleName();
      throw new IllegalArgumentException(
          String.format(
              "For type %s, invalid partition key: %s, should be: %s",
              requestClassName,
              itemPartitionKey,
              partitionKey));
    }
  }

  @Override
  public void processBundle(List<BundlingContext<RequestT, ResponseT>> bundle) {
    List<RequestT> requests = new ArrayList<>(bundle.size());
    for (BundlingContext<RequestT, ResponseT> message : bundle) {
      requests.add(message.getRequest());
    }
    RequestT bundleRequest = bundlingDescriptor.mergeRequests(requests);
    UnaryCallable<RequestT, ResponseT> callable = bundle.get(0).getCallable();

    try {
      ResponseT bundleResponse = callable.call(bundleRequest);
      bundlingDescriptor.splitResponse(bundleResponse, bundle);
    } catch (Throwable exception) {
      bundlingDescriptor.splitException(exception, bundle);
    }

    for (BundlingContext<RequestT, ResponseT> message : bundle) {
      message.sendResult();
    }
  }
}
