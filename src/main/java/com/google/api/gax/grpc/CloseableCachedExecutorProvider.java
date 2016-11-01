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
package com.google.api.gax.grpc;

import java.util.concurrent.ScheduledExecutorService;

/**
 * CloseableCachedExecutorProvider calls an inner ExecutorProvider the first time getExecutor()
 * is called and caches the executor, and then returns that cached
 * executor on subsequent calls.
 *
 * Note: close() must be called on this class to clean up the executor.
 */
public class CloseableCachedExecutorProvider implements ExecutorProvider {
  private final ExecutorProvider innerProvider;
  private ScheduledExecutorService cachedExecutor;

  private CloseableCachedExecutorProvider(ExecutorProvider innerProvider) {
    this.innerProvider = innerProvider;
  }

  @Override
  public boolean shouldAutoClose() {
    return false;
  }

  @Override
  public ScheduledExecutorService getExecutor() {
    if (cachedExecutor == null) {
      cachedExecutor = innerProvider.getExecutor();
    }
    return cachedExecutor;
  }

  public void close() {
    if (cachedExecutor != null) {
      cachedExecutor.shutdown();
    }
  }

  public static CloseableCachedExecutorProvider create(ExecutorProvider innerProvider) {
    return new CloseableCachedExecutorProvider(innerProvider);
  }
}
