/*
 * Copyright 2017, Google Inc. All rights reserved.
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
package com.google.api.gax.core;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An {@link RpcFuture} whose result can be set. Similar to Guava's {@code SettableFuture}, but
 * redeclared so that Guava could be shaded.
 */
public final class SettableRpcFuture<V> implements RpcFuture<V> {
  private final SettableFuture<V> impl = SettableFuture.create();

  public void addListener(Runnable listener, Executor executor) {
    impl.addListener(listener, executor);
  }

  public boolean cancel(boolean mayInterruptIfRunning) {
    return impl.cancel(mayInterruptIfRunning);
  }

  public V get() throws InterruptedException, ExecutionException {
    return impl.get();
  }

  public V get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return impl.get(timeout, unit);
  }

  public boolean isCancelled() {
    return impl.isCancelled();
  }

  public boolean isDone() {
    return impl.isDone();
  }

  public boolean set(V value) {
    return impl.set(value);
  }

  public boolean setException(Throwable throwable) {
    return impl.setException(throwable);
  }

  public void addCallback(final RpcFutureCallback<? super V> callback) {
    Futures.addCallback(
        impl,
        new FutureCallback<V>() {
          @Override
          public void onFailure(Throwable t) {
            callback.onFailure(t);
          }

          @Override
          public void onSuccess(V v) {
            callback.onSuccess(v);
          }
        });
  }

  public <X extends Throwable> RpcFuture catching(
      Class<X> exceptionType, final Function<? super X, ? extends V> callback) {
    SettableRpcFuture<V> future = new SettableRpcFuture<>();
    future.impl.setFuture(
        Futures.catching(
            impl,
            exceptionType,
            new com.google.common.base.Function<X, V>() {
              @Override
              public V apply(X input) {
                return callback.apply(input);
              }
            }));
    return future;
  }
}
