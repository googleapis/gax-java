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

import com.google.api.gax.bundling.ExternalThreshold;
import com.google.api.gax.bundling.ThresholdBundleHandle;

/**
 * An external bundling threshold for a ThresholdBundler which keeps track of
 * how many threads are blocking on the bundler.
 *
 * <p>Package-private for internal use.
 */
class BlockingCallThreshold<E> implements ExternalThreshold<E> {
  private final int threshold;
  private int sum;

  /**
   * Construct an instance.
   */
  public BlockingCallThreshold(int threshold) {
    this.threshold = threshold;
    this.sum = 0;
  }

  @Override
  public void startBundle() {}

  @Override
  public void handleEvent(ThresholdBundleHandle bundleHandle, Object event) {
    if (event instanceof NewBlockingCall) {
      sum += 1;
      if (sum >= threshold) {
        bundleHandle.flush();
      }
    }
  }

  @Override
  public ExternalThreshold<E> copyWithZeroedValue() {
    return new BlockingCallThreshold<>(threshold);
  }

  /**
   * The class to represent a blocking call event. Pass an instance of this
   * class to ThresholdBundleHandle.externalThresholdEvent().
   */
  public static class NewBlockingCall {}
}
