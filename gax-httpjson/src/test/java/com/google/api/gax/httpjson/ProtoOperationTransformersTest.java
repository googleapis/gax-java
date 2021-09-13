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

import com.google.api.gax.httpjson.ProtoOperationTransformers.MetadataTransformer;
import com.google.api.gax.httpjson.ProtoOperationTransformers.ResponseTransformer;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.common.truth.Truth;
import com.google.protobuf.Option;
import org.junit.Test;

public class ProtoOperationTransformersTest {

  @Test
  public void testResponseTransformer() {
    ResponseTransformer<Option> transformer = ResponseTransformer.create(Option.class);
    Option inputOption = Option.newBuilder().setName("Paris").build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.newBuilder()
            .setName("Madrid")
            .setMetadata(2)
            .setDone(true)
            .setResponse(inputOption)
            .setError(0, "no error")
            .build();
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(inputOption);
  }

  @Test
  public void testMetadataTransformer() {
    MetadataTransformer<Option> transformer = MetadataTransformer.create(Option.class);
    Option metaData = Option.newBuilder().setName("Valparaiso").build();
    OperationSnapshot operationSnapshot =
        HttpJsonOperationSnapshot.newBuilder()
            .setName("Barcelona")
            .setMetadata(metaData)
            .setDone(true)
            .setResponse("Gary")
            .setError(0, "no error")
            .build();
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(metaData);
  }
}
