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
package com.google.longrunning;

import static com.google.longrunning.OperationsClient.ListOperationsPagedResponse;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.testing.LocalChannelProvider;
import com.google.api.gax.grpc.testing.MockGrpcService;
import com.google.api.gax.grpc.testing.MockServiceHelper;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.common.collect.Lists;
import com.google.protobuf.Empty;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@javax.annotation.Generated("by GAPIC")
public class OperationsClientTest {
  private static MockOperations mockOperations;
  private static MockServiceHelper serviceHelper;
  private OperationsClient client;
  private LocalChannelProvider channelProvider;

  @BeforeClass
  public static void startStaticServer() {
    mockOperations = new MockOperations();
    serviceHelper =
        new MockServiceHelper("in-process-1", Arrays.<MockGrpcService>asList(mockOperations));
    serviceHelper.start();
  }

  @AfterClass
  public static void stopServer() {
    serviceHelper.stop();
  }

  @Before
  public void setUp() throws IOException {
    serviceHelper.reset();
    channelProvider = serviceHelper.createChannelProvider();
    OperationsSettings settings =
        OperationsSettings.newBuilder()
            .setTransportChannelProvider(channelProvider)
            .setCredentialsProvider(NoCredentialsProvider.create())
            .build();
    client = OperationsClient.create(settings);
  }

  @After
  public void tearDown() throws Exception {
    client.close();
  }

  @Test
  @SuppressWarnings("all")
  public void getOperationTest() {
    String name2 = "name2-1052831874";
    boolean done = true;
    Operation expectedResponse = Operation.newBuilder().setName(name2).setDone(done).build();
    mockOperations.addResponse(expectedResponse);

    String name = "name3373707";

    Operation actualResponse = client.getOperation(name);
    Assert.assertEquals(expectedResponse, actualResponse);

    List<GeneratedMessageV3> actualRequests = mockOperations.getRequests();
    Assert.assertEquals(1, actualRequests.size());
    GetOperationRequest actualRequest = (GetOperationRequest) actualRequests.get(0);

    Assert.assertEquals(name, actualRequest.getName());
  }

  @Test
  @SuppressWarnings("all")
  public void getOperationExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(Status.INVALID_ARGUMENT);
    mockOperations.addException(exception);

    try {
      String name = "name3373707";

      client.getOperation(name);
      Assert.fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception
    }
  }

  @Test
  @SuppressWarnings("all")
  public void listOperationsTest() {
    String nextPageToken = "";
    Operation operationsElement = Operation.newBuilder().build();
    List<Operation> operations = Arrays.asList(operationsElement);
    ListOperationsResponse expectedResponse =
        ListOperationsResponse.newBuilder()
            .setNextPageToken(nextPageToken)
            .addAllOperations(operations)
            .build();
    mockOperations.addResponse(expectedResponse);

    String name = "name3373707";
    String filter = "filter-1274492040";

    ListOperationsPagedResponse pagedListResponse = client.listOperations(name, filter);

    List<Operation> resources = Lists.newArrayList(pagedListResponse.iterateAll());
    Assert.assertEquals(1, resources.size());
    Assert.assertEquals(expectedResponse.getOperationsList().get(0), resources.get(0));

    List<GeneratedMessageV3> actualRequests = mockOperations.getRequests();
    Assert.assertEquals(1, actualRequests.size());
    ListOperationsRequest actualRequest = (ListOperationsRequest) actualRequests.get(0);

    Assert.assertEquals(name, actualRequest.getName());
    Assert.assertEquals(filter, actualRequest.getFilter());
  }

  @Test
  @SuppressWarnings("all")
  public void listOperationsExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(Status.INVALID_ARGUMENT);
    mockOperations.addException(exception);

    try {
      String name = "name3373707";
      String filter = "filter-1274492040";

      client.listOperations(name, filter);
      Assert.fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception
    }
  }

  @Test
  @SuppressWarnings("all")
  public void cancelOperationTest() {
    Empty expectedResponse = Empty.newBuilder().build();
    mockOperations.addResponse(expectedResponse);

    String name = "name3373707";

    client.cancelOperation(name);

    List<GeneratedMessageV3> actualRequests = mockOperations.getRequests();
    Assert.assertEquals(1, actualRequests.size());
    CancelOperationRequest actualRequest = (CancelOperationRequest) actualRequests.get(0);

    Assert.assertEquals(name, actualRequest.getName());
  }

  @Test
  @SuppressWarnings("all")
  public void cancelOperationExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(Status.INVALID_ARGUMENT);
    mockOperations.addException(exception);

    try {
      String name = "name3373707";

      client.cancelOperation(name);
      Assert.fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception
    }
  }

  @Test
  @SuppressWarnings("all")
  public void deleteOperationTest() {
    Empty expectedResponse = Empty.newBuilder().build();
    mockOperations.addResponse(expectedResponse);

    String name = "name3373707";

    client.deleteOperation(name);

    List<GeneratedMessageV3> actualRequests = mockOperations.getRequests();
    Assert.assertEquals(1, actualRequests.size());
    DeleteOperationRequest actualRequest = (DeleteOperationRequest) actualRequests.get(0);

    Assert.assertEquals(name, actualRequest.getName());
  }

  @Test
  @SuppressWarnings("all")
  public void deleteOperationExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(Status.INVALID_ARGUMENT);
    mockOperations.addException(exception);

    try {
      String name = "name3373707";

      client.deleteOperation(name);
      Assert.fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception
    }
  }
}
