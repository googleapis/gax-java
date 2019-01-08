/*
 * Copyright 2019 Google LLC
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

import com.google.api.client.util.Maps;
import com.google.api.core.BetaApi;
import com.google.api.gax.rpc.ApiException;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Status.CanonicalCode;
import io.opencensus.trace.Tracer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.threeten.bp.Duration;

/**
 * Implementation of {@link ApiTracer} that uses OpenCensus.
 *
 * <p>This implementation creates an OpenCensus {@link Span} for every tracer and annotates that
 * {@link Span} with various events throughout the lifecycle of the logical operation.
 */
@BetaApi("Surface for tracing is not yet stable")
public class OpencensusTracer implements ApiTracer {
  private final Tracer tracer;
  private final Span span;

  private long currentAttemptId;
  private long attemptRequests = 0;
  private long attemptResponses = 0;
  private long totalAttemptRequests = 0;
  private long totalAttemptResponses = 0;

  public OpencensusTracer(@Nonnull Tracer tracer, @Nonnull Span span) {
    this.tracer = Preconditions.checkNotNull(tracer, "tracer can't be null");
    this.span = Preconditions.checkNotNull(span, "span can't be null");
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
    Map<String, AttributeValue> attributes = Maps.newHashMap();

    attributes.put("attempt count", AttributeValue.longAttributeValue(currentAttemptId + 1));
    attributes.put("total request count", AttributeValue.longAttributeValue(totalAttemptRequests));
    attributes.put("total response count", AttributeValue.longAttributeValue(totalAttemptResponses));

    span.putAttributes(attributes);

    span.end();
  }

  @Override
  public void operationFailed(Throwable error) {
    Map<String, AttributeValue> attributes = Maps.newHashMap();

    attributes.put("attempt count", AttributeValue.longAttributeValue(currentAttemptId + 1));
    attributes.put("total request count", AttributeValue.longAttributeValue(totalAttemptRequests));
    attributes.put("total response count", AttributeValue.longAttributeValue(totalAttemptResponses));

    span.putAttributes(attributes);

    span.end(EndSpanOptions.builder().setStatus(convertErrorToStatus(error)).build());
  }

  @Override
  public void connectionSelected(int id) {
    span.addAnnotation(
        "Connection selected", ImmutableMap.of("id", AttributeValue.longAttributeValue(id)));
  }

  @Override
  public void attemptStarted(int attemptNumber) {
    currentAttemptId = attemptNumber;

    HashMap<String, AttributeValue> attributes = Maps.newHashMap();
    populateAttemptNumber(attributes);

    span.addAnnotation("Attempt started", attributes);
  }

  @Override
  public void attemptSucceeded() {
    Map<String, AttributeValue> attributes = baseAttemptAttributes();

    span.addAnnotation("Attempt succeeded", attributes);
  }

  @Override
  public void attemptFailed(Throwable error, Duration delay) {
    Map<String, AttributeValue> attributes = baseAttemptAttributes();
    attributes.put("delay ms", AttributeValue.longAttributeValue(delay.toMillis()));

    String msg = error != null ? "Attempt failed" : "Operation incomplete";
    span.addAnnotation(msg + ", scheduling next attempt", attributes);
  }

  @Override
  public void attemptFailedRetriesExhausted(Throwable error) {
    Map<String, AttributeValue> attributes = baseAttemptAttributes();
    populateError(attributes, error);

    span.addAnnotation("Attempts exhausted", attributes);
  }

  @Override
  public void attemptPermanentFailure(Throwable error) {
    Map<String, AttributeValue> attributes = baseAttemptAttributes();
    populateError(attributes, error);

    span.addAnnotation("Attempt failed, error not retryable ", attributes);
  }

  @Override
  public void responseReceived() {
    attemptResponses++;
    totalAttemptResponses++;
  }

  @Override
  public void requestSent() {
    attemptRequests++;
    totalAttemptRequests++;
  }

  @Override
  public void batchRequestSent(long elementCount, long requestSize) {
    span.putAttribute("batch count", AttributeValue.longAttributeValue(elementCount));
    span.putAttribute("request size", AttributeValue.longAttributeValue(requestSize));
  }


  private Map<String, AttributeValue> baseAttemptAttributes() {
    HashMap<String, AttributeValue> attributes = Maps.newHashMap();

    populateAttemptNumber(attributes);
    attributes.put("attempt request count", AttributeValue.longAttributeValue(attemptRequests));
    attributes.put("attempt response count", AttributeValue.longAttributeValue(attemptResponses));

    return attributes;
  }

  private void populateAttemptNumber(Map<String, AttributeValue> attributes) {
    attributes.put("attempt", AttributeValue.longAttributeValue(currentAttemptId));
  }

  private void populateError(Map<String, AttributeValue> attributes, Throwable error) {
    if (error == null) {
      attributes.put("errorCode", null);
      return;
    }

    Status status = convertErrorToStatus(error);

    attributes.put("errorCode",
        AttributeValue.stringAttributeValue(status.getCanonicalCode().toString()));
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
