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

import com.google.api.core.BetaApi;
import com.google.api.gax.core.SequentialExecutor;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mediates message flow between 2 {@link ResponseObserver}s. Its intended for situations when a
 * stream needs to be transformed in such a way where the incoming responses do not map 1:1 to the
 * output responses.
 *
 * <p>It manages back pressure between M upstream responses represent N downstream responses. This
 * class buffers responses when M &gt; N and spools them when M &lt; N. The downstream responses
 * will be delivered via either the upstream thread or the downstream thread that called request(),
 * in either case, the downstream methods will be invoked sequentially. Neither the downstream
 * {@link ResponseObserver} nor the {@link Delegate} need to be threadsafe.
 *
 * <p>Expected usage:
 *
 * <pre>{@code
 *  class MyServerStreamingCallable extends ServerStreamingCallable<Request, FullResponse> {
 *    private final ServerStreamingCallable<Request, Chunk> upstream;
 *
 *    MyServerStreamingCallable(ServerStreamingCallable<Request, Chunk> upstream) {
 *      this.upstream = upstream;
 *    }
 *
 *    public void call(Request request, ResponseObserver<FullResponse> downstreamObserver,
 * ApiCallContext context) {
 *      Delegate<Chunk, FullResponse> myDelegate = new MyDelegate();
 *      upstream.call(request, new StreamMediator(myDelegate, downstreamObserver),
 * context);
 *    }
 *  }
 * }</pre>
 */
@BetaApi
public class StreamMediator<UpstreamResponseT, DownstreamResponseT> extends StreamController
    implements ResponseObserver<UpstreamResponseT> {

  public static class IncompleteStreamException extends RuntimeException {

    IncompleteStreamException() {
      super("Upstream closed too early leaving an incomplete response.");
    }
  }

  private static final Logger LOGGER = Logger.getLogger(StreamMediator.class.getName());

  /**
   * Interface for the business logic of the stream transformation. Implementations don't need to be
   * thread safe or be concerned with back pressure, both of those responsibilities are handled by
   * the StreamMediator.
   *
   * @param <UpstreamT> The type of responses coming from the usptream ServerStreamingCallable.
   * @param <DownstreamT> The type of responses the downstream {@link ResponseObserver} expects.
   */
  public interface Delegate<UpstreamT, DownstreamT> {

    void push(UpstreamT response);

    boolean hasFullResponse();

    boolean hasPartialResponse();

    DownstreamT pop();
  }

  private final SequentialExecutor reactor;

  // The pipeline
  private StreamController upstreamController;
  private final Delegate<UpstreamResponseT, DownstreamResponseT> delegate;
  private final ResponseObserver<DownstreamResponseT> downstreamObserver;

  // Downstream state
  private int numPending;
  private final AtomicReference<Throwable> cancellationRequest = new AtomicReference<>();

  // Own state
  private boolean autoFlowControl;
  private boolean started;
  private volatile Thread deliveryThread;
  private boolean awaitingUpstream;
  private boolean done;

  // Upstream state
  private boolean closeOnDone;
  private Throwable closeError;

  /**
   * Constructs a {@link StreamMediator} that will pass upstream responses through the delegate
   * before delivering them to the downstreamObserver.
   *
   * @param delegate The business logic of how to manipulate the responses.
   * @param downstreamObserver The {@link ResponseObserver} to notify of the delegate's output.
   */
  public StreamMediator(
      Delegate<UpstreamResponseT, DownstreamResponseT> delegate,
      ResponseObserver<DownstreamResponseT> downstreamObserver) {
    reactor = new SequentialExecutor(MoreExecutors.directExecutor());
    autoFlowControl = true;

    this.delegate = delegate;
    this.downstreamObserver = downstreamObserver;
  }

  /**
   * Callback that will be notified when the upstream callable starts. This will in turn notify the
   * downstreamObserver of stream start. Regardless of the downstreamObserver, the upstream
   * controller will be put into manual flow control.
   *
   * @param controller The controller for the upstream stream.
   */
  @Override
  public void onStart(StreamController controller) {
    Preconditions.checkState(!started, "Already started");

    upstreamController = controller;
    upstreamController.disableAutoInboundFlowControl();
    downstreamObserver.onStart(this);
    started = true;

    if (autoFlowControl) {
      reactor.execute(
          new Runnable() {
            @Override
            public void run() {
              numPending = Integer.MAX_VALUE;
              deliver();
            }
          });
    }
  }

  /** {@inheritDoc} */
  @Override
  public void disableAutoInboundFlowControl() {
    Preconditions.checkState(
        !started, "Flow control can't be disabled after the stream has started");

    autoFlowControl = false;
  }

  /**
   * Request n responses to be delivered to the downstream {@link
   * ResponseObserver#onResponse(Object)}. This method might synchronously deliver the messages if
   * they have already been buffered. Or it will deliver them asynchronously if they need to be
   * requested from upstream.
   *
   * @param n The maximum number of responsees to deliver
   */
  @Override
  public void request(final int n) {
    Preconditions.checkState(!autoFlowControl, "Auto flow control is currently enabled");

    // Fast path: the current thread is in the delivery loop,
    // so we can skip the queue & just increment the count.
    // Or the stream hasn't started yet, so we just increment the count.
    if (!started || deliveryThread == Thread.currentThread()) {
      numPending = safeAdd(numPending, n);
      return;
    }

    reactor.execute(
        new Runnable() {
          @Override
          public void run() {
            numPending = safeAdd(numPending, n);
            deliver();
          }
        });
  }

  /**
   * Cancels the stream and notifies the downstream {@link ResponseObserver#onError(Throwable)}.
   * This method can be called multiple times, but only the first time has any effect. Please note
   * that there is a race condition between cancellation and the stream completing normally. Please
   * note that you can only specify a message or a cause, not both.
   *
   * @param cause A user supplied error to use when cancelling.
   */
  // TODO: remove message parameter
  @Override
  public void cancel(final Throwable cause) {
    Preconditions.checkNotNull(cause, "Cause can't be null");

    cancellationRequest.compareAndSet(null, cause);

    if (!started) {
      return;
    }

    reactor.execute(
        new Runnable() {
          @Override
          public void run() {
            upstreamController.cancel(cause);
            deliver();
          }
        });
  }

  /**
   * Process a new response from upstream. The message will be fed to the delegate and the output
   * will be delivered to the downstream {@link ResponseObserver}.
   *
   * <p>If the delivery loop is stopped, this will restart it.
   */
  @Override
  public void onResponse(final UpstreamResponseT response) {
    Preconditions.checkState(started, "Upstream called onResponse() before onStart()");

    reactor.execute(
        new Runnable() {
          @Override
          public void run() {
            awaitingUpstream = false;
            try {
              delegate.push(response);
            } catch (RuntimeException e) {
              LOGGER.log(
                  Level.WARNING, "Delegate did not accept the last message, cancelling stream", e);
              cancellationRequest.compareAndSet(null, e);
            }
            deliver();
          }
        });
  }

  /**
   * Process upstream's onError notification. This will be queued to be delivered after the delegate
   * exhausts it's buffers.
   *
   * <p>If the delivery loop is stopped, this will restart it.
   */
  @Override
  public void onError(final Throwable throwable) {
    Preconditions.checkState(started, "Upstream called onError() before onStart()");

    reactor.execute(
        new Runnable() {
          @Override
          public void run() {
            closeOnDone = true;
            closeError = throwable;
            deliver();
          }
        });
  }

  /**
   * Process upstream's onComplete notification. This will be queued to be delivered after the
   * delegate exhausts it's buffers.
   *
   * <p>If the delivery loop is stopped, this will restart it.
   */
  @Override
  public void onComplete() {
    Preconditions.checkState(started, "Upstream called onComplete() before onStart()");

    reactor.execute(
        new Runnable() {
          @Override
          public void run() {
            closeOnDone = true;
            deliver();
          }
        });
  }

  /** Tries to kick off the delivery loop, wrapping it in error handling. */
  private void deliver() {
    if (deliveryThread != null) {
      return;
    }
    deliveryThread = Thread.currentThread();

    try {
      unsafeDeliver();
    } catch (Throwable t) {
      LOGGER.log(Level.WARNING, "Caught exception during delivery. Cancelling stream", t);
      if (!cancellationRequest.compareAndSet(null, t)) {
        LOGGER.log(Level.WARNING, "Stream is already cancelled, swallowing the error");
      }

      // If this throws, then we'll let the SequentialExecutor deal with it
      unsafeDeliver();
    } finally {
      deliveryThread = null;
    }
  }

  /**
   * Coordinates state transfer between downstream and upstream. It orchestrates the flow of demand
   * from downstream to upstream. The data flow from upstream through the delegate to downstream.
   * It's back pressure aware and will only send as many messages that were requested. However it
   * will send unsolicited onComplete & onError messages.
   *
   * <p>This method is not thread safe and expects to be run in the {@link SequentialExecutor}.
   */
  private void unsafeDeliver() {
    if (done) {
      return;
    }

    while (cancellationRequest.get() == null && numPending > 0 && delegate.hasFullResponse()) {
      if (!autoFlowControl) {
        numPending--;
      }
      downstreamObserver.onResponse(delegate.pop());
    }

    // Check for early cancellation
    if (cancellationRequest.get() != null) {
      done = true;
      downstreamObserver.onError(cancellationRequest.get());
      return;
    }

    // If we have demand and the upstream is not exhausted and we don't have any outstanding
    // upstream requests, then request more data upstream.
    if (numPending > 0 && !awaitingUpstream && !closeOnDone) {
      awaitingUpstream = true;
      upstreamController.request(1);
      return;
    }

    // Both the delegate & the upstream are exhausted, notify downstream of end,
    // taking care treat orphaned data in the delegate as an error.
    if (!delegate.hasFullResponse() && closeOnDone) {
      done = true;
      if (closeError != null) {
        downstreamObserver.onError(closeError);
      } else if (delegate.hasPartialResponse()) {
        downstreamObserver.onError(new IncompleteStreamException());
      } else {
        downstreamObserver.onComplete();
      }
      return;
    }
  }

  /** Add 2 ints together while taking take to avoid integer overflow. */
  private static int safeAdd(int n1, int n2) {
    int maxN2 = Integer.MAX_VALUE - n1;
    n2 = Math.min(maxN2, n2);
    return n1 + n2;
  }
}
