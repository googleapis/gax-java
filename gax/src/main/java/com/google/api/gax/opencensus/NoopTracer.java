package com.google.api.gax.opencensus;

import org.threeten.bp.Duration;

public class NoopTracer implements Tracer {
  public static Tracer create() {
    return new NoopTracer();
  }

  @Override
  public void operationStarted() {

  }

  @Override
  public void operationFailed(Throwable throwable) {

  }

  @Override
  public void operationSucceeded() {

  }

  @Override
  public void connectionSelected(int id) {

  }

  @Override
  public void credentialsRefreshed() {

  }

  @Override
  public void startAttempt() {

  }

  @Override
  public void attemptSucceeded() {

  }

  @Override
  public void retryableFailure(Throwable error, Duration delay) {

  }

  @Override
  public void retriesExhausted() {

  }

  @Override
  public void permanentFailure(Throwable error) {

  }

  @Override
  public void receivedResponse() {

  }

  @Override
  public void sentRequest() {

  }

  @Override
  public void sentBatchRequest(int elementCount, int requestSize) {

  }
}
