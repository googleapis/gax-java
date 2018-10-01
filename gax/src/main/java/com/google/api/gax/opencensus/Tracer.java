package com.google.api.gax.opencensus;

import com.google.api.core.InternalExtensionOnly;
import com.google.api.gax.retrying.RetryingFuture;
import org.threeten.bp.Duration;

@InternalExtensionOnly
public interface Tracer {
  void operationStarted();
  void operationFailed(Throwable throwable);
  void operationSucceeded();

  void connectionSelected(int id);
  void credentialsRefreshed();

  void startAttempt();
  void attemptSucceeded();

  void retryableFailure(Throwable error, Duration delay);
  void retriesExhausted();
  void permanentFailure(Throwable error);

  void receivedResponse();
  void sentRequest();
  void sentBatchRequest(int elementCount, int requestSize);
}
