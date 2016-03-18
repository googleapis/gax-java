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
   * Make a copy of this threshold with the accumulated value reset.
   *
   * Any calls into this function from ThresholdBundler will be under a lock.
   */
  ExternalThreshold<E> copyReset();

}
