package com.google.api.gax.retrying;

import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ServerStreamingAttemptCallable;
import java.util.concurrent.CancellationException;

/**
 *
 * @param <ResponseT>
 */
@InternalApi("For internal use only")
public class StreamingRetryAlgorithm<ResponseT> extends RetryAlgorithm<ResponseT> {
  public StreamingRetryAlgorithm(
      ResultRetryAlgorithm<ResponseT> resultAlgorithm,
      TimedRetryAlgorithm timedAlgorithm) {
    super(resultAlgorithm, timedAlgorithm);
  }

  @Override
  public TimedAttemptSettings createNextAttempt(Throwable prevThrowable, ResponseT prevResponse,
      TimedAttemptSettings prevSettings) {

    if (prevThrowable instanceof ServerStreamingAttemptCallable.WrappedApiException) {
      ServerStreamingAttemptCallable.WrappedApiException wrapper = (ServerStreamingAttemptCallable.WrappedApiException) prevThrowable;

      // Unwrap
      prevThrowable = prevThrowable.getCause();

      // If we have made progress in the last attempt, then reset the delays
      if (wrapper.hasSeenSuccessSinceLastError()) {
        prevSettings = createFirstAttempt().toBuilder()
            .setFirstAttemptStartTimeNanos(prevSettings.getFirstAttemptStartTimeNanos())
            .build();
      }
    }

    return super.createNextAttempt(prevThrowable, prevResponse, prevSettings);
  }

  @Override
  public boolean shouldRetry(Throwable prevThrowable, ResponseT prevResponse,
      TimedAttemptSettings nextAttemptSettings) throws CancellationException {

    // Unwrap
    if (prevThrowable instanceof ServerStreamingAttemptCallable.WrappedApiException) {
      prevThrowable = prevThrowable.getCause();
    }
    return super.shouldRetry(prevThrowable, prevResponse, nextAttemptSettings);
  }
}
