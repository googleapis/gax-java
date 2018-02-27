/*
 * Copyright 2016 Google LLC
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

import com.google.api.core.BetaApi;
import com.google.longrunning.CancelOperationRequest;
import com.google.longrunning.DeleteOperationRequest;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.ListOperationsRequest;
import com.google.longrunning.ListOperationsResponse;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsGrpc.OperationsImplBase;
import com.google.protobuf.Empty;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/** A custom mock Operations service implementation which only mocks responses for GetOperation. */
@BetaApi
public class MockOperationsExImpl extends OperationsImplBase {
  private ArrayList<GeneratedMessageV3> requests;
  private Queue<Object> getOperationResponses;

  public MockOperationsExImpl() {
    requests = new ArrayList<>();
    getOperationResponses = new LinkedList<>();
  }

  public List<GeneratedMessageV3> getRequests() {
    return requests;
  }

  public void addGetOperationResponse(GeneratedMessageV3 response) {
    this.getOperationResponses.add(response);
  }

  public void addGetOperationError(Throwable error) {
    this.getOperationResponses.add(error);
  }

  public void reset() {
    requests = new ArrayList<>();
    getOperationResponses = new LinkedList<>();
  }

  @Override
  public void getOperation(
      GetOperationRequest request, StreamObserver<Operation> responseObserver) {
    requests.add(request);
    Object response = getOperationResponses.remove();
    if (response instanceof Throwable) {
      responseObserver.onError((Throwable) response);
    } else if (response instanceof GeneratedMessageV3) {
      responseObserver.onNext((Operation) response);
      responseObserver.onCompleted();
    }
  }

  @Override
  public void listOperations(
      ListOperationsRequest request, StreamObserver<ListOperationsResponse> responseObserver) {
    requests.add(request);
    responseObserver.onError(
        new UnsupportedOperationException(
            "MockOperationsExImpl: listOperations not yet supported"));
  }

  @Override
  public void cancelOperation(
      CancelOperationRequest request, StreamObserver<Empty> responseObserver) {
    requests.add(request);
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void deleteOperation(
      DeleteOperationRequest request, StreamObserver<Empty> responseObserver) {
    requests.add(request);
    responseObserver.onError(
        new UnsupportedOperationException(
            "MockOperationsExImpl: listOperations not yet supported"));
  }
}
