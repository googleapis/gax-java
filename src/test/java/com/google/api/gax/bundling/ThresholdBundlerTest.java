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

package com.google.api.gax.bundling;

import com.google.common.truth.Truth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.joda.time.Duration;
import org.junit.Test;

public class ThresholdBundlerTest {

  @Test
  public void testEmptyAddAndDrain() {
    ThresholdBundler<Integer> bundler =
        new ThresholdBundler<Integer>(BundlingThresholds.<Integer>of(5));
    List<Integer> resultBundle = new ArrayList<>();
    Truth.assertThat(bundler.size()).isEqualTo(0);
    Truth.assertThat(bundler.toArray()).isEqualTo(new Integer[]{});

    int drained = bundler.drainTo(resultBundle);
    Truth.assertThat(drained).isEqualTo(0);
    Truth.assertThat(resultBundle).isEqualTo(new ArrayList<>());
  }

  @Test
  public void testAddAndDrain() {
    ThresholdBundler<Integer> bundler =
        new ThresholdBundler<Integer>(BundlingThresholds.<Integer>of(5));
    bundler.add(14);
    Truth.assertThat(bundler.size()).isEqualTo(1);
    Truth.assertThat(bundler.toArray()).isEqualTo(new Integer[]{14});

    List<Integer> resultBundle = new ArrayList<>();
    int drained = bundler.drainTo(resultBundle);
    Truth.assertThat(drained).isEqualTo(1);
    Truth.assertThat(resultBundle).isEqualTo(Arrays.asList(14));
    Truth.assertThat(bundler.size()).isEqualTo(0);
    Truth.assertThat(bundler.toArray()).isEqualTo(new Integer[]{});

    List<Integer> resultBundle2 = new ArrayList<>();
    int drained2 = bundler.drainTo(resultBundle2);
    Truth.assertThat(drained2).isEqualTo(0);
    Truth.assertThat(resultBundle2).isEqualTo(new ArrayList<>());
  }

  @Test
  public void testBundling() throws Exception {
    ThresholdBundler<Integer> bundler =
        new ThresholdBundler<Integer>(BundlingThresholds.<Integer>of(2));
    AccumulatingBundleReceiver<Integer> receiver =
        new AccumulatingBundleReceiver<Integer>();
    ThresholdBundlingForwarder<Integer> forwarder =
        new ThresholdBundlingForwarder<Integer>(bundler, receiver);

    try {
      forwarder.start();
      bundler.add(3);
      bundler.add(5);
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);

      bundler.add(7);
      bundler.add(9);
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);

      bundler.add(11);

    } finally {
      forwarder.close();
    }

    List<List<Integer>> expected =
        Arrays.asList(
            Arrays.asList(3, 5),
            Arrays.asList(7, 9),
            Arrays.asList(11));
    Truth.assertThat(receiver.getBundles()).isEqualTo(expected);
  }

  @Test
  public void testBundlingWithDelay() throws Exception {
    ThresholdBundler<Integer> bundler =
        new ThresholdBundler<Integer>(Duration.millis(100));
    AccumulatingBundleReceiver<Integer> receiver =
        new AccumulatingBundleReceiver<Integer>();
    ThresholdBundlingForwarder<Integer> forwarder =
        new ThresholdBundlingForwarder<Integer>(bundler, receiver);

    try {
      forwarder.start();
      bundler.add(3);
      bundler.add(5);
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(500);

      bundler.add(11);

    } finally {
      forwarder.close();
    }

    List<List<Integer>> expected =
        Arrays.asList(
            Arrays.asList(3, 5),
            Arrays.asList(11));
    Truth.assertThat(receiver.getBundles()).isEqualTo(expected);
  }

  @Test
  public void testFlush() throws Exception {
    ThresholdBundler<Integer> bundler =
        new ThresholdBundler<Integer>(BundlingThresholds.<Integer>of(2));
    AccumulatingBundleReceiver<Integer> receiver =
        new AccumulatingBundleReceiver<Integer>();
    ThresholdBundlingForwarder<Integer> forwarder =
        new ThresholdBundlingForwarder<Integer>(bundler, receiver);

    try {
      forwarder.start();
      bundler.add(3);
      // flush before the threshold is met
      bundler.flush();
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);

      bundler.add(7);
      bundler.add(9);
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);

    } finally {
      forwarder.close();
    }

    List<List<Integer>> expected =
        Arrays.asList(
            Arrays.asList(3),
            Arrays.asList(7, 9));
    Truth.assertThat(receiver.getBundles()).isEqualTo(expected);
  }
}
