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
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.CurrentMillisClock;
import com.google.api.core.SettableApiFuture;
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
import com.google.common.collect.ImmutableList;
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
@Warmup(iterations = 50)
@Measurement(iterations = 5)
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
    impl1 = new Batcher<>(new FakeBatchingDescriptor(), baseCallable, batchingThresholds, executor, /*Duration.ofSeconds(5)*/ Duration.ofDays(1), new BatchExecutor<>(batchingDescriptor,partitionKey), batchingFlowController);
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
    final CountDownLatch latch = new CountDownLatch(messageCount);

    final Semaphore outstandingElements = new Semaphore(maxOutstandingElements);

    PublishRequest prototype = PublishRequest.newBuilder().setTopic(TOPIC).build();

    PublishRequest.Builder requestBuilder = prototype.toBuilder();
    int currentBatchSize = 0;
    List<SettableApiFuture<PublishResponse>> entryFutures =
        Lists.newArrayListWithCapacity(elementsPerBatch);

    for (int i = 0; i < messageCount; i++) {
      SettableApiFuture<PublishResponse> entryFuture = SettableApiFuture.create();
      entryFuture.addListener(
          new Runnable() {
            @Override
            public void run() {
              latch.countDown();
            }
          },
          MoreExecutors.directExecutor());
      entryFutures.add(entryFuture);

      // Fill up the current batch
      requestBuilder.addMessages(
          PubsubMessage.newBuilder()
              .setData(payloads[RANDOM.nextInt(payloads.length)])
              .setMessageId("message-" + i)
              .build());

      currentBatchSize++;

      // Respecting the element count flow control
      outstandingElements.acquire(1);

      // Send the batches when full or if we are about to run out of messages
      if (currentBatchSize == elementsPerBatch || i == messageCount - 1) {
        final List<SettableApiFuture<PublishResponse>> futureSnapshot = entryFutures;
        entryFutures = Lists.newArrayListWithCapacity(elementsPerBatch);

        final int currentBatchSizeSnapshot = currentBatchSize;
        PublishRequest request = requestBuilder.build();
        // Send the RPC
        final ApiFuture<PublishResponse> batchFuture = baseCallable.futureCall(request);

        // Return the tokens back to the flow control
        ApiFutures.addCallback(
            batchFuture,
            new ApiFutureCallback<PublishResponse>() {
              @Override
              public void onFailure(Throwable t) {
                outstandingElements.release(currentBatchSizeSnapshot);
                for (SettableApiFuture<PublishResponse> f : futureSnapshot) {
                  f.setException(t);
                }
              }

              @Override
              public void onSuccess(PublishResponse result) {
                outstandingElements.release(currentBatchSizeSnapshot);
                for (SettableApiFuture<PublishResponse> f : futureSnapshot) {
                  f.set(result);
                }
              }
            });

        // reset for next batch
        requestBuilder = prototype.toBuilder();
        currentBatchSize = 0;
      }
    }

    if (!latch.await(10, TimeUnit.MINUTES)) {
      throw new TimeoutException("Timed out waiting for all batches to finish");
    }
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

    // Since the current implement does not support flushing, all messages must be aligned to the batch boundary
    Preconditions.checkState(
        messageCount % elementsPerBatch == 0,
        String.format(
            "opsPerInvocation (%s) must be a multiple of elementsPerBatch (%d)",
            messageCount, elementsPerBatch));

    final CountDownLatch latch = new CountDownLatch(messageCount);

    for (int i = 0; i < messageCount; i++) {
      PublishRequest request =
          PublishRequest.newBuilder()
              .setTopic(TOPIC)
              .addMessages(
                  PubsubMessage.newBuilder()
                      .setData(payloads[RANDOM.nextInt(payloads.length)])
                      .setMessageId("message-" + i)
                      .build())
              .build();

      ApiFuture<PublishResponse> future = batchingCallable.futureCall(request);
      future.addListener(
          new Runnable() {
            @Override
            public void run() {
              latch.countDown();
            }
          },
          MoreExecutors.directExecutor());
    }

    if (!latch.await(10, TimeUnit.MINUTES)) {
      throw new TimeoutException("Timed out waiting for all elements to finish");
    }
  }

  /**
   * One layer down into the gax batching infrastructure: skips the {@link
   * com.google.api.gax.rpc.BatchingCallable} and the {@link PartitionKey} routing.
   */
  @Benchmark
  public void pushingBatcherBenchmark(BenchmarkParams benchmarkParams) throws Exception {
    int messageCount = benchmarkParams.getOpsPerInvocation();
    final CountDownLatch latch = new CountDownLatch(messageCount);

    FakeBatchingDescriptor descriptor = new FakeBatchingDescriptor();

    for (int i = 0; i < messageCount; i++) {
      PublishRequest request =
          PublishRequest.newBuilder()
              .setTopic(TOPIC)
              .addMessages(
                  PubsubMessage.newBuilder()
                      .setData(payloads[RANDOM.nextInt(payloads.length)])
                      .setMessageId("message-" + i)
                      .build())
              .build();

      BatchedFuture<PublishResponse> future = new BatchedFuture<>();
      pushingBatcher.add(new Batch<>(descriptor, request, baseCallable, future));
      future.addListener(
          new Runnable() {
            @Override
            public void run() {
              latch.countDown();
            }
          },
          MoreExecutors.directExecutor());
    }
    pushingBatcher.pushCurrentBatch();

    if (!latch.await(10, TimeUnit.MINUTES)) {
      throw new TimeoutException("Timed out waiting for all elements to finish");
    }
  }

  /**
   * 2 layers deep into the gax batching infrastructure that tries to avoid re-wrapping the request
   * protos by pre-sizing the batches.
   */
  @Benchmark
  public void pushingBatcher2Benchmark(BenchmarkParams benchmarkParams) throws Exception {
    int messageCount = benchmarkParams.getOpsPerInvocation();
    final CountDownLatch latch =
        new CountDownLatch((int) Math.ceil(messageCount / elementsPerBatch));

    FakeBatchingDescriptor descriptor = new FakeBatchingDescriptor();

    PublishRequest prototype = PublishRequest.newBuilder().setTopic(TOPIC).build();

    PublishRequest.Builder builder = prototype.toBuilder();
    int currentBatchSize = 0;

    for (int i = 0; i < messageCount; i++) {
      builder
          .addMessages(
              PubsubMessage.newBuilder()
                  .setData(payloads[RANDOM.nextInt(payloads.length)])
                  .setMessageId("message-" + i)
                  .build())
          .build();
      currentBatchSize++;

      if (currentBatchSize == elementsPerBatch || i == messageCount - 1) {
        BatchedFuture<PublishResponse> future = new BatchedFuture<>();
        pushingBatcher.add(new Batch<>(descriptor, builder.build(), baseCallable, future));
        future.addListener(
            new Runnable() {
              @Override
              public void run() {
                latch.countDown();
              }
            },
            MoreExecutors.directExecutor());
        builder = prototype.toBuilder();
        currentBatchSize = 0;
      }
    }
    pushingBatcher.pushCurrentBatch();

    if (!latch.await(10, TimeUnit.MINUTES)) {
      throw new TimeoutException("Timed out waiting for all elements to finish");
    }
  }

  // Skip batch merging
  @Benchmark
  public void noBatchMerging(BenchmarkParams benchmarkParams) throws Exception {
    int messageCount = benchmarkParams.getOpsPerInvocation();
    final CountDownLatch latch = new CountDownLatch(messageCount);

    for (int i = 0; i < messageCount; i++) {
      PublishRequest request =
          PublishRequest.newBuilder()
              .setTopic(TOPIC)
              .addMessages(
                  PubsubMessage.newBuilder()
                      .setData(payloads[RANDOM.nextInt(payloads.length)])
                      .setMessageId("message-" + i)
                      .build())
              .build();

      ApiFuture<PublishResponse> future = impl1.add(request);
      future.addListener(
          new Runnable() {
            @Override
            public void run() {
              latch.countDown();
            }
          },
          MoreExecutors.directExecutor());
    }
    pushingBatcher.pushCurrentBatch();

    if (!latch.await(10, TimeUnit.MINUTES)) {
      throw new TimeoutException("Timed out waiting for all elements to finish");
    }
  }

  // Helpers -------
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
