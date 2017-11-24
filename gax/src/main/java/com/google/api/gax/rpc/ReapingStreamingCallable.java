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

import com.google.api.core.ApiClock;
import com.google.api.core.BetaApi;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Terminates idle streams. This middleware garbage collects idle streams in case the user forgot to
 * close a ServerStream or if a connection is reset and GRPC does not get notified. This prevents
 * the streams from hanging indefinitely.
 *
 * @param <RequestT> The type of the request.
 * @param <ResponseT> The type of the response.
 */
@BetaApi("The surface for streaming is not stable yet and may change in the future.")
public final class ReapingStreamingCallable<RequestT, ResponseT>
    extends ServerStreamingCallable<RequestT, ResponseT> {

  // Dummy value to convert the ConcurrentHashMap into a Set
  private static Object VALUE_MARKER = new Object();
  private final ConcurrentHashMap<Stream, Object> openStreams = new ConcurrentHashMap<>();

  private final ServerStreamingCallable<RequestT, ResponseT> upstream;
  private final ScheduledExecutorService executor;
  private final ApiClock clock;

  private final Duration checkInterval;
  private final Duration waitingTimeout;
  private final Duration idleTimeout;

  /**
   * Constructs a new
   *
   * @param upstream The stream that this middleware is protecting.
   * @param executor Used for scheduling idle check runs.
   * @param waitingTimeout How long to wait for the next response. This is used to detect when a
   *     server has gone away w/o notice.
   */
  ReapingStreamingCallable(
      ServerStreamingCallable<RequestT, ResponseT> upstream,
      ScheduledExecutorService executor,
      ApiClock clock,
      Duration waitingTimeout,
      Duration idleTimeout,
      Duration checkInterval) {

    Preconditions.checkNotNull(upstream, "upstream can't be null");

    Preconditions.checkNotNull(executor, "executor can't be null");
    Preconditions.checkNotNull(clock, "clock can't be null");

    Preconditions.checkNotNull(waitingTimeout, "waitingTimeout can't be null");
    Preconditions.checkNotNull(idleTimeout, "idleTimeout can't be null");
    Preconditions.checkNotNull(checkInterval, "checkInterval can't be null");

    Preconditions.checkArgument(
        waitingTimeout.compareTo(idleTimeout) < 0,
        "Request idle timeout must be longer than response waiting timeout.");

    Preconditions.checkArgument(
        checkInterval.compareTo(waitingTimeout) <= 0,
        "Check interval must be less than the timeouts to be able to honor them");

    this.upstream = upstream;
    this.executor = executor;
    this.clock = clock;
    this.waitingTimeout = waitingTimeout;
    this.idleTimeout = idleTimeout;
    this.checkInterval = checkInterval;
  }

  /** Schedules the timeout check thread. */
  void start() {
    executor.scheduleAtFixedRate(
        new Runnable() {
          @Override
          public void run() {
            checkAll();
          }
        },
        checkInterval.toMillis(),
        checkInterval.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void call(
      RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context) {
    Stream stream = new Stream(responseObserver);
    openStreams.put(stream, VALUE_MARKER);
    upstream.call(request, stream, context);
  }

  @VisibleForTesting
  void checkAll() {
    Iterator<Entry<Stream, Object>> it = openStreams.entrySet().iterator();

    while (it.hasNext()) {
      Stream stream = it.next().getKey();
      if (stream.cancelIfStale()) {
        it.remove();
      }
    }
  }

  enum State {
    /** Stream has been started, but doesn't have any outstanding requests. */
    IDLE,
    /** Stream is awaiting a response from upstream. */
    WAITING,
    /** Stream received a response from upstream, and is awaiting downstream processing. */
    DELIVERING
  }

  class Stream extends StreamController implements ResponseObserver<ResponseT> {

    private final Object lock = new Object();
    private boolean hasStarted;
    private boolean autoAutoFlowControl = true;

    private final ResponseObserver<ResponseT> downstream;
    private StreamController upstreamController;

    private State state = State.IDLE;
    private int pendingCount = 0;
    private long lastRequestAt;

    Stream(ResponseObserver<ResponseT> downstream) {
      this.downstream = downstream;
      lastRequestAt = clock.millisTime();
    }

    @Override
    public void disableAutoInboundFlowControl() {
      Preconditions.checkState(
          !hasStarted, "Can't disable automatic flow control after the stream has started");
      autoAutoFlowControl = false;
      upstreamController.disableAutoInboundFlowControl();
    }

    @Override
    public void onStart(StreamController controller) {
      Preconditions.checkState(!hasStarted, "Already started");

      this.upstreamController = controller;
      downstream.onStart(this);

      hasStarted = true;
    }

    @Override
    public void request(int count) {
      Preconditions.checkArgument(count > 0, "count must be > 0");
      Preconditions.checkState(!autoAutoFlowControl, "Auto flow control is enabled");

      // Only reset the request water mark if there is no outstanding requests.
      synchronized (lock) {
        if (state == State.IDLE) {
          state = State.WAITING;
          lastRequestAt = clock.millisTime();
        }

        // Increment the request count without overflow
        int maxIncrement = Integer.MAX_VALUE - pendingCount;
        count = Math.min(maxIncrement, count);
        pendingCount += count;
      }
      upstreamController.request(count);
    }

    @Override
    public void onResponse(ResponseT response) {
      synchronized (lock) {
        state = State.DELIVERING;
      }

      downstream.onResponse(response);

      synchronized (lock) {
        pendingCount--;
        if (autoAutoFlowControl || pendingCount > 0) {
          lastRequestAt = clock.millisTime();

          state = State.WAITING;
        } else {
          state = State.IDLE;
        }
      }
    }

    @Override
    public void cancel(Throwable cause) {
      upstreamController.cancel(cause);
    }

    @Override
    public void onError(Throwable t) {
      openStreams.remove(this);
      downstream.onError(t);
    }

    @Override
    public void onComplete() {
      openStreams.remove(this);
      downstream.onComplete();
    }

    /**
     * Checks if this stream has over run any of its timeouts and cancels it if it does.
     *
     * @return True if the stream was canceled.
     */
    boolean cancelIfStale() {
      Throwable error = null;

      synchronized (lock) {
        long waitTime = clock.millisTime() - lastRequestAt;

        switch (this.state) {
          case IDLE:
            if (waitTime >= idleTimeout.toMillis()) {
              error = new IdleConnectionException("Canceled due to idle connection");
            }
            break;
          case WAITING:
            if (waitTime >= waitingTimeout.toMillis()) {
              error =
                  new IdleConnectionException("Canceled due to timeout waiting for next response");
            }
            break;
        }
      }

      if (error != null) {
        upstreamController.cancel(error);
        return true;
      }
      return false;
    }
  }

  /**
   * The marker exception thrown when a timeout is exceeded in a {@link ReapingStreamingCallable}.
   */
  public static class IdleConnectionException extends RuntimeException {

    public IdleConnectionException(String message) {
      super(message);
    }
  }
}
