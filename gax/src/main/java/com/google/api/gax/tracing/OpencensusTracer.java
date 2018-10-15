/*
 * Copyright 2017 Google LLC
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
package com.google.api.gax.tracing;

import com.google.api.gax.rpc.ApiException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Status.CanonicalCode;
import java.util.HashMap;
import java.util.Map;
import org.threeten.bp.Duration;

public class OpencensusTracer implements Tracer {
  private final io.opencensus.trace.Tracer tracer;
  private final Span span;
  private Type type;

  private int attempts = 0;
  private long attemptRequests = 0;
  private long attemptResponses = 0;
  private long totalAttemptRequests = 0;
  private long totalAttemptResponses = 0;

  OpencensusTracer(io.opencensus.trace.Tracer tracer, Span span, Type type) {
    this.tracer = tracer;
    this.span = span;
    this.type = type;
  }

  @Override
  public Scope inScope() {
    final io.opencensus.common.Scope scope = tracer.withSpan(span);

    return new Scope() {
      @Override
      public void close() {
        scope.close();
      }
    };
  }

  @Override
  public void operationSucceeded() {
    span.putAttributes(baseAttemptAttributes());
    span.end();
  }

  @Override
  public void operationFailed(Throwable error) {
    span.putAttributes(baseAttemptAttributes());
    span.end(EndSpanOptions.builder().setStatus(convertErrorToStatus(error)).build());
  }

  private Map<String, AttributeValue> baseOperationAttributes() {
    HashMap<String, AttributeValue> attributes = Maps.newHashMap();

    if (type.getCountRequests()) {
      attributes.put(
          "total request count", AttributeValue.longAttributeValue(totalAttemptRequests));
    }
    if (type.getCountResponses()) {
      attributes.put(
          "total response count", AttributeValue.longAttributeValue(totalAttemptResponses));
    }
    return attributes;
  }

  @Override
  public void connectionSelected(int id) {
    span.addAnnotation(
        "Connection selected", ImmutableMap.of("id", AttributeValue.longAttributeValue(id)));
  }

  @Override
  public void startAttempt() {
    HashMap<String, AttributeValue> attributes = Maps.newHashMap();
    attributes.put("attempt", AttributeValue.longAttributeValue(attempts));

    attempts++;
    attemptRequests = attemptResponses = 0;

    span.addAnnotation(
        "Attempt started", ImmutableMap.of("attempt", AttributeValue.longAttributeValue(attempts)));
  }

  @Override
  public void attemptSucceeded() {
    Map<String, AttributeValue> attributes = baseAttemptAttributes();
    span.addAnnotation("Attempt succeeded", attributes);
  }

  @Override
  public void retryableFailure(Throwable error, Duration delay) {
    Map<String, AttributeValue> attributes = baseAttemptAttributes();
    attributes.put("delay ms", AttributeValue.longAttributeValue(delay.toMillis()));

    String msg = error != null ? "Attempt failed" : "Operation incomplete";
    span.addAnnotation(msg + ", scheduling next attempt", attributes);
  }

  @Override
  public void retriesExhausted() {
    Map<String, AttributeValue> attributes = baseAttemptAttributes();
    span.addAnnotation("Attempts exhausted", attributes);
  }

  @Override
  public void permanentFailure(Throwable error) {
    Map<String, AttributeValue> attributes = baseAttemptAttributes();
    span.addAnnotation("Attempt failed, error not retryable ", attributes);
  }

  @Override
  public void receivedResponse() {
    attemptResponses++;
    totalAttemptResponses++;
  }

  private Map<String, AttributeValue> baseAttemptAttributes() {
    HashMap<String, AttributeValue> attributes = Maps.newHashMap();
    attributes.put("attempt", AttributeValue.longAttributeValue(attempts));

    if (type.getCountRequests()) {
      attributes.put("attempt request count", AttributeValue.longAttributeValue(attemptRequests));
    }
    if (type.getCountResponses()) {
      attributes.put("attempt response count", AttributeValue.longAttributeValue(attemptResponses));
    }

    return attributes;
  }

  @Override
  public void sentRequest() {
    attemptRequests++;
    totalAttemptRequests++;
  }

  @Override
  public void sentBatchRequest(long elementCount, long requestSize) {
    span.putAttribute("batch count", AttributeValue.longAttributeValue(elementCount));
    span.putAttribute("request size", AttributeValue.longAttributeValue(requestSize));
  }

  private static Status convertErrorToStatus(Throwable error) {
    if (!(error instanceof ApiException)) {
      return Status.UNKNOWN.withDescription(error.getMessage());
    }

    ApiException apiException = (ApiException) error;

    Status.CanonicalCode code;
    try {
      code = Status.CanonicalCode.valueOf(apiException.getStatusCode().getCode().name());
    } catch (IllegalArgumentException e) {
      code = CanonicalCode.UNKNOWN;
    }

    return code.toStatus().withDescription(error.getMessage());
  }
}
