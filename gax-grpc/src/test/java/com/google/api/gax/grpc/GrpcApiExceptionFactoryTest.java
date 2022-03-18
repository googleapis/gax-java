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
import com.google.rpc.ErrorInfo;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GrpcApiExceptionFactoryTest {

  private GrpcApiExceptionFactory factory;

  @Before
  public void setUp() throws Exception {
    factory = new GrpcApiExceptionFactory(Collections.emptySet());
  }

  @Test
  public void create_shouldCreateApiExceptionForStatusException() {
    Metadata trailers = new Metadata();
    ErrorInfo errorInfo =
        ErrorInfo.newBuilder()
            .setDomain("googleapis.com")
            .setReason("SERVICE_DISABLED")
            .putAllMetadata(Collections.emptyMap())
            .build();
    com.google.rpc.Status status =
        com.google.rpc.Status.newBuilder().addDetails(Any.pack(errorInfo)).build();
    trailers.put(
        Metadata.Key.of(ERROR_DETAIL_KEY, Metadata.BINARY_BYTE_MARSHALLER), status.toByteArray());
    StatusException statusException = new StatusException(Status.CANCELLED, trailers);

    ApiException actual = factory.create(statusException);
    ErrorDetails expected = ErrorDetails.builder().setErrorInfo(errorInfo).build();

    Truth.assertThat(actual.getErrorDetails()).isEqualTo(expected);
  }
}
