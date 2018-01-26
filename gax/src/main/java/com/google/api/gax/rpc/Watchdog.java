/*
 * Copyright 2018, Google LLC All rights reserved.
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
import com.google.api.core.InternalApi;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.GuardedBy;
import org.threeten.bp.Duration;

/**
 * Prevents the streams from hanging indefinitely. This middleware garbage collects idle streams in
 * case the user forgot to close a ServerStream or if a connection is reset and GRPC does not get
 * notified.
 *
 * <p>For every {@code checkInterval}, this class checks two thresholds:
 *
 * <ul>
 *   <li>waitingTimeout: the amount of time to wait for a response (after the caller signaled
 *       demand) before forcefully closing the stream. Duration.ZERO disables the timeout.
 *   <li>idleTimeout: the amount of time to wait before assuming that the caller forgot to close the
 *       stream and forcefully closing the stream. This is measured from the last time the caller
 *       had no outstanding demand. Duration.ZERO disables the timeout.
 * </ul>
 *
 * @param <ResponseT> The type of the response.
 */
@InternalApi
public class Watchdog<ResponseT> {
  // Dummy value to convert the ConcurrentHashMap into a Set
  private static Object VALUE_MARKER = new Object();
  private final ConcurrentHashMap<WatchdogStream, Object> openStreams = new ConcurrentHashMap<>();

  private final ScheduledExecutorService executor;
  private final ApiClock clock;
  private final Duration checkInterval;
  private final Duration idleTimeout;

  public Watchdog(
      ScheduledExecutorService executor,
      ApiClock clock,
      Duration checkInterval,
      Duration idleTimeout) {

    Preconditions.checkNotNull(executor, "executor can't be null");
    Preconditions.checkNotNull(clock, "clock can't be null");
    Preconditions.checkNotNull(checkInterval, "checkInterval can't be null");
    Preconditions.checkNotNull(idleTimeout, "checkInterval can't be null");

    Preconditions.checkArgument(
        Duration.ZERO.compareTo(checkInterval) < 0, "checkInterval must be > 0");

    Preconditions.checkArgument(
        Duration.ZERO.compareTo(idleTimeout) <= 0, "idleTimeout must be >= 0");

    this.executor = executor;
    this.clock = clock;
    this.checkInterval = checkInterval;
    this.idleTimeout = idleTimeout;
  }

  /** Schedules the timeout check thread. */
  public void start() {
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

  /** Wraps the target observer with timing constraints. */
  public ResponseObserver<ResponseT> watch(
      ResponseObserver<ResponseT> innerObserver, Duration waitTimeout) {
    Preconditions.checkNotNull(innerObserver, "innerObserver can't be null");
    Preconditions.checkArgument(Duration.ZERO.compareTo(waitTimeout) <= 0, "waitTimeout must >= 0");

    WatchdogStream stream = new WatchdogStream(innerObserver, waitTimeout);
    openStreams.put(stream, VALUE_MARKER);
    return stream;
  }

  @VisibleForTesting
  void checkAll() {
    Iterator<Entry<WatchdogStream, Object>> it = openStreams.entrySet().iterator();

    while (it.hasNext()) {
      WatchdogStream stream = it.next().getKey();
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
    /**
     * Stream received a response from upstream, and is awaiting outerResponseObserver processing.
     */
    DELIVERING
  }

  class WatchdogStream extends StateCheckingResponseObserver<ResponseT> {
    private final Object lock = new Object();

    private final Duration waitTimeout;
    private boolean hasStarted;
    private boolean autoAutoFlowControl = true;

    private final ResponseObserver<ResponseT> outerResponseObserver;
    private StreamController innerController;

    @GuardedBy("lock")
    private State state = State.IDLE;

    @GuardedBy("lock")
    private int pendingCount = 0;

    @GuardedBy("lock")
    private long lastActivityAt = clock.millisTime();

    private volatile Throwable error;

    WatchdogStream(ResponseObserver<ResponseT> responseObserver, Duration waitTimeout) {
      this.waitTimeout = waitTimeout;
      this.outerResponseObserver = responseObserver;
    }

    @Override
    public void onStartImpl(StreamController controller) {
      this.innerController = controller;
      outerResponseObserver.onStart(
          new StreamController() {
            @Override
            public void disableAutoInboundFlowControl() {
              Preconditions.checkState(
                  !hasStarted, "Can't disable automatic flow control after the stream has started");
              autoAutoFlowControl = false;
              innerController.disableAutoInboundFlowControl();
            }

            @Override
            public void request(int count) {
              WatchdogStream.this.onRequest(count);
            }

            @Override
            public void cancel() {
              WatchdogStream.this.onCancel();
            }
          });

      hasStarted = true;
    }

    private void onRequest(int count) {
      Preconditions.checkArgument(count > 0, "count must be > 0");
      Preconditions.checkState(!autoAutoFlowControl, "Auto flow control is enabled");

      // Only reset the request water mark if there are no outstanding requests.
      synchronized (lock) {
        if (state == State.IDLE) {
          state = State.WAITING;
          lastActivityAt = clock.millisTime();
        }

        // Increment the request count without overflow
        int maxIncrement = Integer.MAX_VALUE - pendingCount;
        count = Math.min(maxIncrement, count);
        pendingCount += count;
      }
      innerController.request(count);
    }

    private void onCancel() {
      error = new CancellationException("User cancelled stream");
      innerController.cancel();
    }

    @Override
    public void onResponseImpl(ResponseT response) {
      synchronized (lock) {
        state = State.DELIVERING;
      }

      outerResponseObserver.onResponse(response);

      synchronized (lock) {
        pendingCount--;
        lastActivityAt = clock.millisTime();

        if (autoAutoFlowControl || pendingCount > 0) {
          state = State.WAITING;
        } else {
          state = State.IDLE;
        }
      }
    }

    @Override
    public void onErrorImpl(Throwable t) {
      // Overlay the cancellation errors (either user or idle)
      if (this.error != null) {
        t = this.error;
      }
      openStreams.remove(this);
      outerResponseObserver.onError(t);
    }

    @Override
    public void onCompleteImpl() {
      openStreams.remove(this);
      outerResponseObserver.onComplete();
    }

    /**
     * Checks if this stream has overrun any of its timeouts and cancels it if it does.
     *
     * @return True if the stream was canceled.
     */
    boolean cancelIfStale() {
      Throwable myError = null;

      synchronized (lock) {
        long waitTime = clock.millisTime() - lastActivityAt;

        switch (this.state) {
          case IDLE:
            if (!idleTimeout.isZero() && waitTime >= idleTimeout.toMillis()) {
              myError = new IdleConnectionException("Canceled due to idle connection", false);
            }
            break;
          case WAITING:
            if (!waitTimeout.isZero() && waitTime >= waitTimeout.toMillis()) {
              myError =
                  new IdleConnectionException(
                      "Canceled due to timeout waiting for next response", true);
            }
            break;
        }
      }

      if (myError != null) {
        this.error = myError;
        innerController.cancel();
        return true;
      }
      return false;
    }
  }

  /** The marker exception thrown when a timeout is exceeded. */
  public static class IdleConnectionException extends ApiException {
    private static final long serialVersionUID = -777463630112442085L;

    IdleConnectionException(String message, boolean retry) {
      super(message, null, LOCAL_ABORTED_STATUS_CODE, retry);
    }
  }

  public static final StatusCode LOCAL_ABORTED_STATUS_CODE =
      new StatusCode() {
        @Override
        public Code getCode() {
          return Code.ABORTED;
        }

        @Override
        public Object getTransportCode() {
          return null;
        }
      };
}
