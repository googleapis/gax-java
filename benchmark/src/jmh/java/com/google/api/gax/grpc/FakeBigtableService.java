/*
 * Copyright 2018 Google LLC
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

import com.google.bigtable.v2.BigtableGrpc;
import com.google.bigtable.v2.ReadRowsRequest;
import com.google.bigtable.v2.ReadRowsResponse;
import com.google.bigtable.v2.ReadRowsResponse.CellChunk;
import com.google.common.collect.AbstractIterator;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fake implementation of the bigtable service. It is meant to aid in benchmarking client code by
 * generating fake responses.
 */
public class FakeBigtableService extends BigtableGrpc.BigtableImplBase {
  /**
   * Generates a stream of responses as fast as it can. The number of responses is controlled by
   * {@link ReadRowsRequest#getRowsLimit()}.
   */
  @Override
  public void readRows(ReadRowsRequest request, StreamObserver<ReadRowsResponse> responseObserver) {
    long numRows = Long.MAX_VALUE;
    if (request.getRowsLimit() > 0) {
      numRows = request.getRowsLimit();
    }

    final AtomicBoolean done = new AtomicBoolean();

    final Iterator<ReadRowsResponse> source = new ReadRowsGenerator(numRows);
    final ServerCallStreamObserver<ReadRowsResponse> target =
        (ServerCallStreamObserver<ReadRowsResponse>) responseObserver;

    target.setOnReadyHandler(
        new Runnable() {
          @Override
          public void run() {
            while (target.isReady() && source.hasNext()) {
              target.onNext(source.next());
            }
            if (!source.hasNext() && done.compareAndSet(false, true)) {
              target.onCompleted();
            }
          }
        });
  }

  private static class ReadRowsGenerator extends AbstractIterator<ReadRowsResponse> {
    private final long numResponses;
    private long numSent;

    ReadRowsGenerator(long numResponses) {
      this.numResponses = numResponses;
    }

    @Override
    protected ReadRowsResponse computeNext() {
      if (numSent < numResponses) {
        return buildResponse(numSent++);
      }
      return endOfData();
    }

    private ReadRowsResponse buildResponse(long i) {
      return ReadRowsResponse.newBuilder()
          .addChunks(
              CellChunk.newBuilder()
                  .setRowKey(ByteString.copyFromUtf8(String.format("user%07d", i)))
                  .setFamilyName(StringValue.newBuilder().setValue("cf").build())
                  .setTimestampMicros(1_000)
                  .setValue(ByteString.copyFromUtf8(String.format("user%07d", i)))
                  .setCommitRow(true)
                  .build())
          .build();
    }
  }
}
