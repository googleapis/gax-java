/*
 * Copyright 2018, Google LLC All rights reserved.
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

import com.google.api.core.ApiFuture;
import com.google.api.core.CurrentMillisClock;
import com.google.api.gax.batching.BatchingFlowController;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.BatchingThreshold;
import com.google.api.gax.batching.ElementCounter;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.batching.FlowController.LimitExceededBehavior;
import com.google.api.gax.batching.NumericThreshold;
import com.google.api.gax.batching.PartitionKey;
import com.google.api.gax.batching.RequestBuilder;
import com.google.api.gax.batching.ThresholdBatcher;
import com.google.api.gax.grpc.batching.Batcher;
import com.google.api.gax.rpc.Batch;
import com.google.api.gax.rpc.BatchExecutor;
import com.google.api.gax.rpc.BatchedFuture;
import com.google.api.gax.rpc.BatchedRequestIssuer;
import com.google.api.gax.rpc.BatcherFactory;
import com.google.api.gax.rpc.BatchingCallSettings;
import com.google.api.gax.rpc.BatchingDescriptor;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PublishResponse.Builder;
import com.google.pubsub.v1.PublisherGrpc;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
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
 * Exploratory benchmarks for batching.
 *
 * <p>This benchmark tries to measure the overhead of gax's batching infrastructure. It uses a fake
 * pubsub server to sink the requests. This is done to take netty context switchs into account. It
 * tries to pin done performance sensitive portions.
 *
 * <p>Each iteration will send {@code OperationsPerInvocation} entries, split by {@code
 * elementsPerBatch}, with at most {@code maxOutstandingElements} elements unsent. When the {@code
 * maxOutstandingElements} is reached all benchmarks will block.
 */
@Fork(value = 1)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 10 )
@Measurement(iterations = 10)
@State(Scope.Benchmark)
@OperationsPerInvocation(10_000)
@OutputTimeUnit(TimeUnit.SECONDS)
public class BatchingBenchmark {
  private static final Logger LOG = Logger.getLogger(BatchingBenchmark.class.getName());
  private static final String TOPIC = "projects/fake-project/topics/fake-topic";
  private static final Random RANDOM = new Random(123);

  @SuppressWarnings("WeakerAccess")
  @Param("1000")
  int maxOutstandingElements;

  @SuppressWarnings("WeakerAccess")
  @Param("100")
  int elementsPerBatch;

  @SuppressWarnings("WeakerAccess")
  @Param("1024")
  int messageSize;

  private ScheduledExecutorService executor;
  private Server fakeServer;
  private ManagedChannel grpcChannel;
  private ClientContext clientContext;
  private ByteString[] payloads;

  private UnaryCallable<PublishRequest, PublishResponse> baseCallable;
  private UnaryCallable<PublishRequest, PublishResponse> batchingCallable;
  private ThresholdBatcher<Batch<PublishRequest, PublishResponse>> pushingBatcher;
  private Batcher<PublishRequest, PublishResponse> impl1;

  @Setup
  public void setup(BenchmarkParams benchmarkParams, Blackhole blackhole) throws IOException {
    Preconditions.checkState(elementsPerBatch <= maxOutstandingElements);
    Preconditions.checkState(maxOutstandingElements % elementsPerBatch == 0);
    Preconditions.checkState(benchmarkParams.getOpsPerInvocation() % elementsPerBatch == 0);

    byte[] buffer = new byte[messageSize];
    payloads = new ByteString[100];
    for (int i = 0; i < payloads.length; i++) {
      RANDOM.nextBytes(buffer);
      payloads[i] = ByteString.copyFrom(buffer);
    }

    executor = Executors.newScheduledThreadPool(4);

    final int availablePort;
    try (ServerSocket ss = new ServerSocket(0)) {
      availablePort = ss.getLocalPort();
    }
    fakeServer =
        ServerBuilder.forPort(availablePort).addService(new FakePubSub(blackhole)).build().start();

    grpcChannel =
        ManagedChannelBuilder.forAddress("localhost", availablePort).usePlaintext(true).build();

    clientContext =
        ClientContext.newBuilder()
            .setExecutor(executor)
            .setClock(CurrentMillisClock.getDefaultClock())
            .setDefaultCallContext(
                GrpcCallContext.of(
                    grpcChannel, CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.HOURS)))
            .setTransportChannel(GrpcTransportChannel.create(grpcChannel))
            .build();

    GrpcCallSettings<PublishRequest, PublishResponse> grpcSettings =
        GrpcCallSettings.<PublishRequest, PublishResponse>newBuilder()
            .setMethodDescriptor(PublisherGrpc.METHOD_PUBLISH)
            .build();

    UnaryCallSettings<PublishRequest, PublishResponse> callSettings =
        UnaryCallSettings.<PublishRequest, PublishResponse>newUnaryCallSettingsBuilder()
            .setSimpleTimeoutNoRetries(Duration.ofMinutes(1))
            .build();

    baseCallable =
        GrpcCallableFactory.createUnaryCallable(grpcSettings, callSettings, clientContext);

    BatchingCallSettings.Builder<PublishRequest, PublishResponse> batchingCallSettings =
        BatchingCallSettings.newBuilder(new FakeBatchingDescriptor())
            .setBatchingSettings(
                BatchingSettings.newBuilder()
                    .setIsEnabled(true)
                    .setFlowControlSettings(
                        FlowControlSettings.newBuilder()
                            .setLimitExceededBehavior(LimitExceededBehavior.Block)
                            .setMaxOutstandingElementCount((long) maxOutstandingElements)
                            .build())
                    // Not actually used because we generate messages faster
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .setElementCountThreshold((long) elementsPerBatch)
                    .build());
    batchingCallSettings.setSimpleTimeoutNoRetries(Duration.ofSeconds(10));

    batchingCallable =
        GrpcCallableFactory.createBatchingCallable(
            grpcSettings, batchingCallSettings.build(), clientContext);

    BatcherFactory<PublishRequest, PublishResponse> batcherFactory =
        new BatcherFactory<>(
            new FakeBatchingDescriptor(),
            BatchingSettings.newBuilder()
                .setIsEnabled(true)
                .setFlowControlSettings(
                    FlowControlSettings.newBuilder()
                        .setLimitExceededBehavior(LimitExceededBehavior.Block)
                        .setMaxOutstandingElementCount((long) maxOutstandingElements)
                        .build())
                // Not actually used because we generate messages faster
                .setDelayThreshold(Duration.ofSeconds(5))
                .setElementCountThreshold((long) elementsPerBatch)
                .build(),
            executor,
            new FlowController(
                FlowControlSettings.newBuilder()
                    .setLimitExceededBehavior(LimitExceededBehavior.Block)
                    .setMaxOutstandingElementCount((long) maxOutstandingElements)
                    .build()));
    pushingBatcher = batcherFactory.getPushingBatcher(new PartitionKey(TOPIC));

    ArrayList<BatchingThreshold<PublishRequest>> batchingThresholds = Lists.newArrayList();
    batchingThresholds.add(
        new NumericThreshold<>(elementsPerBatch,
            new ElementCounter<PublishRequest>() {
              @Override
              public long count(PublishRequest element) {
                return element.getMessagesCount();
              }
            })
    );

    FakeBatchingDescriptor batchingDescriptor = new FakeBatchingDescriptor();
    PartitionKey partitionKey = new PartitionKey(TOPIC);
    FlowController flowController = new FlowController(
        FlowControlSettings.newBuilder()
            .setLimitExceededBehavior(LimitExceededBehavior.Block)
            .setMaxOutstandingElementCount((long) maxOutstandingElements)
            .build()
    );
    BatchingFlowController<PublishRequest> batchingFlowController = new BatchingFlowController<>(
        flowController,
        new ElementCounter<PublishRequest>() {
          @Override
          public long count(PublishRequest element) {
            return element.getMessagesCount();
          }
        },
        new ElementCounter<PublishRequest>() {
          @Override
          public long count(PublishRequest element) {
            return element.getSerializedSize();
          }
        }
    );
    impl1 = Batcher.<PublishRequest, PublishResponse>newBuilder()
      .setDescriptor(new FakeBatchingDescriptor())
        .setInnerCallable(baseCallable)
        .setThresholds(batchingThresholds)
        .setExecutor(executor)
        .setMaxDelay(Duration.ofSeconds(5))
        .setReceiver(new BatchExecutor<>(batchingDescriptor,partitionKey))
        .setFlowController(batchingFlowController)
        .build();
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

  /** Simple, direct implementation that should represent minimal overhead */
  @Benchmark
  public void manualBaseline(BenchmarkParams benchmarkParams) throws Exception {
    int messageCount = benchmarkParams.getOpsPerInvocation();
    int batchCount = messageCount / elementsPerBatch;

    final Semaphore outstandingElements = new Semaphore(maxOutstandingElements);
    FinishLine finishLine = new FinishLine(batchCount) {
      @Override
      public void run() {
        outstandingElements.release(elementsPerBatch);
        super.run();
      }
    };

    for (int i = 0; i < batchCount; i++) {
      outstandingElements.acquire(elementsPerBatch);
      PublishRequest request = buildRequest(elementsPerBatch);

      final ApiFuture<PublishResponse> batchFuture = baseCallable.futureCall(request);
      batchFuture.addListener(finishLine, MoreExecutors.directExecutor());
    }

    finishLine.waitForArrival();
  }

  /**
   * The full gax batching stack.
   *
   * <p>Note: since gax doesn't support flushing, {@link OperationsPerInvocation} must be a multiple
   * of {@link #elementsPerBatch}.
   */
  @Benchmark
  public void batchingCallableBenchmark(BenchmarkParams benchmarkParams) throws Exception {
    int messageCount = benchmarkParams.getOpsPerInvocation();
    FinishLine finishLine = new FinishLine(messageCount);

    for (int i = 0; i < messageCount; i++) {
      PublishRequest request = buildRequest(1);

      ApiFuture<PublishResponse> future = batchingCallable.futureCall(request);
      future.addListener(finishLine, MoreExecutors.directExecutor());
    }

    finishLine.waitForArrival();
  }

  /**
   * One layer down into the gax batching infrastructure: skips the {@link
   * com.google.api.gax.rpc.BatchingCallable} and the {@link PartitionKey} routing.
   */
  @Benchmark
  public void pushingBatcherBenchmark(BenchmarkParams benchmarkParams) throws Exception {
    int messageCount = benchmarkParams.getOpsPerInvocation();
    FinishLine finishLine = new FinishLine(messageCount);

    FakeBatchingDescriptor descriptor = new FakeBatchingDescriptor();

    for (int i = 0; i < messageCount; i++) {
      PublishRequest request = buildRequest(1);

      BatchedFuture<PublishResponse> future = new BatchedFuture<>();
      pushingBatcher.add(new Batch<>(descriptor, request, baseCallable, future));
      future.addListener(finishLine, MoreExecutors.directExecutor());
    }

    pushingBatcher.pushCurrentBatch();
    finishLine.waitForArrival();
  }

  /**
   * 2 layers deep into the gax batching infrastructure that tries to avoid re-wrapping the request
   * protos by pre-sizing the batches.
   */
  @Benchmark
  public void pushingBatcher2Benchmark(BenchmarkParams benchmarkParams) throws Exception {
    int messageCount = benchmarkParams.getOpsPerInvocation();
    int batchCount = messageCount / elementsPerBatch;
    FinishLine finishLine = new FinishLine(batchCount);

    FakeBatchingDescriptor descriptor = new FakeBatchingDescriptor();

    for (int i = 0; i < batchCount; i++) {
      PublishRequest request = buildRequest(elementsPerBatch);
      BatchedFuture<PublishResponse> future = new BatchedFuture<>();
      pushingBatcher.add(new Batch<>(descriptor, request, baseCallable, future));
      future.addListener(finishLine, MoreExecutors.directExecutor());
    }

    pushingBatcher.pushCurrentBatch();
    finishLine.waitForArrival();
  }

  // Skip batch merging
  @Benchmark
  public void noBatchMerging(BenchmarkParams benchmarkParams) throws Exception {
    int messageCount = benchmarkParams.getOpsPerInvocation();
    FinishLine finishLine = new FinishLine(messageCount);

    for (int i = 0; i < messageCount; i++) {
      PublishRequest request = buildRequest(1);
      ApiFuture<PublishResponse> future = impl1.add(request);
      future.addListener(finishLine, MoreExecutors.directExecutor());
    }

    pushingBatcher.pushCurrentBatch();
    finishLine.waitForArrival();
  }

  // Helpers -------
  private PublishRequest buildRequest(int entryCount) {
    PublishRequest.Builder builder = PublishRequest.newBuilder()
            .setTopic(TOPIC);

    for (int i = 0; i < entryCount; i++) {
      builder.addMessages(
          PubsubMessage.newBuilder()
              .setData(payloads[RANDOM.nextInt(payloads.length)])
              .setMessageId("message-" + i)
              .build());
    }

    return builder.build();
  }

  static class FinishLine implements Runnable {
    private final CountDownLatch countDownLatch;

    FinishLine(int eventCount) {
      countDownLatch = new CountDownLatch(eventCount);
    }

    @Override
    public void run() {
      countDownLatch.countDown();
    }

    void waitForArrival() throws TimeoutException, InterruptedException {
      if (!countDownLatch.await(10, TimeUnit.MINUTES)) {
        throw new TimeoutException("Timed out waiting for all elements to finish");
      }
    }
  }

  static class FakePubSub extends com.google.pubsub.v1.PublisherGrpc.PublisherImplBase {
    private final Blackhole blackhole;

    FakePubSub(Blackhole blackhole) {
      this.blackhole = blackhole;
    }

    @Override
    public void publish(PublishRequest request, StreamObserver<PublishResponse> responseObserver) {
      blackhole.consume(request);
      Builder responseBuilder = PublishResponse.newBuilder();
      for (PubsubMessage msg : request.getMessagesList()) {
        responseBuilder.addMessageIds(msg.getMessageId());
      }

      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }
  }

  static class FakeBatchingDescriptor
      implements BatchingDescriptor<PublishRequest, PublishResponse> {
    @Override
    public PartitionKey getBatchPartitionKey(PublishRequest request) {
      return new PartitionKey(request.getTopic());
    }

    @Override
    public RequestBuilder<PublishRequest> getRequestBuilder() {
      return new RequestBuilder<PublishRequest>() {
        private PublishRequest.Builder builder;

        @Override
        public void appendRequest(PublishRequest request) {
          if (builder == null) {
            builder = request.toBuilder();
          } else {
            builder.addAllMessages(request.getMessagesList());
          }
        }

        @Override
        public PublishRequest build() {
          return builder.build();
        }
      };
    }

    @Override
    public void splitResponse(
        PublishResponse batchResponse,
        Collection<? extends BatchedRequestIssuer<PublishResponse>> batch) {
      int batchMessageIndex = 0;
      for (BatchedRequestIssuer<PublishResponse> responder : batch) {
        List<String> subresponseElements = new ArrayList<>();
        long subresponseCount = responder.getMessageCount();
        for (int i = 0; i < subresponseCount; i++) {
          subresponseElements.add(batchResponse.getMessageIds(batchMessageIndex));
          batchMessageIndex += 1;
        }
        PublishResponse response =
            PublishResponse.newBuilder().addAllMessageIds(subresponseElements).build();
        responder.setResponse(response);
      }
    }

    @Override
    public void splitException(
        Throwable throwable, Collection<? extends BatchedRequestIssuer<PublishResponse>> batch) {
      for (BatchedRequestIssuer<PublishResponse> responder : batch) {
        responder.setException(throwable);
      }
    }

    @Override
    public long countElements(PublishRequest request) {
      return request.getMessagesCount();
    }

    @Override
    public long countBytes(PublishRequest request) {
      return request.getSerializedSize();
    }
  }
}
