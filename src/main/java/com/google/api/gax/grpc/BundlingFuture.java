package com.google.api.gax.grpc;

import com.google.api.gax.bundling.ThresholdBundleHandle;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A ListenableFuture to be used with bundling. It wraps a SettableFuture, and
 * if a ThresholdBundleHandle is provided to it, it will call
 * externalThresholdEvent to notify any BlockingCallThreshold of a blocking event
 * (i.e. a call to get()).
 *
 * <p>Package-private for internal use.
 */
class BundlingFuture<ResponseT> implements ListenableFuture<ResponseT> {
  private final Lock lock = new ReentrantLock();
  private final SettableFuture<ResponseT> settableFuture;
  private ThresholdBundleHandle bundleHandle;

  /**
   * Get a new instance.
   */
  public static <T> BundlingFuture<T> create() {
    return new BundlingFuture<T>();
  }

  private BundlingFuture() {
    this.settableFuture = SettableFuture.<ResponseT>create();
  }

  @Override
  public void addListener(Runnable runnable, Executor executor) {
    settableFuture.addListener(runnable, executor);
  }

  @Override
  public ResponseT get() throws InterruptedException, ExecutionException {
    final Lock lock = this.lock;
    lock.lock();
    ThresholdBundleHandle localBundleHandle = null;
    try {
      localBundleHandle = bundleHandle;
    } finally {
      lock.unlock();
    }
    if (localBundleHandle != null) {
      localBundleHandle.externalThresholdEvent(new BlockingCallThreshold.NewBlockingCall());
    }
    return settableFuture.get();
  }

  @Override
  public ResponseT get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    final Lock lock = this.lock;
    lock.lock();
    ThresholdBundleHandle localBundleHandle = null;
    try {
      localBundleHandle = bundleHandle;
    } finally {
      lock.unlock();
    }
    if (localBundleHandle != null) {
      localBundleHandle.externalThresholdEvent(new BlockingCallThreshold.NewBlockingCall());
    }
    return settableFuture.get(timeout, unit);
  }

  /**
   * Sets the result.
   */
  public boolean set(ResponseT value) {
    return settableFuture.set(value);
  }

  /**
   * Sets the result to an exception.
   */
  public boolean setException(Throwable throwable) {
    return settableFuture.setException(throwable);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return settableFuture.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return settableFuture.isCancelled();
  }

  @Override
  public boolean isDone() {
    return settableFuture.isDone();
  }

  /**
   * Sets the ThresholdBundleHandle to notify of blocking events.
   */
  public void setBundleHandle(ThresholdBundleHandle bundleHandle) {
    final Lock lock = this.lock;
    lock.lock();
    try {
      this.bundleHandle = bundleHandle;
    } finally {
      lock.unlock();
    }
  }

}
