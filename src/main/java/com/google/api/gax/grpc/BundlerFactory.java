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

import com.google.api.gax.bundling.BundlingThreshold;
import com.google.api.gax.bundling.ElementCounter;
import com.google.api.gax.bundling.ExternalThreshold;
import com.google.api.gax.bundling.NumericThreshold;
import com.google.api.gax.bundling.ThresholdBundler;
import com.google.api.gax.bundling.ThresholdBundlingForwarder;
import com.google.common.collect.ImmutableList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Factory class which, for each unique partitionKey, creates a trio including a ThresholdBundler,
 * BundleExecutor, and ThresholdBundlingForwarder. The ThresholdBundlingForwarder pulls items from
 * the ThresholdBundler and forwards them to the BundleExecutor for processing.
 *
 * <p>This is public only for technical reasons, for advanced usage.
 */
public final class BundlerFactory<RequestT, ResponseT> implements AutoCloseable {
  private final Map<String, ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>>>
      forwarders = new ConcurrentHashMap<>();
  private final BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
  private final BundlingSettings bundlingSettings;
  private final Object lock = new Object();

  public BundlerFactory(
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlingSettings bundlingSettings) {
    this.bundlingDescriptor = bundlingDescriptor;
    this.bundlingSettings = bundlingSettings;
  }

  /**
   * Provides the ThresholdBundlingForwarder corresponding to the given partitionKey, or constructs
   * one if it doesn't exist yet. The implementation is thread-safe.
   */
  public ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>> getForwarder(
      String partitionKey) {
    ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>> forwarder =
        forwarders.get(partitionKey);
    if (forwarder == null) {
      synchronized (lock) {
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

  /**
   * Returns the BundlingSettings object that is associated with this factory.
   *
   * <p>Package-private for internal use.
   */
  BundlingSettings getBundlingSettings() {
    return bundlingSettings;
  }

  private ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>> createForwarder(
      String partitionKey) {
    ThresholdBundler<BundlingContext<RequestT, ResponseT>> bundler =
        ThresholdBundler.<BundlingContext<RequestT, ResponseT>>newBuilder()
            .setThresholds(getThresholds(bundlingSettings))
            .setExternalThresholds(getExternalThresholds(bundlingSettings))
            .setMaxDelay(bundlingSettings.getDelayThreshold())
            .build();
    BundleExecutor<RequestT, ResponseT> processor =
        new BundleExecutor<>(bundlingDescriptor, partitionKey);
    return new ThresholdBundlingForwarder<>(bundler, processor);
  }

  @Override
  public void close() {
    synchronized (lock) {
      for (ThresholdBundlingForwarder<BundlingContext<RequestT, ResponseT>> forwarder :
          forwarders.values()) {
        forwarder.close();
      }
      forwarders.clear();
    }
  }

  private ImmutableList<BundlingThreshold<BundlingContext<RequestT, ResponseT>>> getThresholds(
      BundlingSettings bundlingSettings) {
    ImmutableList.Builder<BundlingThreshold<BundlingContext<RequestT, ResponseT>>> listBuilder =
        ImmutableList.<BundlingThreshold<BundlingContext<RequestT, ResponseT>>>builder();

    if (bundlingSettings.getElementCountThreshold() != null) {
      ElementCounter<BundlingContext<RequestT, ResponseT>> elementCounter =
          new ElementCounter<BundlingContext<RequestT, ResponseT>>() {
            @Override
            public long count(BundlingContext<RequestT, ResponseT> bundlablePublish) {
              return bundlingDescriptor.countElements(bundlablePublish.getRequest());
            }
          };

      Long elementCountLimit = null;
      if (bundlingSettings.getElementCountLimit() != null) {
        elementCountLimit = bundlingSettings.getElementCountLimit().longValue();
      }

      BundlingThreshold<BundlingContext<RequestT, ResponseT>> countThreshold =
          new NumericThreshold<>(
              bundlingSettings.getElementCountThreshold(), elementCountLimit, elementCounter);
      listBuilder.add(countThreshold);
    }

    if (bundlingSettings.getRequestByteThreshold() != null) {
      ElementCounter<BundlingContext<RequestT, ResponseT>> requestByteCounter =
          new ElementCounter<BundlingContext<RequestT, ResponseT>>() {
            @Override
            public long count(BundlingContext<RequestT, ResponseT> bundlablePublish) {
              return bundlingDescriptor.countBytes(bundlablePublish.getRequest());
            }
          };

      Long requestByteLimit = null;
      if (bundlingSettings.getRequestByteLimit() != null) {
        requestByteLimit = bundlingSettings.getRequestByteLimit().longValue();
      }

      BundlingThreshold<BundlingContext<RequestT, ResponseT>> byteThreshold =
          new NumericThreshold<>(
              bundlingSettings.getRequestByteThreshold(), requestByteLimit, requestByteCounter);
      listBuilder.add(byteThreshold);
    }

    return listBuilder.build();
  }

  private ImmutableList<ExternalThreshold<BundlingContext<RequestT, ResponseT>>>
      getExternalThresholds(BundlingSettings bundlingSettings) {
    ImmutableList.Builder<ExternalThreshold<BundlingContext<RequestT, ResponseT>>> listBuilder =
        ImmutableList.<ExternalThreshold<BundlingContext<RequestT, ResponseT>>>builder();

    Long blockingCallCountThreshold = bundlingSettings.getBlockingCallCountThreshold();
    if (blockingCallCountThreshold == null) {
      blockingCallCountThreshold = 1L;
    }
    if (blockingCallCountThreshold > 0) {
      BlockingCallThreshold<BundlingContext<RequestT, ResponseT>> blockingCallThreshold =
          new BlockingCallThreshold<>(blockingCallCountThreshold);
      listBuilder.add(blockingCallThreshold);
    }

    return listBuilder.build();
  }
}
