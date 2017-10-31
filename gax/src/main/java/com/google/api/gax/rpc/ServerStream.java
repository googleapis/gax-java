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
package com.google.api.gax.rpc;

import com.google.common.base.Throwables;
import com.google.common.collect.Queues;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;

public class ServerStream<V> implements Iterable<V> {
  private static final Object EOF_MARKER = new Object();

  private QueuingResponseObserver<V> observer = new QueuingResponseObserver<>();
  private ServerStreamIterator<V> iterator = new ServerStreamIterator<>(observer);
  private boolean consumed;

  // Package private constructor
  ServerStream() {}

  ResponseObserver<V> observer() {
    return observer;
  }

  @Override
  public Iterator<V> iterator() {
    if (consumed) {
      throw new IllegalStateException("Iterator already consumed");
    }
    consumed = true;

    return iterator;
  }

  public void cancel() {
    observer.cancel();
  }

  public static class ServerStreamIterator<V> implements Iterator<V> {
    private final QueuingResponseObserver<V> observer;
    private Object last;

    ServerStreamIterator(QueuingResponseObserver<V> observer) {
      this.observer = observer;
    }

    @Override
    public V next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      try {
        observer.request();
        @SuppressWarnings("unchecked")
        V tmp = (V) last;
        return tmp;
      } finally {
        last = null;
      }
    }

    @Override
    public boolean hasNext() {
      if (last == null) {
        try {
          last = observer.getNext();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
      if (last instanceof Throwable) {
        Throwable throwable = (Throwable) this.last;

        Throwables.throwIfUnchecked(throwable);
        throw new RuntimeException(throwable);
      }
      return last != EOF_MARKER;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  static class QueuingResponseObserver<V> implements ResponseObserver<V> {
    private ArrayBlockingQueue<Object> buffer = Queues.newArrayBlockingQueue(2);
    private volatile StreamController controller;
    private volatile boolean isCancelled;

    void request() {
      controller.request(1);
    }

    Object getNext() throws InterruptedException {
      if (isCancelled) {
        return EOF_MARKER;
      }
      return buffer.take();
    }

    void cancel() {
      isCancelled = true;

      if (controller != null) {
        controller.cancel();
      }
    }

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;
      controller.disableAutoInboundFlowControl();

      if (isCancelled) {
        controller.cancel();
      } else {
        controller.request(1);
      }
    }

    @Override
    public void onResponse(V response) {
      buffer.add(response);
    }

    @Override
    public void onError(Throwable t) {
      buffer.add(t);
    }

    @Override
    public void onComplete() {
      buffer.add(EOF_MARKER);
    }
  }
}
