/*
 * Copyright 2017, Google Inc. All rights reserved.
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
 *     * Neither the name of Google Inc. nor the names of its
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

import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.common.truth.Truth;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApiExceptionFactoryTest {

  @Test
  public void cancelled() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.CANCELLED), false))
        .isInstanceOf(CancelledException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.CANCELLED), false))
        .isInstanceOf(CancelledException.class);
  }

  @Test
  public void notFound() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.NOT_FOUND), false))
        .isInstanceOf(NotFoundException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.NOT_FOUND), false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  public void unknown() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.UNKNOWN), false))
        .isInstanceOf(UnknownException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.UNKNOWN), false))
        .isInstanceOf(UnknownException.class);
  }

  @Test
  public void invalidArgument() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.INVALID_ARGUMENT), false))
        .isInstanceOf(InvalidArgumentException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.INVALID_ARGUMENT), false))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  public void deadlineExceeded() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.DEADLINE_EXCEEDED), false))
        .isInstanceOf(DeadlineExceededException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message",
                new RuntimeException(),
                FakeStatusCode.of(Code.DEADLINE_EXCEEDED),
                false))
        .isInstanceOf(DeadlineExceededException.class);
  }

  @Test
  public void alreadyExists() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.ALREADY_EXISTS), false))
        .isInstanceOf(AlreadyExistsException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.ALREADY_EXISTS), false))
        .isInstanceOf(AlreadyExistsException.class);
  }

  @Test
  public void permissionDenied() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.PERMISSION_DENIED), false))
        .isInstanceOf(PermissionDeniedException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message",
                new RuntimeException(),
                FakeStatusCode.of(Code.PERMISSION_DENIED),
                false))
        .isInstanceOf(PermissionDeniedException.class);
  }

  @Test
  public void resourceExhausted() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.RESOURCE_EXHAUSTED), false))
        .isInstanceOf(ResourceExhaustedException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message",
                new RuntimeException(),
                FakeStatusCode.of(Code.RESOURCE_EXHAUSTED),
                false))
        .isInstanceOf(ResourceExhaustedException.class);
  }

  @Test
  public void failedPrecondition() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.FAILED_PRECONDITION), false))
        .isInstanceOf(FailedPreconditionException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message",
                new RuntimeException(),
                FakeStatusCode.of(Code.FAILED_PRECONDITION),
                false))
        .isInstanceOf(FailedPreconditionException.class);
  }

  @Test
  public void aborted() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.ABORTED), false))
        .isInstanceOf(AbortedException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.ABORTED), false))
        .isInstanceOf(AbortedException.class);
  }

  @Test
  public void outOfRange() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.OUT_OF_RANGE), false))
        .isInstanceOf(OutOfRangeException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.OUT_OF_RANGE), false))
        .isInstanceOf(OutOfRangeException.class);
  }

  @Test
  public void internal() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.INTERNAL), false))
        .isInstanceOf(InternalException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.INTERNAL), false))
        .isInstanceOf(InternalException.class);
  }

  @Test
  public void unavailable() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.UNAVAILABLE), false))
        .isInstanceOf(UnavailableException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.UNAVAILABLE), false))
        .isInstanceOf(UnavailableException.class);
  }

  @Test
  public void dataLoss() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.DATA_LOSS), false))
        .isInstanceOf(DataLossException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.DATA_LOSS), false))
        .isInstanceOf(DataLossException.class);
  }

  @Test
  public void unauthenticated() {
    Truth.assertThat(
            ApiExceptionFactory.createException(
                new RuntimeException(), FakeStatusCode.of(Code.UNAUTHENTICATED), false))
        .isInstanceOf(UnauthenticatedException.class);
    Truth.assertThat(
            ApiExceptionFactory.createException(
                "message", new RuntimeException(), FakeStatusCode.of(Code.UNAUTHENTICATED), false))
        .isInstanceOf(UnauthenticatedException.class);
  }
}
