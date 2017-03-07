/*
 * Copyright 2016, Google Inc. All rights reserved.
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

import com.google.api.gax.core.ApiFuture;
import com.google.api.gax.testing.MockGrpcService;
import com.google.api.gax.testing.MockServiceHelper;
import com.google.common.truth.Truth;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.longrunning.OperationsSettings;
import com.google.protobuf.Any;
import com.google.type.Color;
import io.grpc.CallOptions;
import io.grpc.Channel;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class OperationCallableTest {
  private MockOperationsEx mockOperations;
  private MockServiceHelper serviceHelper;
  private OperationsClient operationsClient;
  private ScheduledExecutorService executor;

  @Before
  public void setUp() throws IOException {
    mockOperations = new MockOperationsEx();
    serviceHelper =
        new MockServiceHelper("in-process-1", Arrays.<MockGrpcService>asList(mockOperations));
    serviceHelper.start();
    OperationsSettings settings =
        OperationsSettings.defaultBuilder()
            .setChannelProvider(serviceHelper.createChannelProvider())
            .build();
    operationsClient = OperationsClient.create(settings);
    executor = new ScheduledThreadPoolExecutor(1);
  }

  @After
  public void tearDown() throws Exception {
    operationsClient.close();
    executor.shutdown();
    serviceHelper.stop();
  }

  @Test
  public void testCall() {
    String opName = "testCall";
    Color injectedResponse = Color.newBuilder().setBlue(1.0f).build();
    Operation resultOperation =
        Operation.newBuilder()
            .setName(opName)
            .setDone(true)
            .setResponse(Any.pack(injectedResponse))
            .build();
    StashCallable<Integer, Operation> stash = new StashCallable<>(resultOperation);
    UnaryCallable<Integer, Operation> stashUnaryCallable = new UnaryCallable<>(stash);

    OperationCallable<Integer, Color> callable =
        new OperationCallable<Integer, Color>(
            stashUnaryCallable, null, executor, operationsClient, Color.class, null);
    Color response = callable.call(2, CallContext.createDefault());
    Truth.assertThat(response).isEqualTo(injectedResponse);
    Truth.assertThat(stash.context.getChannel()).isNull();
    Truth.assertThat(stash.context.getCallOptions()).isEqualTo(CallOptions.DEFAULT);
  }

  @Test
  public void testBind() {
    String opName = "testBind";
    Color injectedResponse = Color.newBuilder().setBlue(1.0f).build();
    Operation resultOperation =
        Operation.newBuilder()
            .setName(opName)
            .setDone(true)
            .setResponse(Any.pack(injectedResponse))
            .build();
    StashCallable<Integer, Operation> stash = new StashCallable<>(resultOperation);
    UnaryCallable<Integer, Operation> stashUnaryCallable = new UnaryCallable<>(stash);

    Channel channel = Mockito.mock(Channel.class);
    OperationCallable<Integer, Color> callable =
        new OperationCallable<Integer, Color>(
            stashUnaryCallable, null, executor, operationsClient, Color.class, null);
    callable = callable.bind(channel);
    Color response = callable.call(2);
    Truth.assertThat(response).isEqualTo(injectedResponse);
    Truth.assertThat(stash.context.getChannel()).isSameAs(channel);
  }

  @Test
  public void testResumeFutureCall() throws Exception {
    String opName = "testCall";
    Color injectedResponse = Color.newBuilder().setBlue(1.0f).build();
    Operation resultOperation =
        Operation.newBuilder()
            .setName(opName)
            .setDone(true)
            .setResponse(Any.pack(injectedResponse))
            .build();
    StashCallable<Integer, Operation> stash = new StashCallable<>(resultOperation);
    UnaryCallable<Integer, Operation> stashUnaryCallable = new UnaryCallable<>(stash);

    mockOperations.addResponse(resultOperation);

    OperationCallable<Integer, Color> callable =
        new OperationCallable<Integer, Color>(
            stashUnaryCallable, null, executor, operationsClient, Color.class, null);
    OperationFuture<Color> operationFuture = callable.futureCall(2);

    Color response = callable.resumeFutureCall(operationFuture.getOperationName()).get();

    Truth.assertThat(response).isEqualTo(injectedResponse);
  }

  private static class StashCallable<RequestT, ResponseT>
      implements FutureCallable<RequestT, ResponseT> {
    CallContext context;
    RequestT request;
    ResponseT result;

    public StashCallable(ResponseT result) {
      this.result = result;
    }

    @Override
    public ApiFuture<ResponseT> futureCall(RequestT request, CallContext context) {
      this.request = request;
      this.context = context;
      return UnaryCallableTest.<ResponseT>immediateFuture(result);
    }
  }
}
