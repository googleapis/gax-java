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
package com.google.longrunning;

import static com.google.longrunning.PagedResponseWrappers.ListOperationsPagedResponse;

import com.google.api.gax.grpc.ChannelAndExecutor;
import com.google.api.gax.grpc.UnaryCallable;
import com.google.api.gax.protobuf.PathTemplate;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

// AUTO-GENERATED DOCUMENTATION AND SERVICE
/**
 * Service Description: Manages long-running operations with an API service.
 *
 * <p>When an API method normally takes long time to complete, it can be designed to return
 * [Operation][google.longrunning.Operation] to the client, and the client can use this interface to
 * receive the real response asynchronously by polling the operation resource, or pass the operation
 * resource to another API (such as Google Cloud Pub/Sub API) to receive the response. Any API
 * service that returns long-running operations should implement the `Operations` interface so
 * developers can have a consistent client experience.
 *
 * <p>This class provides the ability to make remote calls to the backing service through method
 * calls that map to API methods. Sample code to get started:
 *
 * <pre>
 * <code>
 * try (OperationsApi operationsApi = OperationsApi.create()) {
 *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
 *   Operation response = operationsApi.getOperation(formattedName);
 * }
 * </code>
 * </pre>
 *
 * <p>Note: close() needs to be called on the operationsApi object to clean up resources such as
 * threads. In the example above, try-with-resources is used, which automatically calls close().
 *
 * <p>The surface of this class includes several types of Java methods for each of the API's
 * methods:
 *
 * <ol>
 *   <li> A "flattened" method. With this type of method, the fields of the request type have been
 *       converted into function parameters. It may be the case that not all fields are available as
 *       parameters, and not every API method will have a flattened method entry point.
 *   <li> A "request object" method. This type of method only takes one parameter, a request object,
 *       which must be constructed before the call. Not every API method will have a request object
 *       method.
 *   <li> A "callable" method. This type of method takes no parameters and returns an immutable API
 *       callable object, which can be used to initiate calls to the service.
 * </ol>
 *
 * <p>See the individual methods for example code.
 *
 * <p>Many parameters require resource names to be formatted in a particular way. To assist with
 * these names, this class includes a format method for each type of name, and additionally a parse
 * method to extract the individual identifiers contained within names that are returned.
 *
 * <p>This class can be customized by passing in a custom instance of OperationsSettings to
 * create(). For example:
 *
 * <pre>
 * <code>
 * InstantiatingChannelProvider channelProvider =
 *     OperationsSettings.defaultChannelProviderBuilder()
 *         .setCredentialsProvider(FixedCredentialsProvider.create(myCredentials))
 *         .build();
 * OperationsSettings operationsSettings =
 *     OperationsSettings.defaultBuilder().setChannelProvider(channelProvider).build();
 * OperationsApi operationsApi =
 *     OperationsApi.create(operationsSettings);
 * </code>
 * </pre>
 */
@javax.annotation.Generated("by GAPIC")
public class OperationsApi implements AutoCloseable {
  private final OperationsSettings settings;
  private final ScheduledExecutorService executor;
  private final ManagedChannel channel;
  private final List<AutoCloseable> closeables = new ArrayList<>();

  private final UnaryCallable<GetOperationRequest, Operation> getOperationCallable;
  private final UnaryCallable<ListOperationsRequest, ListOperationsResponse> listOperationsCallable;
  private final UnaryCallable<ListOperationsRequest, ListOperationsPagedResponse>
      listOperationsPagedCallable;
  private final UnaryCallable<CancelOperationRequest, Empty> cancelOperationCallable;
  private final UnaryCallable<DeleteOperationRequest, Empty> deleteOperationCallable;

  private static final PathTemplate OPERATION_PATH_PATH_TEMPLATE =
      PathTemplate.createWithoutUrlEncoding("operations/{operation_path=**}");

  /**
   * Formats a string containing the fully-qualified path to represent a operation_path resource.
   */
  public static final String formatOperationPathName(String operationPath) {
    return OPERATION_PATH_PATH_TEMPLATE.instantiate("operation_path", operationPath);
  }

  /**
   * Parses the operation_path from the given fully-qualified path which represents a operation_path
   * resource.
   */
  public static final String parseOperationPathFromOperationPathName(String operationPathName) {
    return OPERATION_PATH_PATH_TEMPLATE.parse(operationPathName).get("operation_path");
  }

  /**
   * Constructs an instance of OperationsApi, using the given settings. The channels are created
   * based on the settings passed in, or defaults for any settings that are not set.
   */
  public static final OperationsApi create(OperationsSettings settings) throws IOException {
    return new OperationsApi(settings);
  }

  /**
   * Constructs an instance of OperationsApi, using the given settings. This is protected so that it
   * easy to make a subclass, but otherwise, the static factory methods should be preferred.
   */
  protected OperationsApi(OperationsSettings settings) throws IOException {
    this.settings = settings;
    ChannelAndExecutor channelAndExecutor = settings.getChannelAndExecutor();
    this.executor = channelAndExecutor.getExecutor();
    this.channel = channelAndExecutor.getChannel();

    this.getOperationCallable =
        UnaryCallable.create(settings.getOperationSettings(), this.channel, this.executor);
    this.listOperationsCallable =
        UnaryCallable.create(settings.listOperationsSettings(), this.channel, this.executor);
    this.listOperationsPagedCallable =
        UnaryCallable.createPagedVariant(
            settings.listOperationsSettings(), this.channel, this.executor);
    this.cancelOperationCallable =
        UnaryCallable.create(settings.cancelOperationSettings(), this.channel, this.executor);
    this.deleteOperationCallable =
        UnaryCallable.create(settings.deleteOperationSettings(), this.channel, this.executor);

    if (settings.getChannelProvider().shouldAutoClose()) {
      closeables.add(
          new Closeable() {
            @Override
            public void close() throws IOException {
              channel.shutdown();
            }
          });
    }
    if (settings.getExecutorProvider().shouldAutoClose()) {
      closeables.add(
          new Closeable() {
            @Override
            public void close() throws IOException {
              executor.shutdown();
            }
          });
    }
  }

  public final OperationsSettings getSettings() {
    return settings;
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Gets the latest state of a long-running operation. Clients can use this method to poll the
   * operation result at intervals as recommended by the API service.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   Operation response = operationsApi.getOperation(formattedName);
   * }
   * </code></pre>
   *
   * @param name The name of the operation resource.
   * @throws com.google.api.gax.grpc.ApiException if the remote call fails
   */
  public final Operation getOperation(String name) {
    OPERATION_PATH_PATH_TEMPLATE.validate(name, "getOperation");
    GetOperationRequest request = GetOperationRequest.newBuilder().setName(name).build();
    return getOperation(request);
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Gets the latest state of a long-running operation. Clients can use this method to poll the
   * operation result at intervals as recommended by the API service.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   GetOperationRequest request = GetOperationRequest.newBuilder()
   *     .setName(formattedName)
   *     .build();
   *   Operation response = operationsApi.getOperation(request);
   * }
   * </code></pre>
   *
   * @param request The request object containing all of the parameters for the API call.
   * @throws com.google.api.gax.grpc.ApiException if the remote call fails
   */
  private final Operation getOperation(GetOperationRequest request) {
    return getOperationCallable().call(request);
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Gets the latest state of a long-running operation. Clients can use this method to poll the
   * operation result at intervals as recommended by the API service.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   GetOperationRequest request = GetOperationRequest.newBuilder()
   *     .setName(formattedName)
   *     .build();
   *   ListenableFuture&lt;Operation&gt; future = operationsApi.getOperationCallable().futureCall(request);
   *   // Do something
   *   Operation response = future.get();
   * }
   * </code></pre>
   */
  public final UnaryCallable<GetOperationRequest, Operation> getOperationCallable() {
    return getOperationCallable;
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Lists operations that match the specified filter in the request. If the server doesn't support
   * this method, it returns `UNIMPLEMENTED`.
   *
   * <p>NOTE: the `name` binding below allows API services to override the binding to use different
   * resource name schemes, such as `users/&#42;/operations`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String name = "";
   *   String filter = "";
   *   for (Operation element : operationsApi.listOperations(name, filter).iterateAllElements()) {
   *     // doThingsWith(element);
   *   }
   * }
   * </code></pre>
   *
   * @param name The name of the operation collection.
   * @param filter The standard list filter.
   * @throws com.google.api.gax.grpc.ApiException if the remote call fails
   */
  public final ListOperationsPagedResponse listOperations(String name, String filter) {
    ListOperationsRequest request =
        ListOperationsRequest.newBuilder().setName(name).setFilter(filter).build();
    return listOperations(request);
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Lists operations that match the specified filter in the request. If the server doesn't support
   * this method, it returns `UNIMPLEMENTED`.
   *
   * <p>NOTE: the `name` binding below allows API services to override the binding to use different
   * resource name schemes, such as `users/&#42;/operations`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String name = "";
   *   String filter = "";
   *   ListOperationsRequest request = ListOperationsRequest.newBuilder()
   *     .setName(name)
   *     .setFilter(filter)
   *     .build();
   *   for (Operation element : operationsApi.listOperations(request).iterateAllElements()) {
   *     // doThingsWith(element);
   *   }
   * }
   * </code></pre>
   *
   * @param request The request object containing all of the parameters for the API call.
   * @throws com.google.api.gax.grpc.ApiException if the remote call fails
   */
  public final ListOperationsPagedResponse listOperations(ListOperationsRequest request) {
    return listOperationsPagedCallable().call(request);
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Lists operations that match the specified filter in the request. If the server doesn't support
   * this method, it returns `UNIMPLEMENTED`.
   *
   * <p>NOTE: the `name` binding below allows API services to override the binding to use different
   * resource name schemes, such as `users/&#42;/operations`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String name = "";
   *   String filter = "";
   *   ListOperationsRequest request = ListOperationsRequest.newBuilder()
   *     .setName(name)
   *     .setFilter(filter)
   *     .build();
   *   ListenableFuture&lt;ListOperationsPagedResponse&gt; future = operationsApi.listOperationsPagedCallable().futureCall(request);
   *   // Do something
   *   for (Operation element : future.get().iterateAllElements()) {
   *     // doThingsWith(element);
   *   }
   * }
   * </code></pre>
   */
  public final UnaryCallable<ListOperationsRequest, ListOperationsPagedResponse>
      listOperationsPagedCallable() {
    return listOperationsPagedCallable;
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Lists operations that match the specified filter in the request. If the server doesn't support
   * this method, it returns `UNIMPLEMENTED`.
   *
   * <p>NOTE: the `name` binding below allows API services to override the binding to use different
   * resource name schemes, such as `users/&#42;/operations`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String name = "";
   *   String filter = "";
   *   ListOperationsRequest request = ListOperationsRequest.newBuilder()
   *     .setName(name)
   *     .setFilter(filter)
   *     .build();
   *   while (true) {
   *     ListOperationsResponse response = operationsApi.listOperationsCallable().call(request);
   *     for (Operation element : response.getOperationsList()) {
   *       // doThingsWith(element);
   *     }
   *     String nextPageToken = response.getNextPageToken();
   *     if (!Strings.isNullOrEmpty(nextPageToken)) {
   *       request = request.toBuilder().setPageToken(nextPageToken).build();
   *     } else {
   *       break;
   *     }
   *   }
   * }
   * </code></pre>
   */
  public final UnaryCallable<ListOperationsRequest, ListOperationsResponse>
      listOperationsCallable() {
    return listOperationsCallable;
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Starts asynchronous cancellation on a long-running operation. The server makes a best effort to
   * cancel the operation, but success is not guaranteed. If the server doesn't support this method,
   * it returns `google.rpc.Code.UNIMPLEMENTED`. Clients can use
   * [Operations.GetOperation][google.longrunning.Operations.GetOperation] or other methods to check
   * whether the cancellation succeeded or whether the operation completed despite cancellation. On
   * successful cancellation, the operation is not deleted; instead, it becomes an operation with an
   * [Operation.error][google.longrunning.Operation.error] value with a
   * [google.rpc.Status.code][google.rpc.Status.code] of 1, corresponding to `Code.CANCELLED`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   operationsApi.cancelOperation(formattedName);
   * }
   * </code></pre>
   *
   * @param name The name of the operation resource to be cancelled.
   * @throws com.google.api.gax.grpc.ApiException if the remote call fails
   */
  public final void cancelOperation(String name) {
    OPERATION_PATH_PATH_TEMPLATE.validate(name, "cancelOperation");
    CancelOperationRequest request = CancelOperationRequest.newBuilder().setName(name).build();
    cancelOperation(request);
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Starts asynchronous cancellation on a long-running operation. The server makes a best effort to
   * cancel the operation, but success is not guaranteed. If the server doesn't support this method,
   * it returns `google.rpc.Code.UNIMPLEMENTED`. Clients can use
   * [Operations.GetOperation][google.longrunning.Operations.GetOperation] or other methods to check
   * whether the cancellation succeeded or whether the operation completed despite cancellation. On
   * successful cancellation, the operation is not deleted; instead, it becomes an operation with an
   * [Operation.error][google.longrunning.Operation.error] value with a
   * [google.rpc.Status.code][google.rpc.Status.code] of 1, corresponding to `Code.CANCELLED`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   CancelOperationRequest request = CancelOperationRequest.newBuilder()
   *     .setName(formattedName)
   *     .build();
   *   operationsApi.cancelOperation(request);
   * }
   * </code></pre>
   *
   * @param request The request object containing all of the parameters for the API call.
   * @throws com.google.api.gax.grpc.ApiException if the remote call fails
   */
  private final void cancelOperation(CancelOperationRequest request) {
    cancelOperationCallable().call(request);
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Starts asynchronous cancellation on a long-running operation. The server makes a best effort to
   * cancel the operation, but success is not guaranteed. If the server doesn't support this method,
   * it returns `google.rpc.Code.UNIMPLEMENTED`. Clients can use
   * [Operations.GetOperation][google.longrunning.Operations.GetOperation] or other methods to check
   * whether the cancellation succeeded or whether the operation completed despite cancellation. On
   * successful cancellation, the operation is not deleted; instead, it becomes an operation with an
   * [Operation.error][google.longrunning.Operation.error] value with a
   * [google.rpc.Status.code][google.rpc.Status.code] of 1, corresponding to `Code.CANCELLED`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   CancelOperationRequest request = CancelOperationRequest.newBuilder()
   *     .setName(formattedName)
   *     .build();
   *   ListenableFuture&lt;Void&gt; future = operationsApi.cancelOperationCallable().futureCall(request);
   *   // Do something
   *   future.get();
   * }
   * </code></pre>
   */
  public final UnaryCallable<CancelOperationRequest, Empty> cancelOperationCallable() {
    return cancelOperationCallable;
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Deletes a long-running operation. This method indicates that the client is no longer interested
   * in the operation result. It does not cancel the operation. If the server doesn't support this
   * method, it returns `google.rpc.Code.UNIMPLEMENTED`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   operationsApi.deleteOperation(formattedName);
   * }
   * </code></pre>
   *
   * @param name The name of the operation resource to be deleted.
   * @throws com.google.api.gax.grpc.ApiException if the remote call fails
   */
  public final void deleteOperation(String name) {
    OPERATION_PATH_PATH_TEMPLATE.validate(name, "deleteOperation");
    DeleteOperationRequest request = DeleteOperationRequest.newBuilder().setName(name).build();
    deleteOperation(request);
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Deletes a long-running operation. This method indicates that the client is no longer interested
   * in the operation result. It does not cancel the operation. If the server doesn't support this
   * method, it returns `google.rpc.Code.UNIMPLEMENTED`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   DeleteOperationRequest request = DeleteOperationRequest.newBuilder()
   *     .setName(formattedName)
   *     .build();
   *   operationsApi.deleteOperation(request);
   * }
   * </code></pre>
   *
   * @param request The request object containing all of the parameters for the API call.
   * @throws com.google.api.gax.grpc.ApiException if the remote call fails
   */
  private final void deleteOperation(DeleteOperationRequest request) {
    deleteOperationCallable().call(request);
  }

  // AUTO-GENERATED DOCUMENTATION AND METHOD
  /**
   * Deletes a long-running operation. This method indicates that the client is no longer interested
   * in the operation result. It does not cancel the operation. If the server doesn't support this
   * method, it returns `google.rpc.Code.UNIMPLEMENTED`.
   *
   * <p>Sample code:
   *
   * <pre><code>
   * try (OperationsApi operationsApi = OperationsApi.create()) {
   *   String formattedName = OperationsApi.formatOperationPathName("[OPERATION_PATH]");
   *   DeleteOperationRequest request = DeleteOperationRequest.newBuilder()
   *     .setName(formattedName)
   *     .build();
   *   ListenableFuture&lt;Void&gt; future = operationsApi.deleteOperationCallable().futureCall(request);
   *   // Do something
   *   future.get();
   * }
   * </code></pre>
   */
  public final UnaryCallable<DeleteOperationRequest, Empty> deleteOperationCallable() {
    return deleteOperationCallable;
  }

  /**
   * Initiates an orderly shutdown in which preexisting calls continue but new calls are immediately
   * cancelled.
   */
  @Override
  public final void close() throws Exception {
    for (AutoCloseable closeable : closeables) {
      closeable.close();
    }
  }
}
