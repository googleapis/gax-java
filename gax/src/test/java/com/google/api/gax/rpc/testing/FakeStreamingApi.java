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
package com.google.api.gax.rpc.testing;

import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.StreamController;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

@InternalApi("for testing")
public class FakeStreamingApi {

  public static class BidiStreamingStashCallable<RequestT, ResponseT>
      extends BidiStreamingCallable<RequestT, ResponseT> {
    private ApiCallContext context;
    private ApiStreamObserver<ResponseT> responseObserver;
    private AccumulatingStreamObserver<RequestT> requestObserver;
    private List<ResponseT> responseList;

    public BidiStreamingStashCallable() {
      responseList = new ArrayList<>();
    }

    public BidiStreamingStashCallable(List<ResponseT> responseList) {
      this.responseList = responseList;
    }

    @Override
    public ApiStreamObserver<RequestT> bidiStreamingCall(
        ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
      Preconditions.checkNotNull(responseObserver);
      this.responseObserver = responseObserver;
      this.context = context;
      this.requestObserver = new AccumulatingStreamObserver<>();
      return requestObserver;
    }

    public ApiCallContext getContext() {
      return context;
    }

    public ApiStreamObserver<ResponseT> getActualObserver() {
      return responseObserver;
    }

    public List<RequestT> getActualRequests() {
      return requestObserver.getValues();
    }

    private void sendResponses() {
      for (ResponseT response : responseList) {
        responseObserver.onNext(response);
      }
      responseObserver.onCompleted();
    }

    private class AccumulatingStreamObserver<T> implements ApiStreamObserver<T> {
      private List<T> requestList = new ArrayList<>();
      private Throwable error;
      private boolean completed = false;

      @Override
      public void onNext(T value) {
        requestList.add(value);
      }

      @Override
      public void onError(Throwable t) {
        error = t;
      }

      @Override
      public void onCompleted() {
        completed = true;
        BidiStreamingStashCallable.this.sendResponses();
      }

      public List<T> getValues() {
        if (!completed) {
          throw new IllegalStateException("Stream not completed.");
        }
        if (error != null) {
          throw ApiExceptionFactory.createException(error, FakeStatusCode.of(Code.UNKNOWN), false);
        }
        return requestList;
      }
    }
  }

  public static class ServerStreamingStashCallable<RequestT, ResponseT>
      extends ServerStreamingCallable<RequestT, ResponseT> {
    private ApiCallContext context;
    private ResponseObserver<ResponseT> actualObserver;
    private RequestT actualRequest;
    private List<ResponseT> responseList;

    public ServerStreamingStashCallable() {
      responseList = new ArrayList<>();
    }

    public ServerStreamingStashCallable(List<ResponseT> responseList) {
      this.responseList = responseList;
    }

    @Override
    public void call(
        RequestT request, ResponseObserver<ResponseT> responseObserver, ApiCallContext context) {
      Preconditions.checkNotNull(request);
      Preconditions.checkNotNull(responseObserver);
      this.actualRequest = request;
      this.actualObserver = responseObserver;
      this.context = context;

      StashStreamController<ResponseT> c =
          new StashStreamController<>(responseObserver, responseList);
      c.start();
    }

    public ApiCallContext getContext() {
      return context;
    }

    public ResponseObserver<ResponseT> getActualObserver() {
      return actualObserver;
    }

    public RequestT getActualRequest() {
      return actualRequest;
    }
  }

  public static class StashStreamController<V> extends StreamController {
    private final Queue<V> responseList;
    private boolean autoflowControl = true;
    private int pendingRequests;
    private boolean inDelivery;
    private boolean done;

    private ResponseObserver<V> observer;

    public StashStreamController(ResponseObserver<V> observer, List<V> responseList) {
      this.observer = observer;
      this.responseList = Queues.newArrayDeque(responseList);
    }

    public void start() {
      observer.onStart(this);

      if (responseList.isEmpty()) {
        finish(null);
      } else if (autoflowControl) {
        request(1);
      }
    }

    @Override
    public void cancel(Throwable cause) {
      finish(cause);
    }

    @Override
    public void disableAutoInboundFlowControl() {
      autoflowControl = false;
    }

    @Override
    public void request(int count) {
      pendingRequests++;
      deliver();
    }

    private void deliver() {
      if (inDelivery) return;
      inDelivery = true;

      try {
        while (!done && pendingRequests > 0 && !responseList.isEmpty()) {
          observer.onResponse(responseList.remove());
          pendingRequests--;

          if (autoflowControl) {
            pendingRequests++;
          }
        }
      } finally {
        inDelivery = false;
      }

      if (!done && responseList.isEmpty()) {
        finish(null);
      }
    }

    private void finish(Throwable error) {
      if (done) return;
      done = true;
      if (error == null) {
        observer.onComplete();
      } else {
        observer.onError(error);
      }
    }
  }

  public static class ClientStreamingStashCallable<RequestT, ResponseT>
      extends ClientStreamingCallable<RequestT, ResponseT> {
    private ApiCallContext context;
    private ApiStreamObserver<ResponseT> responseObserver;
    private AccumulatingStreamObserver<RequestT> requestObserver;
    private ResponseT response;

    public ClientStreamingStashCallable() {}

    public ClientStreamingStashCallable(ResponseT response) {
      this.response = response;
    }

    @Override
    public ApiStreamObserver<RequestT> clientStreamingCall(
        ApiStreamObserver<ResponseT> responseObserver, ApiCallContext context) {
      Preconditions.checkNotNull(responseObserver);
      this.responseObserver = responseObserver;
      this.context = context;
      this.requestObserver = new AccumulatingStreamObserver<>();
      return requestObserver;
    }

    public ApiCallContext getContext() {
      return context;
    }

    public ApiStreamObserver<ResponseT> getActualObserver() {
      return responseObserver;
    }

    public List<RequestT> getActualRequests() {
      return requestObserver.getValues();
    }

    private void sendResponses() {
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    private class AccumulatingStreamObserver<T> implements ApiStreamObserver<T> {
      private List<T> requestList = new ArrayList<>();
      private Throwable error;
      private boolean completed = false;

      @Override
      public void onNext(T value) {
        requestList.add(value);
      }

      @Override
      public void onError(Throwable t) {
        error = t;
      }

      @Override
      public void onCompleted() {
        completed = true;
        ClientStreamingStashCallable.this.sendResponses();
      }

      public List<T> getValues() {
        if (!completed) {
          throw new IllegalStateException("Stream not completed.");
        }
        if (error != null) {
          throw ApiExceptionFactory.createException(error, FakeStatusCode.of(Code.UNKNOWN), false);
        }
        return requestList;
      }
    }
  }
}
