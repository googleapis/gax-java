package com.google.api.gax.bundling;

/**
 * A handle to a bundle in a ThresholdBundler. Using this handle, code external
 * to a ThresholdBundler can safely ensure that a particular bundle has been
 * flushed without accidentally flushing a following bundle.
 */
public interface ThresholdBundleHandle {
  /**
   * Notifies the ThresholdBundler of an event for this threshold bundle, which
   * is targeted at a particular ExternalThreshold.
   */
  void externalThresholdEvent(Object event);

  /**
   * Flush this bundle if it hasn't been flushed yet.
   */
  void flush();
}
