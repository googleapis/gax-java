package com.google.api.gax.opencensus;

import java.io.Closeable;
import org.threeten.bp.Duration;

public class OpenCensusTracer implements Tracer {

  public static OpenCensusTracer create(String spanName) {

    return null;
  }

  public TracerContext enter() {
    return new TracerContext();
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

  static class TracerContext implements AutoCloseable {

    @Override
    public void close() {

    }
  }
}
