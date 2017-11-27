/*
 * Copyright 2017, Google LLC All rights reserved.
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
 *     * Neither the name of Google LLC nor the names of its
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

import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.common.base.Throwables;
import com.google.common.collect.Queues;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import javax.annotation.Nonnull;

/**
 * A blocking Iterable-style wrapper around server stream responses.
 *
 * <p>This class asynchronously pulls responses from upstream via {@link
 * StreamController#request(int)} and exposes them via its Iterator. The implementation is back
 * pressure aware and uses a constant buffer of 1 item.
 *
 * <p>Please note that the stream can only be consumed once and must either be fully consumed or be
 * canceled.
 *
 * <p>This class is not thread safe.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * ServerStream<Item> stream = ...;
 *
 * for (Item item : stream) {
 *   System.out.println(item.id());
 *
 *   // Allow for early termination
 *   if (item.id().equals("needle")) {
 *     stream.cancel();
 *   }
 * }
 * }</pre>
 *
 * @param <V> The type of each response.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public final class ServerStream<V> implements Iterable<V> {
  private static final Object EOF_MARKER = new Object();

  private final QueuingResponseObserver<V> observer = new QueuingResponseObserver<>();
  private final ServerStreamIterator<V> iterator = new ServerStreamIterator<>(observer);
  private boolean consumed;

  @InternalApi("For use by ServerStreamingCallable only.")
  ServerStream() {}

  @InternalApi("For use by ServerStreamingCallable only.")
  ResponseObserver<V> observer() {
    return observer;
  }

  /** {@inheritDoc} */
  @Override
  @Nonnull
  public Iterator<V> iterator() {
    if (consumed) {
      throw new IllegalStateException("Iterator already consumed");
    }
    consumed = true;

    return iterator;
  }

  /**
   * Returns true if the next call to the iterator's hasNext() or next() is guaranteed to be
   * nonblocking.
   *
   * @return If the call on any of the iterator's methods is guaranteed to be nonblocking.
   */
  public boolean isReady() {
    return iterator.isReady();
  }

  /**
   * Cleanly cancels a partially consumed stream. The associated iterator will return false for the
   * hasNext() in the next iteration. This maintains the contract that an observed true from
   * hasNext() will yield an item in next(), but afterwards will return false.
   */
  public void cancel() {
    observer.cancel();
  }

  /**
   * Internal implementation of a blocking Iterator, which will coordinate with the
   * QueuingResponseObserver fetch new items from upstream. The Iterator expects the observer to
   * request the first item, afterwards, new items will be requested when the current ones are
   * consumed by next().
   *
   * @param <V> The type of items to be Iterated over.
   */
  private static final class ServerStreamIterator<V> implements Iterator<V> {
    private final QueuingResponseObserver<V> observer;
    private Object last;

    ServerStreamIterator(QueuingResponseObserver<V> observer) {
      this.observer = observer;
    }

    /**
     * Checks if the next call to {@code hasNext()} (and {@code next()}) will block.
     *
     * @return true if the next call is guaranteed to be nonblocking.
     */
    boolean isReady() {
      return last != null || observer.isReady();
    }

    /**
     * Consumes the next response and asynchronously request the next response from the observer.
     *
     * @return The next response.
     * @throws NoSuchElementException If the stream has been consumed or cancelled.
     */
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

    /**
     * Checks if the stream has been fully consumed or cancelled. This method will block until the
     * observer enqueues another event (response or completion event). If the observer encountered
     * an error, this method will propagte the error and put itself into an error where it will
     * always return the same error.
     *
     * @return true If iterator has more responses.
     */
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

    /** Unsupported. */
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * A back pressure aware bridge from a {@link ResponseObserver} to a {@link BlockingQueue}. The
   * queue size is fixed to 1 item & a close signal. The observer will manage it's own flow control
   * keeping the queue in one of 3 states:
   *
   * <ul>
   *   <li>empty: a item has been requested and we are awaiting the next item
   *   <li>1 item: an in progress stream with 1 item buffered
   *   <li>1 control signal: either a Throwable or an EOF_MARKER means that the stream is closed
   *   <li>1 item & 1 control signal: this is the last item of the stream
   * </ul>
   *
   * The observer can also be abruptly cancelled, which cancels the underlying call and always
   * returns an EOF_MARKER.
   *
   * @param <V> The item type.
   */
  private static final class QueuingResponseObserver<V> implements ResponseObserver<V> {
    private final BlockingQueue<Object> buffer = Queues.newArrayBlockingQueue(2);
    private StreamController controller;
    private boolean isCancelled;

    void request() {
      controller.request(1);
    }

    Object getNext() throws InterruptedException {
      if (isCancelled) {
        return EOF_MARKER;
      }
      return buffer.take();
    }

    boolean isReady() {
      return isCancelled || !buffer.isEmpty();
    }

    /**
     * Cancels the underlying RPC and causes getNext to always return EOF_MARKER. This can only be
     * called after starting the underlying call.
     */
    void cancel() {
      isCancelled = true;
      controller.cancel();
    }

    /**
     * Before starting the RPC, disable automatic flow control and retain a reference to the
     * controller.
     *
     * @param controller The controller for the stream.
     */
    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;
      controller.disableAutoInboundFlowControl();
      controller.request(1);
    }

    /**
     * Buffer the response. There should be at most 1 response in the buffer
     *
     * @param response The received response.
     */
    @Override
    public void onResponse(V response) {
      buffer.add(response);
    }

    /**
     * Enqueue the error to be thrown later on. The error might occur w/o a request so the queue
     * might grow to 2 elements, in that case the previous response will be consumed first.
     *
     * @param t The error occurred on the stream
     */
    @Override
    public void onError(Throwable t) {
      buffer.add(t);
    }

    /**
     * Enqueue a marker to notify the consumer that the stream is finished. In most situations this
     * will cause the queue to grow to 2 elements: the requested response and an unsolicited
     * completion marker.
     */
    @Override
    public void onComplete() {
      buffer.add(EOF_MARKER);
    }
  }
}
