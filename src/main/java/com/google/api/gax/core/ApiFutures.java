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

import com.google.api.gax.core.internal.ApiFutureToListenableFuture;
import com.google.api.gax.core.internal.ListenableFutureToApiFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nullable;

/**
 * Static utility methods for the {@link ApiFuture} interface.
 */
public final class ApiFutures {
  private ApiFutures() {}

  public static <V> void addCallback(
      final ApiFuture<V> future, final ApiFutureCallback<? super V> callback) {
    Futures.addCallback(
        listenableFutureForApiFuture(future),
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

  public static <V, X extends Throwable> ApiFuture catching(
      ApiFuture<? extends V> input,
      Class<X> exceptionType,
      Function<? super X, ? extends V> callback) {
    ListenableFuture<V> catchingFuture =
        Futures.catching(
            listenableFutureForApiFuture(input),
            exceptionType,
            new GaxFunctionToGuavaFunction<X, V>(callback));
    return new ListenableFutureToApiFuture(catchingFuture);
  }

  public static <V> ApiFuture<V> immediateFuture(V value) {
    return new ListenableFutureToApiFuture<>(Futures.<V>immediateFuture(value));
  }

  public static <V> ApiFuture<V> immediateFailedFuture(Throwable throwable) {
    return new ListenableFutureToApiFuture<V>(Futures.<V>immediateFailedFuture(throwable));
  }

  public static <V, X> ApiFuture<X> transform(
      ApiFuture<? extends V> input, final Function<? super V, ? extends X> function) {
    return new ListenableFutureToApiFuture<>(
        Futures.transform(
            listenableFutureForApiFuture(input), new GaxFunctionToGuavaFunction<V, X>(function)));
  }

  private static <V> ListenableFuture<V> listenableFutureForApiFuture(ApiFuture<V> apiFuture) {
    ListenableFuture<V> listenableFuture;
    if (apiFuture instanceof AbstractApiFuture) {
      // prefer to use the wrapped ListenableFuture to reduce the number of layers
      listenableFuture = ((AbstractApiFuture) apiFuture).getInternalListenableFuture();
    } else {
      listenableFuture = new ApiFutureToListenableFuture(apiFuture);
    }
    return listenableFuture;
  }

  private static class GaxFunctionToGuavaFunction<X, V>
      implements com.google.common.base.Function<X, V> {
    private Function<? super X, ? extends V> f;

    public GaxFunctionToGuavaFunction(Function<? super X, ? extends V> f) {
      this.f = f;
    }

    @Nullable
    @Override
    public V apply(@Nullable X input) {
      return f.apply(input);
    }
  }
}
