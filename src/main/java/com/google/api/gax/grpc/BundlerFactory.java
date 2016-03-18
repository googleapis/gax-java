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

import com.google.api.gax.bundling.ThresholdBundler;
import com.google.api.gax.bundling.ThresholdBundlingForwarder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Factory class which, for each unique partitionKey, creates a trio including
 * a ThresholdBundler, BundleExecutor, and ThresholdBundlingForwarder. The
 * ThresholdBundlingForwarder pulls items from the ThresholdBundler and forwards
 * them to the BundleExecutor for processing.
 */
public class BundlerFactory<RequestT, ResponseT> implements AutoCloseable {
  private final Map<String,
      ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>>> forwarders =
          new ConcurrentHashMap<>();
  private final BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
  private final BundlingSettings<RequestT, ResponseT> bundlingSettings;
  private final Object lock = new Object();

  public BundlerFactory(BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlingSettings<RequestT, ResponseT> bundlingSettings) {
    this.bundlingDescriptor = bundlingDescriptor;
    this.bundlingSettings = bundlingSettings;
  }

  /**
   * Provides the ThresholdBundlingForwarder corresponding to the give
   * partitionKey, or constructs one if it doesn't exist yet. The implementation
   * is thread-safe.
   */
  public ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>>
      getForwarder(String partitionKey) {
    ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>> forwarder =
        forwarders.get(partitionKey);
    if (forwarder == null) {
      synchronized(lock) {
        forwarder = forwarders.get(partitionKey);
        if (forwarder == null) {
          forwarder = createForwarder(partitionKey);
          forwarders.put(partitionKey, forwarder);
          forwarder.start();
        }
      }
    }
    return forwarder;
  }

  private ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>>
      createForwarder(String partitionKey) {
    ThresholdBundler<BundlingContext<RequestT, ResponseT>> bundler =
        ThresholdBundler.<BundlingContext<RequestT, ResponseT>>newBuilder()
          .setThresholds(bundlingSettings.getThresholds())
          .setExternalThresholds(bundlingSettings.getExternalThresholds())
          .setMaxDelay(bundlingSettings.getDelayThreshold())
          .build();
    BundleExecutor<RequestT, ResponseT> processor =
        new BundleExecutor<>(bundlingDescriptor, partitionKey);
    return new ThresholdBundlingForwarder<>(bundler, processor);
  }

  @Override
  public void close() {
    synchronized(lock) {
      for (ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>> forwarder :
          forwarders.values()) {
        forwarder.close();
      }
      forwarders.clear();
    }
  }
}
