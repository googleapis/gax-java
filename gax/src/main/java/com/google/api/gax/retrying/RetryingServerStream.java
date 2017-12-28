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
package com.google.api.gax.retrying;

import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StreamController;
import com.google.common.base.Preconditions;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The core logic for ServerStreaming retries.
 *
 * <p>Wraps a request, a {@link ResponseObserver} and an inner {@link ServerStreamingCallable} and
 * coordinates retries between them. When inner callable throws an error, this class will schedule
 * retries using the configured {@link RetryAlgorithm}. The {@link RetryAlgorithm} behaves slightly
 * differently for streaming: the attempts are reset as soon as a response is received and rpc
 * timeouts are ignored.
 *
 * <p>Streams can be resumed using a {@link StreamTracker}. The {@link StreamTracker} is notified of
 * incoming responses is expected to track the progress of the stream. Upon receiving an error, the
 * {@link StreamTracker} is asked to modify the original request to resume the stream.
 */
@InternalApi("For internal use only")
public class RetryingServerStream<RequestT, ResponseT> {
  private final Object lock = new Object();

  private final ScheduledExecutorService executor;
  private final ServerStreamingCallable<RequestT, ResponseT> innerCallable;
  private final TimedRetryAlgorithm retryAlgorithm;

  private final StreamTracker<RequestT, ResponseT> streamTracker;
  private final RequestT initialRequest;
  private final ApiCallContext context;

  private final ResponseObserver<ResponseT> outerObserver;

  // Start state
  private boolean autoFlowControl = true;
  private boolean isStarted;

  // Outer state
  private volatile Throwable cancellationCause;
  private int pendingRequests;

  // Internal retry state
  private StreamController currentInnerController;
  private TimedAttemptSettings timedAttemptSettings;
  private boolean seenSuccessSinceLastError;

  public RetryingServerStream(
      ScheduledExecutorService executor,
      ServerStreamingCallable<RequestT, ResponseT> innerCallable,
      TimedRetryAlgorithm retryAlgorithm,
      StreamTracker<RequestT, ResponseT> streamTracker,
      RequestT initialRequest,
      ApiCallContext context,
      ResponseObserver<ResponseT> outerObserver) {
    this.executor = executor;
    this.innerCallable = innerCallable;
    this.retryAlgorithm = retryAlgorithm;

    this.initialRequest = initialRequest;
    this.context = context;
    this.outerObserver = outerObserver;
    this.streamTracker = streamTracker;
  }

  /**
   * Starts the initial call. The call is attempted on the caller's thread. Further call attempts
   * will be made by the executor.
   */
  public void start() {
    Preconditions.checkState(!isStarted, "Already started");

    // Initialize the outer observer
    outerObserver.onStart(
        new StreamController() {
          @Override
          public void disableAutoInboundFlowControl() {
            Preconditions.checkState(
                !isStarted, "Can't disable auto flow control once the stream is started");
            autoFlowControl = false;
          }

          @Override
          public void request(int count) {
            onRequest(count);
          }

          @Override
          public void cancel() {
            onCancel();
          }
        });
    isStarted = true;
    if (autoFlowControl) {
      pendingRequests = Integer.MAX_VALUE;
    }
    timedAttemptSettings = retryAlgorithm.createFirstAttempt();

    // Call the inner callable
    callNextAttempt();
  }

  /**
   * Called when the outer {@link ResponseObserver} is ready for more data.
   *
   * @see StreamController#request(int)
   */
  private void onRequest(int count) {
    Preconditions.checkState(!autoFlowControl, "Automatic flow control is enabled");
    Preconditions.checkArgument(count > 0, "Count must be > 0");

    final StreamController currentUpstreamController;

    synchronized (lock) {
      int maxInc = Integer.MAX_VALUE - pendingRequests;
      count = Math.min(maxInc, count);

      pendingRequests += count;
      currentUpstreamController = this.currentInnerController;
    }

    // Note: there is a race condition here where the count might go to the previous attempt's
    // StreamController after it failed. But it doesn't matter, because the controller will just
    // ignore it and the current controller will pick it up onStart.
    if (currentUpstreamController != null) {
      currentUpstreamController.request(count);
    }
  }

  /**
   * Called when the outer {@link ResponseObserver} wants to prematurely cancel the stream.
   *
   * @see StreamController#cancel()
   */
  private void onCancel() {
    StreamController upstreamController;

    synchronized (lock) {
      if (cancellationCause != null) {
        return;
      }
      cancellationCause = new CancellationException("User cancelled stream");
      upstreamController = currentInnerController;
    }

    if (upstreamController != null) {
      upstreamController.cancel();
    }
  }

  /**
   * Called by the inner {@link ServerStreamingCallable} when the call is about to start. This will
   * transfer unfinished state from the previous attempt.
   *
   * @see ResponseObserver#onStart(StreamController)
   */
  private void onAttemptStart(StreamController controller) {
    if (!autoFlowControl) {
      controller.disableAutoInboundFlowControl();
    }

    Throwable cancellationRequest;
    int numToRequest = 0;

    synchronized (lock) {
      currentInnerController = controller;

      cancellationRequest = this.cancellationCause;

      if (!autoFlowControl) {
        numToRequest = pendingRequests;
      }
    }

    if (cancellationRequest != null) {
      controller.cancel();
    } else if (numToRequest > 0) {
      controller.request(numToRequest);
    }
  }

  /**
   * Called by the inner {@link ServerStreamingCallable} when it received data. This will notify the
   * {@link StreamTracker} and the outer {@link ResponseObserver}.
   *
   * @see ResponseObserver#onResponse(Object)
   */
  private void onAttemptResponse(ResponseT response) {
    synchronized (lock) {
      if (!autoFlowControl) {
        pendingRequests--;
      }
    }

    streamTracker.onProgress(response);
    seenSuccessSinceLastError = true;
    outerObserver.onResponse(response);
  }

  /**
   * Called by the inner {@link ServerStreamingCallable} when an error is encountered. This method
   * try to schedule a new attempt using executor.
   *
   * @see ResponseObserver#onError(Throwable)
   */
  private void onAttemptError(Throwable t) {
    final boolean shouldResetAttempts = seenSuccessSinceLastError;
    seenSuccessSinceLastError = false;

    boolean shouldRetry = true;

    // cancellations should not be retried
    synchronized (lock) {
      if (cancellationCause != null) {
        shouldRetry = false;
        t = cancellationCause;
      }
    }

    if (shouldRetry && isRetryable(t)) {
      // If the error is retryable, update timing settings
      timedAttemptSettings = nextAttemptSettings(shouldResetAttempts);
    } else {
      shouldRetry = false;
    }

    // make sure that none of retry limits have been exhausted
    shouldRetry = shouldRetry && retryAlgorithm.shouldRetry(timedAttemptSettings);

    if (shouldRetry) {
      executor.schedule(
          new Runnable() {
            @Override
            public void run() {
              callNextAttempt();
            }
          },
          timedAttemptSettings.getRandomizedRetryDelay().getNano(),
          TimeUnit.NANOSECONDS);
    } else {
      outerObserver.onError(t);
    }
  }

  /**
   * Called by the inner {@link ServerStreamingCallable} when the stream is complete. The outer
   * {@link ResponseObserver} is notified.
   */
  private void onAttemptComplete() {
    outerObserver.onComplete();
  }

  /** Schedules the next call attmept. */
  private void callNextAttempt() {
    RequestT resumeRequest = streamTracker.getResumeRequest(initialRequest);

    innerCallable.call(
        resumeRequest,
        new ResponseObserver<ResponseT>() {
          @Override
          public void onStart(StreamController controller) {
            onAttemptStart(controller);
          }

          @Override
          public void onResponse(ResponseT response) {
            onAttemptResponse(response);
          }

          @Override
          public void onError(Throwable t) {
            onAttemptError(t);
          }

          @Override
          public void onComplete() {
            onAttemptComplete();
          }
        },
        context);
  }

  /**
   * Creates a next attempt {@link TimedAttemptSettings} using the retryAlgorithm. When reset is
   * true, all properties (except the start time) be reset as if this is first retry attempt (ie.
   * second request).
   */
  private TimedAttemptSettings nextAttemptSettings(boolean reset) {
    TimedAttemptSettings currentAttemptSettings = timedAttemptSettings;

    if (reset) {
      TimedAttemptSettings firstAttempt = retryAlgorithm.createFirstAttempt();

      currentAttemptSettings =
          firstAttempt
              .toBuilder()
              .setFirstAttemptStartTimeNanos(currentAttemptSettings.getFirstAttemptStartTimeNanos())
              .build();
    }

    return retryAlgorithm.createNextAttempt(currentAttemptSettings);
  }

  private boolean isRetryable(Throwable t) {
    return t instanceof ApiException && ((ApiException) t).isRetryable();
  }
}
