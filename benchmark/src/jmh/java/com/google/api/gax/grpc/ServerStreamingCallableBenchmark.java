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

import com.google.api.core.CurrentMillisClock;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.RequestParamsExtractor;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStream;
import com.google.api.gax.rpc.ServerStreamingCallSettings;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.StreamController;
import com.google.bigtable.v2.BigtableGrpc;
import com.google.bigtable.v2.ReadRowsRequest;
import com.google.bigtable.v2.ReadRowsResponse;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import org.threeten.bp.Duration;

/**
 * This benchmark tests the performance of gax's {@link ServerStreamingCallable}s relative to the
 * raw GRPC interfaces. It uses {@link FakeBigtableService} to generate the stream. The number of
 * responses per stream is controlled by {@code OperationsPerInvocation}.
 */
@Fork(value = 1)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
@OperationsPerInvocation(100_000)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ServerStreamingCallableBenchmark {
  private static final Logger LOG =
      Logger.getLogger(ServerStreamingCallableBenchmark.class.getName());

  @State(Scope.Benchmark)
  public static class AsyncSettings {
    @Param({"true", "false"})
    private boolean autoFlowControl = true;
  }

  private ScheduledExecutorService executor;
  private Server fakeServer;
  private ManagedChannel grpcChannel;
  private ClientContext clientContext;
  private ReadRowsRequest request;

  private ServerStreamingCallable<ReadRowsRequest, ReadRowsResponse> directCallable;
  private ServerStreamingCallable<ReadRowsRequest, ReadRowsResponse> baseCallable;
  private BigtableGrpc.BigtableStub stub;

  @Setup
  public void setup(BenchmarkParams benchmarkParams) throws IOException {
    executor = Executors.newScheduledThreadPool(4);

    final int availablePort;
    try (ServerSocket ss = new ServerSocket(0)) {
      availablePort = ss.getLocalPort();
    }
    fakeServer =
        ServerBuilder.forPort(availablePort).addService(new FakeBigtableService()).build().start();

    grpcChannel =
        ManagedChannelBuilder.forAddress("localhost", availablePort).usePlaintext().build();

    clientContext =
        ClientContext.newBuilder()
            .setExecutor(executor)
            .setClock(CurrentMillisClock.getDefaultClock())
            .setDefaultCallContext(
                GrpcCallContext.of(
                    grpcChannel, CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.HOURS)))
            .setTransportChannel(GrpcTransportChannel.create(grpcChannel))
            .build();

    request =
        ReadRowsRequest.newBuilder().setRowsLimit(benchmarkParams.getOpsPerInvocation()).build();

    // Stub
    stub = BigtableGrpc.newStub(grpcChannel);

    // Direct Callable
    directCallable = new GrpcDirectServerStreamingCallable<>(BigtableGrpc.METHOD_READ_ROWS);

    // Base Callable (direct + params extractor + exceptions + retries)
    GrpcCallSettings<ReadRowsRequest, ReadRowsResponse> grpcCallSettings =
        GrpcCallSettings.<ReadRowsRequest, ReadRowsResponse>newBuilder()
            .setMethodDescriptor(BigtableGrpc.METHOD_READ_ROWS)
            .setParamsExtractor(new FakeRequestParamsExtractor())
            .build();

    ServerStreamingCallSettings<ReadRowsRequest, ReadRowsResponse> callSettings =
        ServerStreamingCallSettings.<ReadRowsRequest, ReadRowsResponse>newBuilder()
            .setRetryableCodes(Code.UNAVAILABLE)
            .setRetrySettings(
                RetrySettings.newBuilder()
                    .setMaxAttempts(10)
                    .setJittered(true)
                    .setTotalTimeout(Duration.ofHours(1))
                    .setInitialRetryDelay(Duration.ofMillis(5))
                    .setRetryDelayMultiplier(2)
                    .setMaxRetryDelay(Duration.ofMinutes(1))
                    .setInitialRpcTimeout(Duration.ofHours(1))
                    .setRpcTimeoutMultiplier(1)
                    .setMaxRpcTimeout(Duration.ofHours(1))
                    .build())
            .setIdleTimeout(Duration.ofSeconds(1))
            .setTimeoutCheckInterval(Duration.ofSeconds(1))
            .build();

    baseCallable =
        GrpcCallableFactory.createServerStreamingCallable(
            grpcCallSettings, callSettings, clientContext);
  }

  @TearDown
  public void teardown() {
    try {
      grpcChannel.shutdown();
      if (!grpcChannel.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new TimeoutException();
      }
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Failed to close the grpc channel", e);
    }

    try {
      fakeServer.shutdown();
      if (!fakeServer.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new TimeoutException();
      }
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Failed to shutdown the fake server", e);
    }

    try {
      executor.shutdown();
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new TimeoutException();
      }
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Failed to shutdown the the executor", e);
    }
  }

  // ClientCall.Listener baseline
  @Benchmark
  public void asyncGrpcListener(AsyncSettings asyncSettings, Blackhole blackhole) throws Exception {
    ClientCall<ReadRowsRequest, ReadRowsResponse> clientCall =
        grpcChannel.newCall(BigtableGrpc.METHOD_READ_ROWS, CallOptions.DEFAULT);

    GrpcClientCallListener listener =
        new GrpcClientCallListener(clientCall, asyncSettings.autoFlowControl, blackhole);
    clientCall.start(listener, new Metadata());
    clientCall.sendMessage(request);
    clientCall.halfClose();

    if (asyncSettings.autoFlowControl) {
      clientCall.request(Integer.MAX_VALUE);
    } else {
      clientCall.request(1);
    }

    listener.awaitCompletion();
  }

  // grpc-stub baseline
  @Benchmark
  public void asyncGrpcObserver(AsyncSettings asyncSettings, Blackhole blackhole) throws Exception {
    GrpcStreamingObserver observer =
        new GrpcStreamingObserver(asyncSettings.autoFlowControl, blackhole);
    stub.readRows(request, observer);
    observer.awaitCompletion();
  }

  @Benchmark
  public void syncGrpcIterator(Blackhole blackhole) {
    Iterator<ReadRowsResponse> iterator =
        ClientCalls.blockingServerStreamingCall(
            grpcChannel, BigtableGrpc.METHOD_READ_ROWS, CallOptions.DEFAULT, request);

    while (iterator.hasNext()) {
      ReadRowsResponse response = iterator.next();
      blackhole.consume(response);
    }
  }

  // DirectCallable
  @Benchmark
  public void asyncDirectServerStreamingCallable(AsyncSettings asyncSettings, Blackhole blackhole)
      throws Exception {
    GaxResponseObserver responseObserver =
        new GaxResponseObserver(asyncSettings.autoFlowControl, blackhole);
    directCallable.call(request, responseObserver, clientContext.getDefaultCallContext());
    responseObserver.awaitCompletion();
  }

  @Benchmark
  public void syncDirectServerStreamingCallable(Blackhole blackhole) {
    ServerStream<ReadRowsResponse> results =
        directCallable.call(request, clientContext.getDefaultCallContext());
    for (ReadRowsResponse result : results) {
      blackhole.consume(result);
    }
  }

  // BaseCallable
  @Benchmark
  public void asyncBaseServerStreamingCallable(AsyncSettings asyncSettings, Blackhole blackhole)
      throws Exception {
    GaxResponseObserver responseObserver =
        new GaxResponseObserver(asyncSettings.autoFlowControl, blackhole);
    baseCallable.call(request, responseObserver);
    responseObserver.awaitCompletion();
  }

  @Benchmark
  public void syncBaseServerStreamingCallable(Blackhole blackhole) {
    ServerStream<ReadRowsResponse> results = baseCallable.call(request);
    for (ReadRowsResponse result : results) {
      blackhole.consume(result);
    }
  }

  // Helpers
  private static class FakeRequestParamsExtractor
      implements RequestParamsExtractor<ReadRowsRequest> {
    @Override
    public Map<String, String> extract(ReadRowsRequest request) {
      return ImmutableMap.of("table_name", request.getTableName());
    }
  }

  static class GrpcClientCallListener extends ClientCall.Listener<ReadRowsResponse> {
    private final SettableFuture<Void> future = SettableFuture.create();
    private final ClientCall<ReadRowsRequest, ReadRowsResponse> clientCall;
    private final boolean autoFlowControl;
    private final Blackhole blackhole;

    GrpcClientCallListener(
        ClientCall<ReadRowsRequest, ReadRowsResponse> clientCall,
        boolean autoFlowControl,
        Blackhole blackhole) {
      this.clientCall = clientCall;
      this.autoFlowControl = autoFlowControl;
      this.blackhole = blackhole;
    }

    void awaitCompletion() throws InterruptedException, ExecutionException, TimeoutException {
      future.get(1, TimeUnit.MINUTES);
    }

    @Override
    public void onMessage(ReadRowsResponse message) {
      blackhole.consume(message);
      if (!autoFlowControl) {
        clientCall.request(1);
      }
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      if (status.isOk()) {
        future.set(null);
      } else {
        future.setException(status.asRuntimeException(trailers));
      }
    }
  }

  static class GrpcStreamingObserver
      implements ClientResponseObserver<ReadRowsRequest, ReadRowsResponse> {
    private final SettableFuture<Void> future = SettableFuture.create();
    private final boolean autoFlowControl;
    private final Blackhole blackhole;
    private ClientCallStreamObserver inner;

    public GrpcStreamingObserver(boolean autoFlowControl, Blackhole blackhole) {
      this.autoFlowControl = autoFlowControl;
      this.blackhole = blackhole;
    }

    void awaitCompletion() throws InterruptedException, ExecutionException, TimeoutException {
      future.get(1, TimeUnit.HOURS);
    }

    @Override
    public void beforeStart(ClientCallStreamObserver observer) {
      if (!autoFlowControl) {
        observer.disableAutoInboundFlowControl();
      }
      this.inner = observer;
    }

    @Override
    public void onNext(ReadRowsResponse msg) {
      blackhole.consume(msg);
      if (!autoFlowControl) {
        inner.request(1);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      future.setException(throwable);
    }

    @Override
    public void onCompleted() {
      future.set(null);
    }
  }

  static class GaxResponseObserver implements ResponseObserver<ReadRowsResponse> {
    private final SettableFuture<Void> future = SettableFuture.create();
    private final boolean autoFlowControl;
    private final Blackhole blackhole;
    private StreamController controller;

    GaxResponseObserver(boolean autoFlowControl, Blackhole blackhole) {
      this.autoFlowControl = autoFlowControl;
      this.blackhole = blackhole;
    }

    void awaitCompletion() throws InterruptedException, ExecutionException, TimeoutException {
      future.get(1, TimeUnit.HOURS);
    }

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;
      if (!autoFlowControl) {
        controller.disableAutoInboundFlowControl();
        controller.request(1);
      }
    }

    @Override
    public void onResponse(ReadRowsResponse response) {
      blackhole.consume(response);
      if (!autoFlowControl) {
        controller.request(1);
      }
    }

    @Override
    public void onError(Throwable t) {
      future.setException(t);
    }

    @Override
    public void onComplete() {
      future.set(null);
    }
  }
}
