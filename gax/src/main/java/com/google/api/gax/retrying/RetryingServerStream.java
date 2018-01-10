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
package com.google.api.gax.retrying;

import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StateCheckingResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.api.gax.rpc.Watchdog;
import com.google.common.base.Preconditions;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * The core logic for ServerStreaming retries.
 *
 * <p>Wraps a request, a {@link ResponseObserver} and an inner {@link ServerStreamingCallable} and
 * coordinates retries between them. When the inner callable throws an error, this class will
 * schedule retries using the configured {@link RetryAlgorithm}. The {@link RetryAlgorithm} behaves
 * slightly differently for streaming:
 *
 * <ul>
 *   <li>the attempts are reset as soon as a response is received.
 *   <li>rpc timeouts apply to the time interval between caller demanding more responses via {@link
 *       StreamController#request(int)} and the {@link ResponseObserver} receiving the message.
 *   <li>totalTimeout still applies to the entire stream.
 * </ul>
 *
 * <p>Streams can be resumed using a {@link StreamResumptionStrategy}. The {@link
 * StreamResumptionStrategy} is notified of incoming responses and is expected to track the progress
 * of the stream. Upon receiving an error, the {@link StreamResumptionStrategy} is asked to modify
 * the original request to resume the stream.
 */
@InternalApi("For internal use only")
public class RetryingServerStream<RequestT, ResponseT> {
  private final Object lock = new Object();

  private final ScheduledExecutorService executor;
  private final Watchdog<ResponseT> watchdog;
  private final ServerStreamingCallable<RequestT, ResponseT> innerCallable;
  private final TimedRetryAlgorithm retryAlgorithm;

  private final StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;
  private final RequestT initialRequest;
  private ApiCallContext context;

  private final ResponseObserver<ResponseT> outerObserver;

  // Start state
  private boolean autoFlowControl = true;
  private boolean isStarted;

  // Outer state
  private volatile Throwable cancellationCause;
  private int pendingRequests;

  // Internal retry state
  private StreamController innerController;
  private TimedAttemptSettings timedAttemptSettings;
  private boolean seenSuccessSinceLastError;

  public static <RequestT, ResponseT> Builder<RequestT, ResponseT> newBuilder() {
    return new Builder<>();
  }

  private RetryingServerStream(Builder<RequestT, ResponseT> builder) {
    this.executor = builder.executor;
    this.watchdog = builder.watchdog;
    this.innerCallable = builder.innerCallable;
    this.retryAlgorithm = builder.retryAlgorithm;

    this.initialRequest = builder.initialRequest;
    this.context = builder.context;
    this.outerObserver = builder.outerObserver;
    this.resumptionStrategy = builder.resumptionStrategy;
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
    // Propagate the totalTimeout as the overall stream deadline.
    Duration totalTimeout = timedAttemptSettings.getGlobalSettings().getTotalTimeout();
    if (totalTimeout != null) {
      context = context.withTimeout(totalTimeout);
    }

    // Call the inner callable
    callNextAttempt(initialRequest);
  }

  /**
   * Called when the outer {@link ResponseObserver} is ready for more data.
   *
   * @see StreamController#request(int)
   */
  private void onRequest(int count) {
    Preconditions.checkState(!autoFlowControl, "Automatic flow control is enabled");
    Preconditions.checkArgument(count > 0, "Count must be > 0");

    final StreamController localInnerController;

    synchronized (lock) {
      int maxInc = Integer.MAX_VALUE - pendingRequests;
      count = Math.min(maxInc, count);

      pendingRequests += count;
      localInnerController = this.innerController;
    }

    // Note: there is a race condition here where the count might go to the previous attempt's
    // StreamController after it failed. But it doesn't matter, because the controller will just
    // ignore it and the current controller will pick it up onStart.
    if (localInnerController != null) {
      localInnerController.request(count);
    }
  }

  /**
   * Called when the outer {@link ResponseObserver} wants to prematurely cancel the stream.
   *
   * @see StreamController#cancel()
   */
  private void onCancel() {
    StreamController localInnerController;

    synchronized (lock) {
      if (cancellationCause != null) {
        return;
      }
      cancellationCause = new CancellationException("User cancelled stream");
      localInnerController = innerController;
    }

    if (localInnerController != null) {
      localInnerController.cancel();
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

    Throwable localCancellationCause;
    int numToRequest = 0;

    synchronized (lock) {
      innerController = controller;

      localCancellationCause = this.cancellationCause;

      if (!autoFlowControl) {
        numToRequest = pendingRequests;
      }
    }

    if (localCancellationCause != null) {
      controller.cancel();
    } else if (numToRequest > 0) {
      controller.request(numToRequest);
    }
  }

  /**
   * Called by the inner {@link ServerStreamingCallable} when it received data. This will notify the
   * {@link StreamResumptionStrategy} and the outer {@link ResponseObserver}.
   *
   * @see ResponseObserver#onResponse(Object)
   */
  private void onAttemptResponse(ResponseT response) {
    synchronized (lock) {
      if (!autoFlowControl) {
        pendingRequests--;
      }
    }

    resumptionStrategy.onProgress(response);
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

    // Cancellations should not be retried.
    synchronized (lock) {
      if (cancellationCause != null) {
        shouldRetry = false;
        t = cancellationCause;
      }
    }

    if (shouldRetry && isRetryable(t)) {
      // If the error is retryable, update timing settings.
      timedAttemptSettings = nextAttemptSettings(shouldResetAttempts);
    } else {
      shouldRetry = false;
    }

    // Make sure that none of retry limits have been exhausted.
    shouldRetry = shouldRetry && retryAlgorithm.shouldRetry(timedAttemptSettings);

    // make sure that the StreamResumptionStrategy can resume the stream.
    final RequestT resumeRequest;
    if (shouldRetry) {
      resumeRequest = resumptionStrategy.getResumeRequest(initialRequest);
    } else {
      resumeRequest = null;
    }

    shouldRetry = shouldRetry && resumeRequest != null;

    if (shouldRetry) {
      executor.schedule(
          new Runnable() {
            @Override
            public void run() {
              callNextAttempt(resumeRequest);
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
  private void callNextAttempt(RequestT request) {
    innerCallable.call(
        request,
        watchdog.watch(
            new StateCheckingResponseObserver<ResponseT>() {
              @Override
              public void onStartImpl(StreamController controller) {
                onAttemptStart(controller);
              }

              @Override
              public void onResponseImpl(ResponseT response) {
                onAttemptResponse(response);
              }

              @Override
              public void onErrorImpl(Throwable t) {
                onAttemptError(t);
              }

              @Override
              public void onCompleteImpl() {
                onAttemptComplete();
              }
            },
            timedAttemptSettings.getRpcTimeout()),
        context);
  }

  /**
   * Creates a next attempt {@link TimedAttemptSettings} using the retryAlgorithm. When reset is
   * true, all properties (except the start time) be reset as if this is first retry attempt (i.e.
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

  public static class Builder<RequestT, ResponseT> {
    private ScheduledExecutorService executor;
    private Watchdog<ResponseT> watchdog;
    private ServerStreamingCallable<RequestT, ResponseT> innerCallable;
    private TimedRetryAlgorithm retryAlgorithm;
    private StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;
    private RequestT initialRequest;
    private ApiCallContext context;
    private ResponseObserver<ResponseT> outerObserver;

    public Builder<RequestT, ResponseT> setExecutor(ScheduledExecutorService executor) {
      this.executor = executor;
      return this;
    }

    public Builder<RequestT, ResponseT> setWatchdog(Watchdog<ResponseT> watchdog) {
      this.watchdog = watchdog;
      return this;
    }

    public Builder<RequestT, ResponseT> setInnerCallable(
        ServerStreamingCallable<RequestT, ResponseT> innerCallable) {
      this.innerCallable = innerCallable;
      return this;
    }

    public Builder<RequestT, ResponseT> setRetryAlgorithm(TimedRetryAlgorithm retryAlgorithm) {
      this.retryAlgorithm = retryAlgorithm;
      return this;
    }

    public Builder<RequestT, ResponseT> setResumptionStrategy(
        StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy) {
      this.resumptionStrategy = resumptionStrategy;
      return this;
    }

    public Builder<RequestT, ResponseT> setInitialRequest(RequestT initialRequest) {
      this.initialRequest = initialRequest;
      return this;
    }

    public Builder<RequestT, ResponseT> setContext(ApiCallContext context) {
      this.context = context;
      return this;
    }

    public Builder<RequestT, ResponseT> setOuterObserver(
        ResponseObserver<ResponseT> outerObserver) {
      this.outerObserver = outerObserver;
      return this;
    }

    public RetryingServerStream<RequestT, ResponseT> build() {
      return new RetryingServerStream<>(this);
    }
  }
}
