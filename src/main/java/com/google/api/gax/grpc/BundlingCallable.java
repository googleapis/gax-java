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

import com.google.api.gax.bundling.ThresholdBundleHandle;
import com.google.api.gax.bundling.ThresholdBundlingForwarder;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * A {@link FutureCallable} which will bundle requests based on the given bundling descriptor and
 * bundler factory. The bundler factory provides a distinct bundler for each partition as specified
 * by the bundling descriptor. An example of a bundling partition would be a pubsub topic.
 *
 * <p>
 * Package-private for internal use.
 */
class BundlingCallable<RequestT, ResponseT> implements FutureCallable<RequestT, ResponseT> {
  private final FutureCallable<RequestT, ResponseT> callable;
  private final BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
  private final BundlerFactory<RequestT, ResponseT> bundlerFactory;

  public BundlingCallable(
      FutureCallable<RequestT, ResponseT> callable,
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlerFactory<RequestT, ResponseT> bundlerFactory) {
    this.callable = Preconditions.checkNotNull(callable);
    this.bundlingDescriptor = Preconditions.checkNotNull(bundlingDescriptor);
    this.bundlerFactory = Preconditions.checkNotNull(bundlerFactory);
  }

  @Override
  public ListenableFuture<ResponseT> futureCall(RequestT request, CallContext context) {
    if (bundlerFactory.getBundlingSettings().getIsEnabled()) {
      BundlingFuture<ResponseT> result = BundlingFuture.<ResponseT>create();
      UnaryCallable<RequestT, ResponseT> unaryCallable =
          UnaryCallable.<RequestT, ResponseT>create(callable).bind(context.getChannel());
      BundlingContext<RequestT, ResponseT> bundlableMessage =
          new BundlingContext<RequestT, ResponseT>(request, context, unaryCallable, result);
      String partitionKey = bundlingDescriptor.getBundlePartitionKey(request);
      ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>> forwarder =
          bundlerFactory.getForwarder(partitionKey);
      ThresholdBundleHandle bundleHandle = forwarder.addToNextBundle(bundlableMessage);
      result.setBundleHandle(bundleHandle);
      return result;
    } else {
      return callable.futureCall(request, context);
    }
  }
}
