/*
 * Copyright 2017, Google LLC All rights reserved.
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
package com.google.api.gax.grpc.testing;

import com.google.api.gax.grpc.testing.FakeServiceGrpc.FakeServiceImplBase;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.BlockingDeque;

public class InstrumentedFakeServiceImpl extends FakeServiceImplBase {
  private BlockingDeque<Color> requests = Queues.newLinkedBlockingDeque();
  private volatile StreamObserver<Money> responseStream;

  @Override
  public void recognize(Color request, StreamObserver<Money> responseObserver) {
    this.responseStream = responseObserver;
    requests.addLast(request);
  }

  @Override
  public StreamObserver<Color> streamingRecognize(StreamObserver<Money> responseObserver) {
    this.responseStream = responseObserver;

    return new QueuingRequestStreamObserver(requests);
  }

  @Override
  public void serverStreamingRecognize(Color request, StreamObserver<Money> responseObserver) {
    this.responseStream = responseObserver;
    requests.addLast(request);
  }

  @Override
  public StreamObserver<Color> clientStreamingRecognize(StreamObserver<Money> responseObserver) {
    this.responseStream = responseObserver;
    return new QueuingRequestStreamObserver(requests);
  }

  public Color awaitNextRequest() {
    try {
      return requests.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  public StreamObserver<Money> getResponseStream() {
    Preconditions.checkState(
        responseStream != null, "No current response stream, awaitNextRequest() first");
    return responseStream;
  }

  private static class QueuingRequestStreamObserver implements StreamObserver<Color> {
    private final BlockingDeque<Color> queue;

    public QueuingRequestStreamObserver(BlockingDeque<Color> queue) {
      this.queue = queue;
    }

    @Override
    public void onNext(Color request) {
      queue.addLast(request);
    }

    @Override
    public void onError(Throwable throwable) {}

    @Override
    public void onCompleted() {}
  }
}
