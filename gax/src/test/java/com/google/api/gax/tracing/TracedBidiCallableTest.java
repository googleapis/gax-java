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
package com.google.api.gax.tracing;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.BidiStream;
import com.google.api.gax.rpc.BidiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ClientStreamReadyObserver;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class TracedBidiCallableTest {
  private static final SpanName SPAN_NAME = SpanName.of("fake-client", "fake-method");
  public @Rule MockitoRule mockitoRule = MockitoJUnit.rule();

  private FakeBidiObserver outerObserver;
  private FakeCallContext outerCallContext;

  @Mock private ApiTracerFactory tracerFactory;
  @Mock private ApiTracer tracer;

  private TracedBidiCallable<String, String> tracedCallable;

  private FakeBidiCallable innerCallable;

  @Before
  public void setUp() {
    outerObserver = new FakeBidiObserver();
    outerCallContext = FakeCallContext.createDefault();

    Mockito.when(tracerFactory.newTracer(SPAN_NAME)).thenReturn(tracer);

    innerCallable = new FakeBidiCallable();
    tracedCallable = new TracedBidiCallable<>(innerCallable, tracerFactory, SPAN_NAME);
  }

  @Test
  public void testTracerCreated() {
    tracedCallable.call(outerObserver, outerCallContext);

    Mockito.verify(tracerFactory, Mockito.times(1)).newTracer(SPAN_NAME);
  }

  @Test
  public void testOperationFinished() {
    tracedCallable.call(outerObserver, outerCallContext);
    innerCallable.responseObserver.onComplete();

    Mockito.verify(tracer, Mockito.times(1)).operationSucceeded();
    assertThat(outerObserver.complete).isTrue();
  }

  @Test
  public void testOperationFailed() {
    RuntimeException expectedException = new RuntimeException("fake");

    tracedCallable.call(outerObserver, outerCallContext);
    innerCallable.responseObserver.onError(expectedException);

    Mockito.verify(tracer, Mockito.times(1)).operationFailed(expectedException);
    assertThat(outerObserver.complete).isTrue();
    assertThat(outerObserver.error).isEqualTo(expectedException);
  }

  @Test
  public void testSyncError() {
    RuntimeException expectedException = new RuntimeException("fake");
    innerCallable.syncError = expectedException;

    try {
      tracedCallable.call(outerObserver, outerCallContext);
    } catch (RuntimeException e) {
      // noop
    }

    Mockito.verify(tracer, Mockito.times(1)).operationFailed(expectedException);
  }

  @Test
  public void testRequestNotify() {
    tracedCallable.call(outerObserver, outerCallContext);
    outerObserver.clientStream.send("request1");
    outerObserver.clientStream.send("request2");

    Mockito.verify(tracer, Mockito.times(2)).requestSent();
    assertThat(innerCallable.clientStream.sent).containsExactly("request1", "request2");
  }

  @Test
  public void testRequestNotify2() {
    BidiStream<String, String> stream = tracedCallable.call(outerCallContext);
    stream.send("request1");
    stream.send("request2");

    Mockito.verify(tracer, Mockito.times(2)).requestSent();
    assertThat(innerCallable.clientStream.sent).containsExactly("request1", "request2");
  }

  @Test
  public void testResponseNotify() {
    tracedCallable.call(outerObserver, outerCallContext);

    innerCallable.responseObserver.onResponse("response1");
    innerCallable.responseObserver.onResponse("response2");

    Mockito.verify(tracer, Mockito.times(2)).responseReceived();
    assertThat(outerObserver.responses).containsExactly("response1", "response2");
  }

  private static class FakeBidiCallable extends BidiStreamingCallable<String, String> {
    RuntimeException syncError;

    ResponseObserver<String> responseObserver;
    ClientStreamReadyObserver<String> onReady;
    ApiCallContext callContext;
    FakeClientStream clientStream;

    @Override
    public ClientStream<String> internalCall(
        ResponseObserver<String> responseObserver,
        ClientStreamReadyObserver<String> onReady,
        ApiCallContext context) {

      if (syncError != null) {
        throw syncError;
      }

      this.responseObserver = responseObserver;
      this.onReady = onReady;
      this.callContext = context;
      this.clientStream = new FakeClientStream();

      onReady.onReady(clientStream);
      return clientStream;
    }
  }

  private static class FakeClientStream implements ClientStream<String> {
    private List<String> sent = Lists.newArrayList();
    private Throwable closeError;
    private boolean closed;

    @Override
    public void send(String request) {
      sent.add(request);
    }

    @Override
    public void closeSendWithError(Throwable t) {
      closed = true;
      closeError = t;
    }

    @Override
    public void closeSend() {
      closed = true;
    }

    @Override
    public boolean isSendReady() {
      return true;
    }
  }

  private static class FakeBidiObserver implements BidiStreamObserver<String, String> {
    ClientStream<String> clientStream;
    StreamController streamController;
    List<String> responses = Lists.newArrayList();
    Throwable error;
    boolean complete;

    @Override
    public void onReady(ClientStream<String> stream) {
      this.clientStream = stream;
    }

    @Override
    public void onStart(StreamController controller) {
      this.streamController = controller;
    }

    @Override
    public void onResponse(String response) {
      responses.add(response);
    }

    @Override
    public void onError(Throwable t) {
      this.error = t;
      this.complete = true;
    }

    @Override
    public void onComplete() {
      this.complete = true;
    }
  }
}
