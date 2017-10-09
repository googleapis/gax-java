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
package com.google.api.gax.grpc;

import com.google.api.gax.grpc.GrpcOperationTransformers.MetadataTransformer;
import com.google.api.gax.grpc.GrpcOperationTransformers.ResponseTransformer;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.UnavailableException;
import com.google.common.truth.Truth;
import com.google.longrunning.Operation;
import com.google.protobuf.Any;
import com.google.rpc.Status;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.Status.Code;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GrpcOperationTransformersTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testResponseTransformer() {
    ResponseTransformer<Money> transformer = ResponseTransformer.of(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("USD").build();
    OperationSnapshot operationSnapshot =
        GrpcOperationSnapshot.create(
            Operation.newBuilder().setResponse(Any.pack(inputMoney)).build());
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }

  @Test
  public void testResponseTransformer_exception() {
    thrown.expect(UnavailableException.class);
    ResponseTransformer<Money> transformer = ResponseTransformer.of(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("USD").build();
    Status status = Status.newBuilder().setCode(Code.UNAVAILABLE.value()).build();
    OperationSnapshot operationSnapshot =
        GrpcOperationSnapshot.create(
            Operation.newBuilder().setResponse(Any.pack(inputMoney)).setError(status).build());
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }

  @Test
  public void testResponseTransformer_mismatchedTypes() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("Failed to unpack object");
    ResponseTransformer<Money> transformer = ResponseTransformer.of(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("USD").build();
    Status status = Status.newBuilder().setCode(Code.OK.value()).build();
    OperationSnapshot operationSnapshot =
        GrpcOperationSnapshot.create(
            Operation.newBuilder()
                .setResponse(Any.pack(Color.getDefaultInstance()))
                .setError(status)
                .build());
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }

  @Test
  public void testMetadataTransformer() {
    MetadataTransformer<Money> transformer = MetadataTransformer.of(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("USD").build();
    OperationSnapshot operationSnapshot =
        GrpcOperationSnapshot.create(
            Operation.newBuilder().setMetadata(Any.pack(inputMoney)).build());
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }

  @Test
  public void testMetadataTransformer_mismatchedTypes() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("Failed to unpack object");
    MetadataTransformer<Money> transformer = MetadataTransformer.of(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("USD").build();
    Status status = Status.newBuilder().setCode(Code.OK.value()).build();
    OperationSnapshot operationSnapshot =
        GrpcOperationSnapshot.create(
            Operation.newBuilder()
                .setMetadata(Any.pack(Color.getDefaultInstance()))
                .setError(status)
                .build());
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }
}
