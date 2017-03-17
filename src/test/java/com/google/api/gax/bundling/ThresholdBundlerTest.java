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
import com.google.api.gax.core.TrackedFlowController;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ThresholdBundlerTest {

  private static final ScheduledExecutorService EXECUTOR = new ScheduledThreadPoolExecutor(1);

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

  private static TrackedFlowController trackedFlowController;

  private static BundlingFlowController<SimpleBundle> getTrackedIntegerBundlingFlowController(
      Integer elementCount, Integer byteCount, LimitExceededBehavior limitExceededBehaviour) {
    trackedFlowController =
        new TrackedFlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(elementCount)
                .setMaxOutstandingRequestBytes(byteCount)
                .setLimitExceededBehavior(limitExceededBehaviour)
                .build());
    return new BundlingFlowController<>(
        trackedFlowController,
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

  private static ThresholdBundler.Builder<SimpleBundle> createSimpleBundlerBuidler(
      AccumulatingBundleReceiver<SimpleBundle> receiver) {
    return ThresholdBundler.<SimpleBundle>newBuilder()
        .setThresholds(BundlingThresholds.<SimpleBundle>of(100))
        .setExecutor(EXECUTOR)
        .setMaxDelay(Duration.millis(10000))
        .setReceiver(receiver)
        .setFlowController(ThresholdBundlerTest.<SimpleBundle>getDisabledBundlingFlowController())
        .setBundleMerger(new SimpleBundleMerger());
  }

  @Test
  public void testAdd() throws Exception {
    AccumulatingBundleReceiver<SimpleBundle> receiver = new AccumulatingBundleReceiver<>();
    ThresholdBundler<SimpleBundle> bundler = createSimpleBundlerBuidler(receiver).build();
    bundler.add(SimpleBundle.fromInteger(14));
    Truth.assertThat(bundler.isEmpty()).isFalse();
    Truth.assertThat(receiver.getBundles().size()).isEqualTo(0);

    bundler.pushCurrentBundle().get();
    Truth.assertThat(bundler.isEmpty()).isTrue();
    Truth.assertThat(receiver.getBundles().size()).isEqualTo(1);
    Truth.assertThat(receiver.getBundles().get(0).getIntegers()).isEqualTo(Arrays.asList(14));
  }

  @Test
  public void testBundling() throws Exception {
    AccumulatingBundleReceiver<SimpleBundle> receiver = new AccumulatingBundleReceiver<>();
    ThresholdBundler<SimpleBundle> bundler =
        createSimpleBundlerBuidler(receiver)
            .setThresholds(BundlingThresholds.<SimpleBundle>of(2))
            .build();

    bundler.add(SimpleBundle.fromInteger(3));
    bundler.add(SimpleBundle.fromInteger(5));
    // Give time for the executor to push the bundle
    Thread.sleep(100);
    Truth.assertThat(receiver.getBundles().size()).isEqualTo(1);

    bundler.add(SimpleBundle.fromInteger(7));
    bundler.add(SimpleBundle.fromInteger(9));
    // Give time for the executor to push the bundle
    Thread.sleep(100);
    Truth.assertThat(receiver.getBundles().size()).isEqualTo(2);

    bundler.add(SimpleBundle.fromInteger(11));

    bundler.pushCurrentBundle().get();

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
    AccumulatingBundleReceiver<SimpleBundle> receiver = new AccumulatingBundleReceiver<>();
    ThresholdBundler<SimpleBundle> bundler =
        createSimpleBundlerBuidler(receiver).setMaxDelay(Duration.millis(100)).build();

    bundler.add(SimpleBundle.fromInteger(3));
    bundler.add(SimpleBundle.fromInteger(5));
    // Give time for the delay to trigger and push the bundle
    Thread.sleep(500);
    Truth.assertThat(receiver.getBundles().size()).isEqualTo(1);

    bundler.add(SimpleBundle.fromInteger(11));

    bundler.pushCurrentBundle().get();

    List<List<Integer>> expected = Arrays.asList(Arrays.asList(3, 5), Arrays.asList(11));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBundle bundle : receiver.getBundles()) {
      actual.add(bundle.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testExceptionWithNullFlowController() {
    thrown.expect(NullPointerException.class);
    ThresholdBundler.<SimpleBundle>newBuilder()
        .setThresholds(BundlingThresholds.<SimpleBundle>of(100))
        .setExecutor(EXECUTOR)
        .setMaxDelay(Duration.millis(10000))
        .setReceiver(new AccumulatingBundleReceiver<SimpleBundle>())
        .setBundleMerger(new SimpleBundleMerger())
        .build();
  }

  @Test
  public void testBundlingWithFlowControl() throws Exception {
    AccumulatingBundleReceiver<SimpleBundle> receiver = new AccumulatingBundleReceiver<>();
    ThresholdBundler<SimpleBundle> bundler =
        createSimpleBundlerBuidler(receiver)
            .setThresholds(BundlingThresholds.<SimpleBundle>of(2))
            .setFlowController(
                getTrackedIntegerBundlingFlowController(2, null, LimitExceededBehavior.Block))
            .build();

    Truth.assertThat(trackedFlowController.getElementsReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getElementsReleased()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReleased()).isEqualTo(0);

    bundler.add(SimpleBundle.fromInteger(3));
    bundler.add(SimpleBundle.fromInteger(5));
    bundler.add(
        SimpleBundle.fromInteger(7)); // We expect to block here until the first bundle is handled
    Truth.assertThat(receiver.getBundles().size()).isEqualTo(1);
    bundler.add(SimpleBundle.fromInteger(9));
    bundler.add(
        SimpleBundle.fromInteger(11)); // We expect to block here until the second bundle is handled
    Truth.assertThat(receiver.getBundles().size()).isEqualTo(2);

    bundler.pushCurrentBundle().get();

    List<List<Integer>> expected =
        Arrays.asList(Arrays.asList(3, 5), Arrays.asList(7, 9), Arrays.asList(11));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBundle bundle : receiver.getBundles()) {
      actual.add(bundle.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);

    Truth.assertThat(trackedFlowController.getElementsReserved())
        .isEqualTo(trackedFlowController.getElementsReleased());
    Truth.assertThat(trackedFlowController.getBytesReserved())
        .isEqualTo(trackedFlowController.getBytesReleased());
  }

  @Test
  public void testBundlingFlowControlExceptionRecovery() throws Exception {
    AccumulatingBundleReceiver<SimpleBundle> receiver = new AccumulatingBundleReceiver<>();
    ThresholdBundler<SimpleBundle> bundler =
        createSimpleBundlerBuidler(receiver)
            .setThresholds(BundlingThresholds.<SimpleBundle>of(4))
            .setFlowController(
                getTrackedIntegerBundlingFlowController(
                    3, null, LimitExceededBehavior.ThrowException))
            .build();

    Truth.assertThat(trackedFlowController.getElementsReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getElementsReleased()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReserved()).isEqualTo(0);
    Truth.assertThat(trackedFlowController.getBytesReleased()).isEqualTo(0);

    bundler.add(SimpleBundle.fromInteger(3));
    bundler.add(SimpleBundle.fromInteger(5));
    bundler.add(SimpleBundle.fromInteger(7));
    try {
      bundler.add(SimpleBundle.fromInteger(9));
      Truth.assertWithMessage("Failing: expected exception").that(false).isTrue();
    } catch (FlowControlException e) {
    }
    bundler.pushCurrentBundle().get();
    Truth.assertThat(receiver.getBundles().size()).isEqualTo(1);
    bundler.add(SimpleBundle.fromInteger(11));
    bundler.add(SimpleBundle.fromInteger(13));
    bundler.pushCurrentBundle().get();

    List<List<Integer>> expected = Arrays.asList(Arrays.asList(3, 5, 7), Arrays.asList(11, 13));
    List<List<Integer>> actual = new ArrayList<>();
    for (SimpleBundle bundle : receiver.getBundles()) {
      actual.add(bundle.getIntegers());
    }
    Truth.assertThat(actual).isEqualTo(expected);

    Truth.assertThat(trackedFlowController.getElementsReserved())
        .isEqualTo(trackedFlowController.getElementsReleased());
    Truth.assertThat(trackedFlowController.getBytesReserved())
        .isEqualTo(trackedFlowController.getBytesReleased());
  }
}
