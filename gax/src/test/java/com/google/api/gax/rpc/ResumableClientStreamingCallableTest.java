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
package com.google.api.gax.rpc;

import com.google.api.gax.retrying.ClientStreamResumptionStrategy;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.api.gax.rpc.testing.FakeStreamingApi.ClientStreamingStashCallable;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResumableClientStreamingCallableTest {
  private static Throwable unavailable =
      ApiExceptionFactory.createException(
          new Throwable("Things aren't working!"), FakeStatusCode.of(Code.UNAVAILABLE), true);
  private static Throwable badRequest =
      ApiExceptionFactory.createException(
          new Throwable("Bad payload."), FakeStatusCode.of(Code.INVALID_ARGUMENT), false);

  private class FakeResumer<RequestT, ResponseT>
      implements ClientStreamResumptionStrategy<RequestT, ResponseT> {
    RequestT lastSent;

    @Override
    public ClientStreamResumptionStrategy<RequestT, ResponseT> createNew() {
      return new FakeResumer<>();
    }

    @Override
    public RequestT processRequest(RequestT request) {
      lastSent = request;
      return request;
    }

    @Override
    public boolean resumable(Throwable t) {
      return t instanceof UnavailableException;
    }

    @Override
    public void resume(ApiStreamObserver<RequestT> requestObserver) {
      requestObserver.onNext(lastSent);
    }
  }

  private static class AccumulatingStreamObserver implements ApiStreamObserver<Integer> {
    private List<Integer> values = new ArrayList<>();
    private Throwable error;
    private boolean completed = false;

    @Override
    public void onNext(Integer value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable t) {
      error = t;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }

    public List<Integer> getValues() {
      if (!completed) {
        throw new IllegalStateException("Stream not completed.");
      }
      if (error != null) {
        throw ApiExceptionFactory.createException(error, FakeStatusCode.of(Code.UNKNOWN), false);
      }
      return values;
    }
  }

  @Test
  public void resumableClientStreaming() {
    ClientStreamingStashCallable<Integer, Integer> callIntList =
        new ClientStreamingStashCallable<>(100);

    // Wrap the API callable in the resumable one as if the callable factory
    // had done it.
    ClientStreamingCallable<Integer, Integer> callable =
        new ResumableClientStreamingCallable<Integer, Integer>(
            callIntList, new FakeResumer<Integer, Integer>());

    AccumulatingStreamObserver responseObserver = new AccumulatingStreamObserver();
    ApiStreamObserver<Integer> requestObserver = callable.clientStreamingCall(responseObserver);
    requestObserver.onNext(0);
    requestObserver.onNext(2);

    // Mimic the server sending an error by calling the responseObserver onError.
    // This "acutalObserver" is the observer wrapped by ResumableStreamObserver.
    ApiStreamObserver<Integer> actualObserver = callIntList.getActualObserver();
    actualObserver.onError(unavailable);

    // Keep writing from the client side.
    requestObserver.onNext(4);
    requestObserver.onCompleted();

    Truth.assertThat(ImmutableList.copyOf(responseObserver.getValues()))
        .containsExactly(100)
        .inOrder();

    // Expect that the stream was restarted and the simple resumption strategy started over
    // by writing the last seen request to the new stream, hence duplicate '2' requests.
    //
    // Note: This does not account for synchronization of user code calling onNext while
    // the stream is being resumed, because this test runs things sequentially.
    Truth.assertThat(callIntList.getActualRequests()).containsExactly(0, 2, 2, 4).inOrder();
  }

  @Test(expected = UnknownException.class)
  public void resumableClientStreamingNoResume() {
    ClientStreamingStashCallable<Integer, Integer> callIntList =
        new ClientStreamingStashCallable<>(100);

    // Wrap the API callable in the resumable one as if the callable factory
    // had done it.
    ClientStreamingCallable<Integer, Integer> callable =
        new ResumableClientStreamingCallable<Integer, Integer>(
            callIntList, new FakeResumer<Integer, Integer>());

    AccumulatingStreamObserver responseObserver = new AccumulatingStreamObserver();
    ApiStreamObserver<Integer> requestObserver = callable.clientStreamingCall(responseObserver);
    requestObserver.onNext(0);
    requestObserver.onNext(2);

    // Mimic the server sending an error by calling the responseObserver onError.
    // This "acutalObserver" is the observer wrapped by ResumableStreamObserver.
    ApiStreamObserver<Integer> actualObserver = callIntList.getActualObserver();
    actualObserver.onError(badRequest);

    // Close the client side.
    requestObserver.onCompleted();

    // This will throw the exception that the resumption strategy didn't swallow/resume.
    responseObserver.getValues();
  }
}
