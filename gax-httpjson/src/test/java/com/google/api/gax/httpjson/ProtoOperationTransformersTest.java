/*
 * Copyright 2021 Google LLC
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
package com.google.api.gax.httpjson;

import static org.junit.Assert.assertThrows;

import com.google.api.gax.httpjson.ProtoOperationTransformers.MetadataTransformer;
import com.google.api.gax.httpjson.ProtoOperationTransformers.ResponseTransformer;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.UnavailableException;
import com.google.api.gax.rpc.UnknownException;
import com.google.common.truth.Truth;
import com.google.longrunning.Operation;
import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.Status;
import com.google.type.Color;
import com.google.type.Money;
import org.junit.Test;

public class ProtoOperationTransformersTest {

  @Test
  public void testResponseTransformer() {
    ResponseTransformer<Money> transformer = ResponseTransformer.create(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("UAH").build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.newBuilder()
            .setName("Madrid")
            .setMetadata(2)
            .setDone(true)
            .setResponse(inputMoney)
            .setError(0, "no error")
            .build();
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }

  @Test
  public void testMetadataTransformer() {
    MetadataTransformer<Money> transformer = MetadataTransformer.create(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("UAH").build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.newBuilder()
            .setName("Barcelona")
            .setMetadata(inputMoney)
            .setDone(true)
            .setResponse("Gary")
            .setError(0, "no error")
            .build();
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }

  @Test
  public void testAnyResponseTransformer() {
    ResponseTransformer<Money> transformer = ResponseTransformer.create(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("USD").build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.create(
            Operation.newBuilder().setResponse(Any.pack(inputMoney)).build());
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }

  @Test
  public void testAnyResponseTransformer_exception() {
    ResponseTransformer<Money> transformer = ResponseTransformer.create(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("USD").build();
    Status status = Status.newBuilder().setCode(Code.UNAVAILABLE.getNumber()).build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.create(
            Operation.newBuilder().setResponse(Any.pack(inputMoney)).setError(status).build());

    Exception exception =
        assertThrows(UnavailableException.class, () -> transformer.apply(operationSnapshot));
    Truth.assertThat(exception)
        .hasMessageThat()
        .contains("failed with status = HttpJsonStatusCode{statusCode=UNAVAILABLE}");
  }

  @Test
  public void testAnyResponseTransformer_mismatchedTypes() {
    ResponseTransformer<Money> transformer = ResponseTransformer.create(Money.class);
    Status status = Status.newBuilder().setCode(Code.OK.getNumber()).build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.create(
            Operation.newBuilder()
                .setResponse(Any.pack(Color.getDefaultInstance()))
                .setError(status)
                .build());
    Exception exception =
        assertThrows(UnknownException.class, () -> transformer.apply(operationSnapshot));
    Truth.assertThat(exception).hasMessageThat().contains("encountered a problem unpacking it");
  }

  @Test
  public void testAnyMetadataTransformer() {
    MetadataTransformer<Money> transformer = MetadataTransformer.create(Money.class);
    Money inputMoney = Money.newBuilder().setCurrencyCode("USD").build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.create(
            Operation.newBuilder().setMetadata(Any.pack(inputMoney)).build());
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputMoney);
  }

  @Test
  public void testAnyMetadataTransformer_mismatchedTypes() {
    MetadataTransformer<Money> transformer = MetadataTransformer.create(Money.class);
    Status status = Status.newBuilder().setCode(Code.OK.getNumber()).build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.create(
            Operation.newBuilder()
                .setMetadata(Any.pack(Color.getDefaultInstance()))
                .setError(status)
                .build());
    Exception exception =
        assertThrows(UnknownException.class, () -> transformer.apply(operationSnapshot));
    Truth.assertThat(exception).hasMessageThat().contains("encountered a problem unpacking it");
  }
}
