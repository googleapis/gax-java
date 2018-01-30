package com.google.api.gax.rpc;

import com.google.api.core.InternalApi;
import com.google.api.gax.retrying.NonCancellableFuture;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.api.gax.retrying.StreamResumptionStrategy;
import com.google.common.base.Preconditions;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import javax.annotation.concurrent.GuardedBy;
import org.threeten.bp.Duration;

@InternalApi
public class ServerStreamingAttemptCallable<RequestT, ResponseT> implements Callable<Void> {
  private final Object lock = new Object();
  private final ServerStreamingCallable<RequestT, ResponseT> innerCallable;

  private final StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy;
  private final RequestT initialRequest;
  private ApiCallContext context;

  private final ResponseObserver<ResponseT> outerObserver;

  // Start state
  private boolean autoFlowControl = true;
  private boolean isStarted;

  // Outer state
  @GuardedBy("lock")
  private Throwable cancellationCause;

  @GuardedBy("lock")
  private int pendingRequests;

  RetryingFuture<Void> externalFuture;

  // Internal retry state
  @GuardedBy("lock")
  private StreamController innerController;
  private boolean seenSuccessSinceLastError;

  public ServerStreamingAttemptCallable(
      ServerStreamingCallable<RequestT, ResponseT> innerCallable,
      StreamResumptionStrategy<RequestT, ResponseT> resumptionStrategy,
      RequestT initialRequest,
      ApiCallContext context,
      ResponseObserver<ResponseT> outerObserver) {
    this.innerCallable = innerCallable;
    this.resumptionStrategy = resumptionStrategy;
    this.initialRequest = initialRequest;
    this.context = context;
    this.outerObserver = outerObserver;
  }

  public void setExternalFuture(RetryingFuture<Void> externalFuture) {
    this.externalFuture = externalFuture;
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

    // Propagate the totalTimeout as the overall stream deadline.
    Duration totalTimeout = externalFuture.getAttemptSettings().getGlobalSettings().getTotalTimeout();
    if (totalTimeout != null) {
      context = context.withTimeout(totalTimeout);
    }

    // Call the inner callable
    call();
  }

  @Override
  public Void call() {
    Preconditions.checkState(isStarted, "Must be started first");

    RequestT request = resumptionStrategy.getResumeRequest(initialRequest);
    Preconditions.checkState(request != null, "ResumptionStrategy returned a null request.");

    final NonCancellableFuture<Void> attemptFuture = new NonCancellableFuture<>();
    seenSuccessSinceLastError = false;

    // TODO: watchdog
    innerCallable.call(
        request,
        new StateCheckingResponseObserver<ResponseT>() {
          @Override
          public void onStartImpl(StreamController controller) {
            onAttemptStart(controller);
          }

          @Override
          public void onResponseImpl(ResponseT response) {
            seenSuccessSinceLastError = true;
            resumptionStrategy.onProgress(response);
            outerObserver.onResponse(response);
          }

          @Override
          public void onErrorImpl(Throwable t) {
            if (cancellationCause != null) {
              attemptFuture.setExceptionPrivately(cancellationCause);
            } else {
              attemptFuture.setExceptionPrivately(
                  new WrappedApiException(resumptionStrategy.canResume(), seenSuccessSinceLastError, t)
              );
            }
          }

          @Override
          public void onCompleteImpl() {
            attemptFuture.setPrivately(null);
          }
        },
        context);

    externalFuture.setAttemptFuture(attemptFuture);

    return null;
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
      cancellationCause = new WrappedCancellationException("User cancelled stream");
      localInnerController = innerController;
    }

    if (localInnerController != null) {
      localInnerController.cancel();
    }
  }

  public static class WrappedCancellationException extends RuntimeException {
    public WrappedCancellationException(String message) {
      super(new CancellationException(message));
    }
  }

  public static class WrappedApiException extends RuntimeException {
    private final boolean canResume;
    private final boolean seenSuccessSinceLastError;

    public WrappedApiException(boolean seenSuccessSinceLastError, boolean canResume, Throwable t) {
      super(t);
      this.canResume = canResume;
      this.seenSuccessSinceLastError = seenSuccessSinceLastError;
    }

    public boolean canResume() {
      return canResume;
    }

    public boolean hasSeenSuccessSinceLastError() {
      return seenSuccessSinceLastError;
    }
  }
}

