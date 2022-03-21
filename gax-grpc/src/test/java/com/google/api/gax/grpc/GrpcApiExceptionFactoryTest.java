/*
 * Copyright 2022 Google LLC
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
package com.google.api.gax.grpc;

import static com.google.api.gax.grpc.GrpcApiExceptionFactory.ERROR_DETAIL_KEY;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ErrorDetails;
import com.google.common.truth.Truth;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.rpc.BadRequest;
import com.google.rpc.BadRequest.FieldViolation;
import com.google.rpc.DebugInfo;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Help;
import com.google.rpc.Help.Link;
import com.google.rpc.LocalizedMessage;
import com.google.rpc.PreconditionFailure;
import com.google.rpc.QuotaFailure;
import com.google.rpc.QuotaFailure.Violation;
import com.google.rpc.RequestInfo;
import com.google.rpc.ResourceInfo;
import com.google.rpc.RetryInfo;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GrpcApiExceptionFactoryTest {

  private static final ErrorInfo ERROR_INFO =
      ErrorInfo.newBuilder()
          .setDomain("googleapis.com")
          .setReason("SERVICE_DISABLED")
          .putAllMetadata(Collections.emptyMap())
          .build();

  private static final RetryInfo RETRY_INFO =
      RetryInfo.newBuilder().setRetryDelay(Duration.newBuilder().setSeconds(213).build()).build();

  private static final DebugInfo DEBUG_INFO =
      DebugInfo.newBuilder()
          .setDetail("No more details available")
          .addStackEntries("Does not matter")
          .build();

  private static final QuotaFailure QUOTA_FAILURE =
      QuotaFailure.newBuilder()
          .addViolations(
              Violation.newBuilder()
                  .setDescription("new violation")
                  .setSubject("This is a breaking news")
                  .build())
          .build();

  private static final PreconditionFailure PRECONDITION_FAILURE =
      PreconditionFailure.newBuilder()
          .addViolations(
              PreconditionFailure.Violation.newBuilder()
                  .setDescription("new violation")
                  .setSubject("This is a breaking news")
                  .setType("Unknown")
                  .build())
          .build();

  private static final BadRequest BAD_REQUEST =
      BadRequest.newBuilder()
          .addFieldViolations(
              FieldViolation.newBuilder()
                  .setDescription("new field violation")
                  .setField("unknown field")
                  .build())
          .build();

  private static final RequestInfo REQUEST_INFO =
      RequestInfo.newBuilder()
          .setRequestId("ukajsdkansdk123")
          .setServingData("no data available")
          .build();

  private static final ResourceInfo RESOURCE_INFO =
      ResourceInfo.newBuilder()
          .setDescription("not available")
          .setResourceName("my resource")
          .setResourceType("mystery")
          .setOwner("myself")
          .build();

  private static final Help HELP =
      Help.newBuilder()
          .addLinks(Link.newBuilder().setDescription("new link").setUrl("https://abc.com").build())
          .build();

  private static final LocalizedMessage LOCALIZED_MESSAGE =
      LocalizedMessage.newBuilder().setLocale("en").setMessage("nothing").build();

  private static final io.grpc.Status GRPC_STATUS = io.grpc.Status.CANCELLED;

  private GrpcApiExceptionFactory factory;

  @Before
  public void setUp() throws Exception {
    factory = new GrpcApiExceptionFactory(Collections.emptySet());
  }

  @Test
  public void create_shouldCreateApiExceptionForStatusException() {
    Metadata trailers = new Metadata();
    Status status = Status.newBuilder().addDetails(Any.pack(ERROR_INFO)).build();
    trailers.put(
        Metadata.Key.of(ERROR_DETAIL_KEY, Metadata.BINARY_BYTE_MARSHALLER), status.toByteArray());
    StatusException statusException = new StatusException(GRPC_STATUS, trailers);

    ApiException actual = factory.create(statusException);
    ErrorDetails expected = ErrorDetails.builder().setErrorInfo(ERROR_INFO).build();

    Truth.assertThat(actual.getErrorDetails()).isEqualTo(expected);
  }

  @Test
  public void create_shouldCreateApiExceptionForStatusRuntimeException() {
    Metadata trailers = new Metadata();
    Status status = Status.newBuilder().addDetails(Any.pack(ERROR_INFO)).build();
    trailers.put(
        Metadata.Key.of(ERROR_DETAIL_KEY, Metadata.BINARY_BYTE_MARSHALLER), status.toByteArray());
    StatusRuntimeException statusException = new StatusRuntimeException(GRPC_STATUS, trailers);

    ApiException actual = factory.create(statusException);
    ErrorDetails expected = ErrorDetails.builder().setErrorInfo(ERROR_INFO).build();

    Truth.assertThat(actual.getErrorDetails()).isEqualTo(expected);
  }

  @Test
  public void create_shouldCreateApiExceptionWithNoErrorDetailsIfMetadataIsNull() {
    StatusRuntimeException statusException = new StatusRuntimeException(GRPC_STATUS, null);

    ApiException actual = factory.create(statusException);

    Truth.assertThat(actual.getErrorDetails()).isNull();
  }

  @Test
  public void create_shouldCreateApiExceptionWithNoErrorDetailsIfMetadataDoesNotHaveErrorDetails() {
    StatusRuntimeException statusException =
        new StatusRuntimeException(GRPC_STATUS, new Metadata());

    ApiException actual = factory.create(statusException);

    Truth.assertThat(actual.getErrorDetails()).isNull();
  }

  @Test
  public void create_shouldCreateApiExceptionWithNoErrorDetailsIfStatusIsMalformed() {
    Metadata trailers = new Metadata();
    Status status = Status.newBuilder().addDetails(Any.pack(ERROR_INFO)).build();
    byte[] bytes = status.toByteArray();
    // manually manipulate status bytes array
    bytes[0] = 123;
    trailers.put(Metadata.Key.of(ERROR_DETAIL_KEY, Metadata.BINARY_BYTE_MARSHALLER), bytes);
    StatusRuntimeException statusException = new StatusRuntimeException(GRPC_STATUS, trailers);

    ApiException actual = factory.create(statusException);

    Truth.assertThat(actual.getErrorDetails()).isNull();
  }

  @Test
  public void create_shouldCreateApiExceptionWithAllSupportedTypes() {
    Metadata trailers = new Metadata();
    Status status =
        Status.newBuilder()
            .addDetails(Any.pack(ERROR_INFO))
            .addDetails(Any.pack(RETRY_INFO))
            .addDetails(Any.pack(DEBUG_INFO))
            .addDetails(Any.pack(QUOTA_FAILURE))
            .addDetails(Any.pack(PRECONDITION_FAILURE))
            .addDetails(Any.pack(BAD_REQUEST))
            .addDetails(Any.pack(REQUEST_INFO))
            .addDetails(Any.pack(RESOURCE_INFO))
            .addDetails(Any.pack(HELP))
            .addDetails(Any.pack(LOCALIZED_MESSAGE))
            .build();
    trailers.put(
        Metadata.Key.of(ERROR_DETAIL_KEY, Metadata.BINARY_BYTE_MARSHALLER), status.toByteArray());
    StatusException statusException = new StatusException(GRPC_STATUS, trailers);

    ApiException actual = factory.create(statusException);
    ErrorDetails expected =
        ErrorDetails.builder()
            .setErrorInfo(ERROR_INFO)
            .setRetryInfo(RETRY_INFO)
            .setDebugInfo(DEBUG_INFO)
            .setQuotaFailure(QUOTA_FAILURE)
            .setPreconditionFailure(PRECONDITION_FAILURE)
            .setBadRequest(BAD_REQUEST)
            .setRequestInfo(REQUEST_INFO)
            .setResourceInfo(RESOURCE_INFO)
            .setHelp(HELP)
            .setLocalizedMessage(LOCALIZED_MESSAGE)
            .build();

    Truth.assertThat(actual.getErrorDetails()).isEqualTo(expected);
  }

  @Test
  public void create_shouldIgnoreUnknownErrorTypesIfErrorDetailsContainsUnknownErrorType() {
    Metadata trailers = new Metadata();
    Status status =
        Status.newBuilder()
            .addDetails(Any.pack(ERROR_INFO))
            .addDetails(Any.pack(DEBUG_INFO))
            // ErrorDetails should ignore this message
            .addDetails(Any.pack(Duration.newBuilder().setSeconds(200).build()))
            .build();
    trailers.put(
        Metadata.Key.of(ERROR_DETAIL_KEY, Metadata.BINARY_BYTE_MARSHALLER), status.toByteArray());
    StatusRuntimeException statusException = new StatusRuntimeException(GRPC_STATUS, trailers);

    ApiException actual = factory.create(statusException);
    ErrorDetails expected =
        ErrorDetails.builder().setErrorInfo(ERROR_INFO).setDebugInfo(DEBUG_INFO).build();

    Truth.assertThat(actual.getErrorDetails()).isEqualTo(expected);
  }

  @Test
  public void create_shouldCreateApiExceptionIfUnableToUnpackOneOfTheErrorType() {
    Metadata trailers = new Metadata();
    Any malformedDebugType =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/google.rpc.DebugInfo")
            .setValue(ByteString.copyFromUtf8("This is an invalid message!"))
            .build();
    Status status =
        Status.newBuilder().addDetails(Any.pack(ERROR_INFO)).addDetails(malformedDebugType).build();
    trailers.put(
        Metadata.Key.of(ERROR_DETAIL_KEY, Metadata.BINARY_BYTE_MARSHALLER), status.toByteArray());
    StatusRuntimeException statusException = new StatusRuntimeException(GRPC_STATUS, trailers);

    ApiException actual = factory.create(statusException);
    ErrorDetails expected = ErrorDetails.builder().setErrorInfo(ERROR_INFO).build();

    Truth.assertThat(actual.getErrorDetails()).isEqualTo(expected);
  }
}
