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

package io.gapi.gax.bundling;

import java.util.ArrayList;
import java.util.List;

/**
 * Accepts individual items and then forwards them in bundles to the given
 * ThresholdBundleReceiver for processing. This class essentially converts
 * the pull interface of ThresholdBundler into the push interface of
 * ThresholdBundleReceiver.
 */
public class ThresholdBundlingForwarder<T> implements AutoCloseable {
  private final ThresholdBundler<T> bundler;
  private final ThresholdBundleReceiver<T> bundleReceiver;
  private final BundleForwardingRunnable forwardingRunnable;
  private final Thread forwarderThread;

  /**
   * Constructs a ThresholdBundlingForwarder. The start() method must
   * be called for the forwarder to start forwarding.
   */
  public ThresholdBundlingForwarder(ThresholdBundler<T> bundler,
      ThresholdBundleReceiver<T> bundleReceiver) {
    this.bundleReceiver = bundleReceiver;
    this.bundler = bundler;
    forwardingRunnable = new BundleForwardingRunnable();
    forwarderThread = new Thread(forwardingRunnable);
  }

  /**
   * Start the forwarder thread.
   */
  public void start() {
    forwarderThread.start();
  }

  /**
   * First validates that the receiver can receive the given item (based
   * on the inherent characteristics of the item), and then hands it off to
   * the bundler.
   */
  public void addToNextBundle(T item) {
    bundleReceiver.validateItem(item);
    bundler.add(item);
  }

  @Override
  public void close() {
    forwarderThread.interrupt();
    try {
      forwarderThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private class BundleForwardingRunnable implements Runnable {
    @Override
    public void run() {
      do {
        try {
          processBundle(bundler.takeBundle());
        } catch (InterruptedException e) {
          break;
        }
      } while (!Thread.currentThread().isInterrupted());

      List<T> lastBundle = new ArrayList<>();
      bundler.drainTo(lastBundle);
      processBundle(lastBundle);
    }

    private void processBundle(List<T> bundle) {
      if (bundle.size() == 0) {
        return;
      }
      bundleReceiver.processBundle(bundle);
    }
  }
}
