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
 * A ListenableFuture to be used with bundling. It wraps a SettableFuture, and if a
 * ThresholdBundleHandle is provided to it, it will call externalThresholdEvent to notify any
 * BlockingCallThreshold of a blocking event (i.e. a call to get()).
 *
 * <p>
 * Package-private for internal use.
 */
class BundlingFuture<ResponseT> implements ListenableFuture<ResponseT> {
  private final Lock lock = new ReentrantLock();
  private final SettableFuture<ResponseT> settableFuture;
  private ThresholdBundleHandle bundleHandle;

  /**
   * Get a new instance.
   */
  public static <T> BundlingFuture<T> create() {
    return new BundlingFuture<>();
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
