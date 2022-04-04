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
package com.google.api.gax.rpc;

import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
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
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ErrorDetailsTest {

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

  ErrorDetails errorDetails;

  @Before
  public void setUp() throws Exception {
    ImmutableList<Any> rawErrorMessages =
        ImmutableList.of(
            Any.pack(ERROR_INFO),
            Any.pack(RETRY_INFO),
            Any.pack(DEBUG_INFO),
            Any.pack(QUOTA_FAILURE),
            Any.pack(PRECONDITION_FAILURE),
            Any.pack(BAD_REQUEST),
            Any.pack(REQUEST_INFO),
            Any.pack(RESOURCE_INFO),
            Any.pack(HELP),
            Any.pack(LOCALIZED_MESSAGE));

    errorDetails = ErrorDetails.builder().setRawErrorMessages(rawErrorMessages).build();
  }

  @Test
  public void unpack_shouldReturnNullIfRawErrorMessagesIsNull() {
    errorDetails = ErrorDetails.builder().setRawErrorMessages(null).build();

    Truth.assertThat(errorDetails.unpack(ErrorInfo.class)).isNull();
  }

  @Test
  public void unpack_shouldReturnNullIfErrorMessageTypeDoesNotExist() {
    errorDetails =
        ErrorDetails.builder().setRawErrorMessages(ImmutableList.of(Any.pack(ERROR_INFO))).build();

    Truth.assertThat(errorDetails.unpack(DebugInfo.class)).isNull();
  }

  @Test
  public void unpack_shouldThrowExceptionIfUnpackingErrorMassageFailed() {
    Any malformedErrorType =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/google.rpc.ErrorInfo")
            .setValue(ByteString.copyFromUtf8("This is an invalid message!"))
            .build();
    errorDetails =
        ErrorDetails.builder().setRawErrorMessages(ImmutableList.of(malformedErrorType)).build();
    ProtocolBufferParsingException exception =
        assertThrows(
            ProtocolBufferParsingException.class, () -> errorDetails.unpack(ErrorInfo.class));
    Truth.assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "Failed to unpack %s from raw error messages", ErrorInfo.class.getSimpleName()));
  }

  @Test
  public void unpack_shouldReturnDesiredErrorMessageTypeIfItExist() {
    Truth.assertThat(errorDetails.unpack(ErrorInfo.class)).isEqualTo(ERROR_INFO);
  }

  @Test
  public void errorInfo_shouldUnpackErrorInfoProtoMessage() {
    Truth.assertThat(errorDetails.getErrorInfo()).isEqualTo(ERROR_INFO);
  }

  @Test
  public void retryInfo_shouldUnpackRetryInfoProtoMessage() {
    Truth.assertThat(errorDetails.getRetryInfo()).isEqualTo(RETRY_INFO);
  }

  @Test
  public void debugInfo_shouldUnpackDebugInfoProtoMessage() {
    Truth.assertThat(errorDetails.getDebugInfo()).isEqualTo(DEBUG_INFO);
  }

  @Test
  public void quotaFailure_shouldUnpackQuotaFailureProtoMessage() {
    Truth.assertThat(errorDetails.getQuotaFailure()).isEqualTo(QUOTA_FAILURE);
  }

  @Test
  public void preconditionFailure_shouldUnpackPreconditionFailureProtoMessage() {
    Truth.assertThat(errorDetails.getPreconditionFailure()).isEqualTo(PRECONDITION_FAILURE);
  }

  @Test
  public void badRequest_shouldUnpackBadRequestProtoMessage() {
    Truth.assertThat(errorDetails.getBadRequest()).isEqualTo(BAD_REQUEST);
  }

  @Test
  public void requestInfo_shouldUnpackRequestInfoProtoMessage() {
    Truth.assertThat(errorDetails.getRequestInfo()).isEqualTo(REQUEST_INFO);
  }

  @Test
  public void resourceInfo_shouldUnpackResourceInfoProtoMessage() {
    Truth.assertThat(errorDetails.getResourceInfo()).isEqualTo(RESOURCE_INFO);
  }

  @Test
  public void help_shouldUnpackHelpProtoMessage() {
    Truth.assertThat(errorDetails.getHelp()).isEqualTo(HELP);
  }

  @Test
  public void localizedMessage_shouldUnpackLocalizedMessageProtoMessage() {
    Truth.assertThat(errorDetails.getHelp()).isEqualTo(HELP);
  }
}
