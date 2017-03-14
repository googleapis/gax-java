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

import com.google.api.gax.bundling.BundleMerger;
import com.google.api.gax.bundling.BundlingFlowController;
import com.google.api.gax.bundling.BundlingSettings;
import com.google.api.gax.bundling.BundlingThreshold;
import com.google.api.gax.bundling.ElementCounter;
import com.google.api.gax.bundling.NumericThreshold;
import com.google.api.gax.bundling.ThresholdBundler;
import com.google.api.gax.core.FlowControlSettings;
import com.google.api.gax.core.FlowController;
import com.google.api.gax.core.FlowController.LimitExceededBehavior;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A Factory class which, for each unique partitionKey, creates a trio including a ThresholdBundler,
 * BundleExecutor, and ThresholdBundlingForwarder. The ThresholdBundlingForwarder pulls items from
 * the ThresholdBundler and forwards them to the BundleExecutor for processing.
 *
 * <p>
 * This is public only for technical reasons, for advanced usage.
 */
public final class BundlerFactory<RequestT, ResponseT> {
  private final Map<String, ThresholdBundler<Bundle<RequestT, ResponseT>>> bundlers =
      new ConcurrentHashMap<>();
  private final ScheduledExecutorService executor;
  private final BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
  private final FlowController flowController;
  private final BundlingSettings bundlingSettings;
  private final Object lock = new Object();

  public BundlerFactory(
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlingSettings bundlingSettings,
      ScheduledExecutorService executor) {
    this(
        bundlingDescriptor,
        bundlingSettings,
        executor,
        new FlowController(
            bundlingSettings.getFlowControlSettings() != null
                ? bundlingSettings.getFlowControlSettings()
                : FlowControlSettings.newBuilder()
                    .setLimitExceededBehavior(LimitExceededBehavior.Ignore)
                    .build()));
  }

  public BundlerFactory(
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlingSettings bundlingSettings,
      ScheduledExecutorService executor,
      FlowController flowController) {
    this.bundlingDescriptor = bundlingDescriptor;
    this.bundlingSettings = bundlingSettings;
    this.executor = executor;
    this.flowController = flowController;
  }

  /**
   * Provides the ThresholdBundler corresponding to the given partitionKey, or constructs one if it
   * doesn't exist yet. The implementation is thread-safe.
   */
  public ThresholdBundler<Bundle<RequestT, ResponseT>> getPushingBundler(String partitionKey) {
    ThresholdBundler<Bundle<RequestT, ResponseT>> bundler = bundlers.get(partitionKey);
    if (bundler == null) {
      synchronized (lock) {
        bundler = bundlers.get(partitionKey);
        if (bundler == null) {
          bundler = createBundler(partitionKey);
          bundlers.put(partitionKey, bundler);
        }
      }
    }
    return bundler;
  }

  /**
   * Returns the BundlingSettings object that is associated with this factory.
   *
   * <p>
   * Package-private for internal use.
   */
  BundlingSettings getBundlingSettings() {
    return bundlingSettings;
  }

  private ThresholdBundler<Bundle<RequestT, ResponseT>> createBundler(String partitionKey) {
    BundleExecutor<RequestT, ResponseT> processor =
        new BundleExecutor<>(bundlingDescriptor, partitionKey);
    return ThresholdBundler.<Bundle<RequestT, ResponseT>>newBuilder()
        .setThresholds(getThresholds(bundlingSettings))
        .setExecutor(executor)
        .setMaxDelay(bundlingSettings.getDelayThreshold())
        .setReceiver(processor)
        .setFlowController(createBundlingFlowController())
        .setBundleMerger(createBundleMerger())
        .build();
  }

  private BundlingFlowController<Bundle<RequestT, ResponseT>> createBundlingFlowController() {
    return new BundlingFlowController<>(
        flowController,
        new ElementCounter<Bundle<RequestT, ResponseT>>() {
          @Override
          public long count(Bundle<RequestT, ResponseT> bundlablePublish) {
            return bundlingDescriptor.countElements(bundlablePublish.getRequest());
          }
        },
        new ElementCounter<Bundle<RequestT, ResponseT>>() {
          @Override
          public long count(Bundle<RequestT, ResponseT> bundlablePublish) {
            return bundlablePublish.getByteCount();
          }
        });
  }

  private BundleMerger<Bundle<RequestT, ResponseT>> createBundleMerger() {
    return new BundleMerger<Bundle<RequestT, ResponseT>>() {
      @Override
      public void merge(Bundle<RequestT, ResponseT> bundle, Bundle<RequestT, ResponseT> newBundle) {
        bundle.merge(newBundle);
      }
    };
  }

  private ImmutableList<BundlingThreshold<Bundle<RequestT, ResponseT>>> getThresholds(
      BundlingSettings bundlingSettings) {
    ImmutableList.Builder<BundlingThreshold<Bundle<RequestT, ResponseT>>> listBuilder =
        ImmutableList.builder();

    if (bundlingSettings.getElementCountThreshold() != null) {
      ElementCounter<Bundle<RequestT, ResponseT>> elementCounter =
          new ElementCounter<Bundle<RequestT, ResponseT>>() {
            @Override
            public long count(Bundle<RequestT, ResponseT> bundlablePublish) {
              return bundlingDescriptor.countElements(bundlablePublish.getRequest());
            }
          };

      BundlingThreshold<Bundle<RequestT, ResponseT>> countThreshold =
          new NumericThreshold<>(bundlingSettings.getElementCountThreshold(), elementCounter);
      listBuilder.add(countThreshold);
    }

    if (bundlingSettings.getRequestByteThreshold() != null) {
      ElementCounter<Bundle<RequestT, ResponseT>> requestByteCounter =
          new ElementCounter<Bundle<RequestT, ResponseT>>() {
            @Override
            public long count(Bundle<RequestT, ResponseT> bundlablePublish) {
              return bundlablePublish.getByteCount();
            }
          };

      BundlingThreshold<Bundle<RequestT, ResponseT>> byteThreshold =
          new NumericThreshold<>(bundlingSettings.getRequestByteThreshold(), requestByteCounter);
      listBuilder.add(byteThreshold);
    }

    return listBuilder.build();
  }
}
