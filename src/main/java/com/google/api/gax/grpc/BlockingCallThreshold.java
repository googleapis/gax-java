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
  public void startBundle() {
  }

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
    return new BlockingCallThreshold<E>(threshold);
  }

  /**
   * The class to represent a blocking call event. Pass an instance of this
   * class to ThresholdBundleHandle.externalThresholdEvent().
   */
  public static class NewBlockingCall {
  }
}
