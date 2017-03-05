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
package com.google.api.gax.bundling;

import com.google.api.gax.core.FlowControlSettings;
import com.google.api.gax.core.FlowController;
import com.google.api.gax.core.FlowController.FlowControlException;
import com.google.api.gax.core.FlowController.LimitExceededBehavior;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ThresholdBundlerTest {

  private static FlowController getDisabledFlowController() {
    return new FlowController(
        FlowControlSettings.newBuilder()
            .setLimitExceededBehavior(LimitExceededBehavior.Ignore)
            .build());
  }

  private static <T> BundlingFlowController<T> getDisabledBundlingFlowController() {
    return new BundlingFlowController<>(
        getDisabledFlowController(),
        new ElementCounter<T>() {
          @Override
          public long count(T t) {
            return 1;
          }
        },
        new ElementCounter<T>() {
          @Override
          public long count(T t) {
            return 1;
          }
        });
  }

  private static BundlingFlowController<SimpleBundle> getIntegerBundlingFlowController(
      Integer elementCount, Integer byteCount, LimitExceededBehavior limitExceededBehaviour) {
    return new BundlingFlowController<>(
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(elementCount)
                .setMaxOutstandingRequestBytes(byteCount)
                .setLimitExceededBehavior(limitExceededBehaviour)
                .build()),
        new ElementCounter<SimpleBundle>() {
          @Override
          public long count(SimpleBundle t) {
            return t.getIntegers().size();
          }
        },
        new ElementCounter<SimpleBundle>() {
          @Override
          public long count(SimpleBundle t) {
            long counter = 0;
            for (Integer i : t.integers) {
              counter += i;
            }
            return counter;
          }
        });
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static class SimpleBundle {

    private final List<Integer> integers = new ArrayList<>();

    private static SimpleBundle fromInteger(Integer integer) {
      SimpleBundle bundle = new SimpleBundle();
      bundle.integers.add(integer);
      return bundle;
    }

    public void merge(SimpleBundle t) {
      integers.addAll(t.integers);
    }

    private List<Integer> getIntegers() {
      return integers;
    }
  }

  private static class SimpleBundleMerger implements BundleMerger<SimpleBundle> {
    @Override
    public void merge(SimpleBundle bundle, SimpleBundle newBundle) {
      bundle.merge(newBundle);
    }
  }

  @Test
  public void testEmptyAddAndDrain() {
    ThresholdBundler<SimpleBundle> bundler =
        ThresholdBundler.<SimpleBundle>newBuilder()
            .setThresholds(BundlingThresholds.<SimpleBundle>of(5))
            .setFlowController(
                ThresholdBundlerTest.<SimpleBundle>getDisabledBundlingFlowController())
            .setBundleMerger(new SimpleBundleMerger())
            .build();
    Truth.assertThat(bundler.isEmpty()).isTrue();

    SimpleBundle resultBundle = bundler.removeBundle();
    Truth.assertThat(resultBundle).isNull();
  }

  @Test
  public void testAddAndDrain() throws FlowControlException {
    ThresholdBundler<SimpleBundle> bundler =
        ThresholdBundler.<SimpleBundle>newBuilder()
            .setThresholds(BundlingThresholds.<SimpleBundle>of(5))
            .setFlowController(
                ThresholdBundlerTest.<SimpleBundle>getDisabledBundlingFlowController())
            .setBundleMerger(new SimpleBundleMerger())
            .build();
    bundler.add(SimpleBundle.fromInteger(14));
    Truth.assertThat(bundler.isEmpty()).isFalse();

    SimpleBundle resultBundle = bundler.removeBundle();
    Truth.assertThat(resultBundle.getIntegers()).isEqualTo(Arrays.asList(14));
    Truth.assertThat(bundler.isEmpty()).isTrue();

    SimpleBundle resultBundle2 = bundler.removeBundle();
    Truth.assertThat(resultBundle2).isNull();
  }

  @Test
  public void testBundling() throws Exception {
    ThresholdBundler<SimpleBundle> bundler =
        ThresholdBundler.<SimpleBundle>newBuilder()
            .setThresholds(BundlingThresholds.<SimpleBundle>of(2))
            .setFlowController(
                ThresholdBundlerTest.<SimpleBundle>getDisabledBundlingFlowController())
            .setBundleMerger(new SimpleBundleMerger())
            .build();
    AccumulatingBundleReceiver<SimpleBundle> receiver =
        new AccumulatingBundleReceiver<SimpleBundle>();
    ThresholdBundlingForwarder<SimpleBundle> forwarder =
        new ThresholdBundlingForwarder<SimpleBundle>(bundler, receiver);

    try {
      forwarder.start();
      bundler.add(SimpleBundle.fromInteger(3));
      bundler.add(SimpleBundle.fromInteger(5));
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);

      bundler.add(SimpleBundle.fromInteger(7));
      bundler.add(SimpleBundle.fromInteger(9));
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);

      bundler.add(SimpleBundle.fromInteger(11));

    } finally {
      forwarder.close();
    }

    List<List<Integer>> expected =
        Arrays.asList(Arrays.asList(3, 5), Arrays.asList(7, 9), Arrays.asList(11));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBundle bundle : receiver.getBundles()) {
      actual.add(bundle.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testBundlingWithDelay() throws Exception {
    ThresholdBundler<SimpleBundle> bundler =
        ThresholdBundler.<SimpleBundle>newBuilder()
            .setMaxDelay(Duration.millis(100))
            .setFlowController(
                ThresholdBundlerTest.<SimpleBundle>getDisabledBundlingFlowController())
            .setBundleMerger(new SimpleBundleMerger())
            .build();
    AccumulatingBundleReceiver<SimpleBundle> receiver =
        new AccumulatingBundleReceiver<SimpleBundle>();
    ThresholdBundlingForwarder<SimpleBundle> forwarder =
        new ThresholdBundlingForwarder<SimpleBundle>(bundler, receiver);

    try {
      forwarder.start();
      bundler.add(SimpleBundle.fromInteger(3));
      bundler.add(SimpleBundle.fromInteger(5));
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(500);

      bundler.add(SimpleBundle.fromInteger(11));

    } finally {
      forwarder.close();
    }

    List<List<Integer>> expected = Arrays.asList(Arrays.asList(3, 5), Arrays.asList(11));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBundle bundle : receiver.getBundles()) {
      actual.add(bundle.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testFlush() throws Exception {
    ThresholdBundler<SimpleBundle> bundler =
        ThresholdBundler.<SimpleBundle>newBuilder()
            .setThresholds(BundlingThresholds.<SimpleBundle>of(2))
            .setFlowController(
                ThresholdBundlerTest.<SimpleBundle>getDisabledBundlingFlowController())
            .setBundleMerger(new SimpleBundleMerger())
            .build();
    AccumulatingBundleReceiver<SimpleBundle> receiver =
        new AccumulatingBundleReceiver<SimpleBundle>();
    ThresholdBundlingForwarder<SimpleBundle> forwarder =
        new ThresholdBundlingForwarder<SimpleBundle>(bundler, receiver);

    try {
      forwarder.start();
      bundler.add(SimpleBundle.fromInteger(3));
      // flush before the threshold is met
      bundler.flush();
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);

      bundler.add(SimpleBundle.fromInteger(7));
      bundler.add(SimpleBundle.fromInteger(9));
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);

      // should have no effect (everything should be consumed)
      bundler.flush();

    } finally {
      forwarder.close();
    }

    List<List<Integer>> expected = Arrays.asList(Arrays.asList(3), Arrays.asList(7, 9));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBundle bundle : receiver.getBundles()) {
      actual.add(bundle.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testExceptionWithNullFlowController() {
    thrown.expect(NullPointerException.class);
    ThresholdBundler.<SimpleBundle>newBuilder().build();
  }

  @Test
  public void testBundlingWithFlowControl() throws Exception {
    ThresholdBundler<SimpleBundle> bundler =
        ThresholdBundler.<SimpleBundle>newBuilder()
            .setThresholds(BundlingThresholds.<SimpleBundle>of(2))
            .setFlowController(
                getIntegerBundlingFlowController(3, null, LimitExceededBehavior.Block))
            .setBundleMerger(new SimpleBundleMerger())
            .build();
    AccumulatingBundleReceiver<SimpleBundle> receiver =
        new AccumulatingBundleReceiver<SimpleBundle>();
    ThresholdBundlingForwarder<SimpleBundle> forwarder =
        new ThresholdBundlingForwarder<SimpleBundle>(bundler, receiver);

    try {
      forwarder.start();
      bundler.add(SimpleBundle.fromInteger(3));
      bundler.add(SimpleBundle.fromInteger(5));
      bundler.add(SimpleBundle.fromInteger(7));
      bundler.add(
          SimpleBundle.fromInteger(9)); // We expect to block here until the first bundle is handled
      bundler.add(SimpleBundle.fromInteger(11));

    } finally {
      forwarder.close();
    }

    List<List<Integer>> expected =
        Arrays.asList(Arrays.asList(3, 5), Arrays.asList(7, 9), Arrays.asList(11));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBundle bundle : receiver.getBundles()) {
      actual.add(bundle.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testBundlingFlowControlExceptionRecovery() throws Exception {
    ThresholdBundler<SimpleBundle> bundler =
        ThresholdBundler.<SimpleBundle>newBuilder()
            .setThresholds(BundlingThresholds.<SimpleBundle>of(2))
            .setFlowController(
                getIntegerBundlingFlowController(3, null, LimitExceededBehavior.ThrowException))
            .setBundleMerger(new SimpleBundleMerger())
            .build();
    AccumulatingBundleReceiver<SimpleBundle> receiver =
        new AccumulatingBundleReceiver<SimpleBundle>();
    ThresholdBundlingForwarder<SimpleBundle> forwarder =
        new ThresholdBundlingForwarder<SimpleBundle>(bundler, receiver);

    try {
      // Note: do not start the forwarder here, otherwise we have a race condition in the test
      // between whether bundler.add(9) executes before the first bundle is processed.
      bundler.add(SimpleBundle.fromInteger(3));
      bundler.add(SimpleBundle.fromInteger(5));
      bundler.add(SimpleBundle.fromInteger(7));
      try {
        bundler.add(SimpleBundle.fromInteger(9));
      } catch (FlowControlException e) {
      }
      forwarder.start();
      // Give time for the forwarder thread to catch the bundle
      Thread.sleep(100);
      bundler.add(SimpleBundle.fromInteger(11));
      bundler.add(SimpleBundle.fromInteger(13));

    } finally {
      forwarder.close();
    }

    List<List<Integer>> expected =
        Arrays.asList(Arrays.asList(3, 5), Arrays.asList(7, 11), Arrays.asList(13));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBundle bundle : receiver.getBundles()) {
      actual.add(bundle.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);
  }
}
