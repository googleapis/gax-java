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

package com.google.api.gax.bundling;

/**
 * The interface representing an external threshold to be used in ThresholdBundler.
 *
 * An external threshold is a threshold which depends on events external to the
 * ThresholdBundler.
 *
 * Thresholds do not need to be thread-safe if they are only used inside
 * ThresholdBundler.
 */
public interface ExternalThreshold<E> {

  /**
   * Called from ThresholdBundler when the first item in a bundle has been added.
   *
   * Any calls into this function from ThresholdBundler will be under a lock.
   */
  void startBundle();

  /**
   * Called from ThresholdBundler.BundleHandle when externalThresholdEvent is called.
   *
   * Any calls into this function from ThresholdBundler will be under a lock.
   *
   * @param bundleHandle if the threshold has been reached, the external threshold
   *   should call BundleHandle.flushIfNotFlushedYet().
   * @param event the event for the external threshold to handle. If not recognized,
   *   this external threshold should ignore it.
   */
  void handleEvent(ThresholdBundleHandle bundleHandle, Object event);

  /**
   * Make a copy of this threshold but with the accumulated value zeroed.
   *
   * Any calls into this function from ThresholdBundler will be under a lock.
   */
  ExternalThreshold<E> copyWithZeroedValue();
}
