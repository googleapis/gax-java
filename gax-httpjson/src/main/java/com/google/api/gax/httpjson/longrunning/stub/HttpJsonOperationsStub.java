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
package com.google.api.gax.httpjson.longrunning.stub;

import static com.google.api.gax.httpjson.longrunning.OperationsClient.ListOperationsPagedResponse;

import com.google.api.client.http.HttpMethods;
import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.BackgroundResourceAggregation;
import com.google.api.gax.httpjson.ApiMethodDescriptor;
import com.google.api.gax.httpjson.FieldsExtractor;
import com.google.api.gax.httpjson.HttpJsonCallSettings;
import com.google.api.gax.httpjson.HttpJsonLongRunningClient;
import com.google.api.gax.httpjson.HttpJsonOperationSnapshot;
import com.google.api.gax.httpjson.HttpJsonStubCallableFactory;
import com.google.api.gax.httpjson.OperationSnapshotFactory;
import com.google.api.gax.httpjson.PollingRequestFactory;
import com.google.api.gax.httpjson.ProtoMessageRequestFormatter;
import com.google.api.gax.httpjson.ProtoMessageResponseParser;
import com.google.api.gax.httpjson.ProtoRestSerializer;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.LongRunningClient;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.longrunning.CancelOperationRequest;
import com.google.longrunning.DeleteOperationRequest;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.ListOperationsRequest;
import com.google.longrunning.ListOperationsResponse;
import com.google.longrunning.Operation;
import com.google.protobuf.Empty;
import com.google.protobuf.TypeRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Generated;

// AUTO-GENERATED DOCUMENTATION AND CLASS.
/**
 * REST stub implementation for the Operations service API.
 *
 * <p>This class is for advanced usage and reflects the underlying API directly.
 */
@Generated("by gapic-generator-java")
@BetaApi("A restructuring of stub classes is planned, so this may break in the future")
public class HttpJsonOperationsStub extends OperationsStub {
  private static final ApiMethodDescriptor<ListOperationsRequest, ListOperationsResponse>
      listOperationsMethodDescriptor =
          ApiMethodDescriptor.<ListOperationsRequest, ListOperationsResponse>newBuilder()
              .setFullMethodName("google.longrunning.Operations/ListOperations")
              .setHttpMethod(HttpMethods.GET)
              .setRequestFormatter(
                  ProtoMessageRequestFormatter.<ListOperationsRequest>newBuilder()
                      .setPath(
                          "/v1/{name=operations}",
                          new FieldsExtractor<ListOperationsRequest, Map<String, String>>() {
                            @Override
                            public Map<String, String> extract(ListOperationsRequest request) {
                              Map<String, String> fields = new HashMap<>();
                              ProtoRestSerializer<ListOperationsRequest> serializer =
                                  ProtoRestSerializer.create();
                              serializer.putPathParam(fields, "name", request.getName());
                              return fields;
                            }
                          })
                      .setQueryParamsExtractor(
                          new FieldsExtractor<ListOperationsRequest, Map<String, List<String>>>() {
                            @Override
                            public Map<String, List<String>> extract(
                                ListOperationsRequest request) {
                              Map<String, List<String>> fields = new HashMap<>();
                              ProtoRestSerializer<ListOperationsRequest> serializer =
                                  ProtoRestSerializer.create();
                              serializer.putQueryParam(fields, "filter", request.getFilter());
                              serializer.putQueryParam(fields, "pageSize", request.getPageSize());
                              serializer.putQueryParam(fields, "pageToken", request.getPageToken());
                              return fields;
                            }
                          })
                      .setRequestBodyExtractor(
                          new FieldsExtractor<ListOperationsRequest, String>() {
                            @Override
                            public String extract(ListOperationsRequest request) {
                              return null;
                            }
                          })
                      .build())
              .setResponseParser(
                  ProtoMessageResponseParser.<ListOperationsResponse>newBuilder()
                      .setDefaultInstance(ListOperationsResponse.getDefaultInstance())
                      .build())
              .build();

  private static final ApiMethodDescriptor<GetOperationRequest, Operation>
      getOperationMethodDescriptor =
          ApiMethodDescriptor.<GetOperationRequest, Operation>newBuilder()
              .setFullMethodName("google.longrunning.Operations/GetOperation")
              .setHttpMethod(HttpMethods.GET)
              .setRequestFormatter(
                  ProtoMessageRequestFormatter.<GetOperationRequest>newBuilder()
                      .setPath(
                          "/v1/{name=operations/**}",
                          new FieldsExtractor<GetOperationRequest, Map<String, String>>() {
                            @Override
                            public Map<String, String> extract(GetOperationRequest request) {
                              Map<String, String> fields = new HashMap<>();
                              ProtoRestSerializer<GetOperationRequest> serializer =
                                  ProtoRestSerializer.create();
                              serializer.putPathParam(fields, "name", request.getName());
                              return fields;
                            }
                          })
                      .setQueryParamsExtractor(
                          new FieldsExtractor<GetOperationRequest, Map<String, List<String>>>() {
                            @Override
                            public Map<String, List<String>> extract(GetOperationRequest request) {
                              Map<String, List<String>> fields = new HashMap<>();
                              ProtoRestSerializer<GetOperationRequest> serializer =
                                  ProtoRestSerializer.create();
                              return fields;
                            }
                          })
                      .setRequestBodyExtractor(
                          new FieldsExtractor<GetOperationRequest, String>() {
                            @Override
                            public String extract(GetOperationRequest request) {
                              return null;
                            }
                          })
                      .build())
              .setResponseParser(
                  ProtoMessageResponseParser.<Operation>newBuilder()
                      .setDefaultInstance(Operation.getDefaultInstance())
                      .build())
              .setOperationSnapshotFactory(
                  new OperationSnapshotFactory<GetOperationRequest, Operation>() {
                    @Override
                    public OperationSnapshot create(
                        GetOperationRequest request, Operation response) {
                      return HttpJsonOperationSnapshot.create(response);
                    }
                  })
              .setPollingRequestFactory(
                  new PollingRequestFactory<GetOperationRequest>() {
                    @Override
                    public GetOperationRequest create(String compoundOperationId) {
                      return GetOperationRequest.newBuilder().setName(compoundOperationId).build();
                    }
                  })
              .build();

  private static final ApiMethodDescriptor<DeleteOperationRequest, Empty>
      deleteOperationMethodDescriptor =
          ApiMethodDescriptor.<DeleteOperationRequest, Empty>newBuilder()
              .setFullMethodName("google.longrunning.Operations/DeleteOperation")
              .setHttpMethod(HttpMethods.DELETE)
              .setRequestFormatter(
                  ProtoMessageRequestFormatter.<DeleteOperationRequest>newBuilder()
                      .setPath(
                          "/v1/{name=operations/**}",
                          new FieldsExtractor<DeleteOperationRequest, Map<String, String>>() {
                            @Override
                            public Map<String, String> extract(DeleteOperationRequest request) {
                              Map<String, String> fields = new HashMap<>();
                              ProtoRestSerializer<DeleteOperationRequest> serializer =
                                  ProtoRestSerializer.create();
                              serializer.putPathParam(fields, "name", request.getName());
                              return fields;
                            }
                          })
                      .setQueryParamsExtractor(
                          new FieldsExtractor<DeleteOperationRequest, Map<String, List<String>>>() {
                            @Override
                            public Map<String, List<String>> extract(
                                DeleteOperationRequest request) {
                              Map<String, List<String>> fields = new HashMap<>();
                              ProtoRestSerializer<DeleteOperationRequest> serializer =
                                  ProtoRestSerializer.create();
                              return fields;
                            }
                          })
                      .setRequestBodyExtractor(
                          new FieldsExtractor<DeleteOperationRequest, String>() {
                            @Override
                            public String extract(DeleteOperationRequest request) {
                              return null;
                            }
                          })
                      .build())
              .setResponseParser(
                  ProtoMessageResponseParser.<Empty>newBuilder()
                      .setDefaultInstance(Empty.getDefaultInstance())
                      .build())
              .build();

  private static final ApiMethodDescriptor<CancelOperationRequest, Empty>
      cancelOperationMethodDescriptor =
          ApiMethodDescriptor.<CancelOperationRequest, Empty>newBuilder()
              .setFullMethodName("google.longrunning.Operations/CancelOperation")
              .setHttpMethod(HttpMethods.POST)
              .setRequestFormatter(
                  ProtoMessageRequestFormatter.<CancelOperationRequest>newBuilder()
                      .setPath(
                          "/v1/{name=operations/**}:cancel",
                          new FieldsExtractor<CancelOperationRequest, Map<String, String>>() {
                            @Override
                            public Map<String, String> extract(CancelOperationRequest request) {
                              Map<String, String> fields = new HashMap<>();
                              ProtoRestSerializer<CancelOperationRequest> serializer =
                                  ProtoRestSerializer.create();
                              serializer.putPathParam(fields, "name", request.getName());
                              return fields;
                            }
                          })
                      .setQueryParamsExtractor(
                          new FieldsExtractor<CancelOperationRequest, Map<String, List<String>>>() {
                            @Override
                            public Map<String, List<String>> extract(
                                CancelOperationRequest request) {
                              Map<String, List<String>> fields = new HashMap<>();
                              ProtoRestSerializer<CancelOperationRequest> serializer =
                                  ProtoRestSerializer.create();
                              return fields;
                            }
                          })
                      .setRequestBodyExtractor(
                          new FieldsExtractor<CancelOperationRequest, String>() {
                            @Override
                            public String extract(CancelOperationRequest request) {
                              return null;
                            }
                          })
                      .build())
              .setResponseParser(
                  ProtoMessageResponseParser.<Empty>newBuilder()
                      .setDefaultInstance(Empty.getDefaultInstance())
                      .build())
              .build();

  private final UnaryCallable<ListOperationsRequest, ListOperationsResponse> listOperationsCallable;
  private final UnaryCallable<ListOperationsRequest, ListOperationsPagedResponse>
      listOperationsPagedCallable;
  private final UnaryCallable<GetOperationRequest, Operation> getOperationCallable;
  private final UnaryCallable<DeleteOperationRequest, Empty> deleteOperationCallable;
  private final UnaryCallable<CancelOperationRequest, Empty> cancelOperationCallable;

  private final LongRunningClient longRunningClient;
  private final BackgroundResource backgroundResources;
  private final HttpJsonStubCallableFactory callableFactory;

  public static final HttpJsonOperationsStub create(OperationsStubSettings settings)
      throws IOException {
    return new HttpJsonOperationsStub(settings, ClientContext.create(settings));
  }

  public static final HttpJsonOperationsStub create(ClientContext clientContext)
      throws IOException {
    return new HttpJsonOperationsStub(OperationsStubSettings.newBuilder().build(), clientContext);
  }

  public static final HttpJsonOperationsStub create(
      ClientContext clientContext, HttpJsonStubCallableFactory callableFactory) throws IOException {
    return new HttpJsonOperationsStub(
        OperationsStubSettings.newBuilder().build(),
        clientContext,
        callableFactory,
        TypeRegistry.getEmptyTypeRegistry());
  }

  public static final HttpJsonOperationsStub create(
      ClientContext clientContext,
      HttpJsonStubCallableFactory callableFactory,
      TypeRegistry typeRegistry)
      throws IOException {
    return new HttpJsonOperationsStub(
        OperationsStubSettings.newBuilder().build(), clientContext, callableFactory, typeRegistry);
  }

  /**
   * Constructs an instance of HttpJsonOperationsStub, using the given settings. This is protected
   * so that it is easy to make a subclass, but otherwise, the static factory methods should be
   * preferred.
   */
  protected HttpJsonOperationsStub(OperationsStubSettings settings, ClientContext clientContext)
      throws IOException {
    this(
        settings,
        clientContext,
        new HttpJsonOperationsCallableFactory(),
        TypeRegistry.getEmptyTypeRegistry());
  }

  /**
   * Constructs an instance of HttpJsonOperationsStub, using the given settings. This is protected
   * so that it is easy to make a subclass, but otherwise, the static factory methods should be
   * preferred.
   */
  protected HttpJsonOperationsStub(
      OperationsStubSettings settings,
      ClientContext clientContext,
      HttpJsonStubCallableFactory callableFactory,
      TypeRegistry typeRegistry)
      throws IOException {
    this.callableFactory = callableFactory;

    HttpJsonCallSettings<ListOperationsRequest, ListOperationsResponse>
        listOperationsTransportSettings =
            HttpJsonCallSettings.<ListOperationsRequest, ListOperationsResponse>newBuilder()
                .setMethodDescriptor(listOperationsMethodDescriptor)
                .setTypeRegistry(typeRegistry)
                .build();
    HttpJsonCallSettings<GetOperationRequest, Operation> getOperationTransportSettings =
        HttpJsonCallSettings.<GetOperationRequest, Operation>newBuilder()
            .setMethodDescriptor(getOperationMethodDescriptor)
            .setTypeRegistry(typeRegistry)
            .build();
    HttpJsonCallSettings<DeleteOperationRequest, Empty> deleteOperationTransportSettings =
        HttpJsonCallSettings.<DeleteOperationRequest, Empty>newBuilder()
            .setMethodDescriptor(deleteOperationMethodDescriptor)
            .setTypeRegistry(typeRegistry)
            .build();
    HttpJsonCallSettings<CancelOperationRequest, Empty> cancelOperationTransportSettings =
        HttpJsonCallSettings.<CancelOperationRequest, Empty>newBuilder()
            .setMethodDescriptor(cancelOperationMethodDescriptor)
            .setTypeRegistry(typeRegistry)
            .build();

    this.listOperationsCallable =
        callableFactory.createUnaryCallable(
            listOperationsTransportSettings, settings.listOperationsSettings(), clientContext);
    this.listOperationsPagedCallable =
        callableFactory.createPagedCallable(
            listOperationsTransportSettings, settings.listOperationsSettings(), clientContext);
    this.getOperationCallable =
        callableFactory.createUnaryCallable(
            getOperationTransportSettings, settings.getOperationSettings(), clientContext);
    this.deleteOperationCallable =
        callableFactory.createUnaryCallable(
            deleteOperationTransportSettings, settings.deleteOperationSettings(), clientContext);
    this.cancelOperationCallable =
        callableFactory.createUnaryCallable(
            cancelOperationTransportSettings, settings.cancelOperationSettings(), clientContext);

    this.longRunningClient =
        new HttpJsonLongRunningClient<GetOperationRequest, Operation>(
            getOperationCallable,
            getOperationMethodDescriptor.getOperationSnapshotFactory(),
            getOperationMethodDescriptor.getPollingRequestFactory());

    this.backgroundResources =
        new BackgroundResourceAggregation(clientContext.getBackgroundResources());
  }

  @InternalApi
  public static List<ApiMethodDescriptor> getMethodDescriptors() {
    List<ApiMethodDescriptor> methodDescriptors = new ArrayList<>();
    methodDescriptors.add(listOperationsMethodDescriptor);
    methodDescriptors.add(getOperationMethodDescriptor);
    methodDescriptors.add(deleteOperationMethodDescriptor);
    methodDescriptors.add(cancelOperationMethodDescriptor);
    return methodDescriptors;
  }

  @Override
  public UnaryCallable<ListOperationsRequest, ListOperationsResponse> listOperationsCallable() {
    return listOperationsCallable;
  }

  @Override
  public UnaryCallable<ListOperationsRequest, ListOperationsPagedResponse>
      listOperationsPagedCallable() {
    return listOperationsPagedCallable;
  }

  @Override
  public UnaryCallable<GetOperationRequest, Operation> getOperationCallable() {
    return getOperationCallable;
  }

  @Override
  public UnaryCallable<DeleteOperationRequest, Empty> deleteOperationCallable() {
    return deleteOperationCallable;
  }

  @Override
  public UnaryCallable<CancelOperationRequest, Empty> cancelOperationCallable() {
    return cancelOperationCallable;
  }

  @Override
  public LongRunningClient longRunningClient() {
    return longRunningClient;
  }

  @Override
  public final void close() {
    shutdown();
  }

  @Override
  public void shutdown() {
    backgroundResources.shutdown();
  }

  @Override
  public boolean isShutdown() {
    return backgroundResources.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return backgroundResources.isTerminated();
  }

  @Override
  public void shutdownNow() {
    backgroundResources.shutdownNow();
  }

  @Override
  public boolean awaitTermination(long duration, TimeUnit unit) throws InterruptedException {
    return backgroundResources.awaitTermination(duration, unit);
  }
}
