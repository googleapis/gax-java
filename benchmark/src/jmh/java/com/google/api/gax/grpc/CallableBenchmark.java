/*
 * Copyright 2017 Google LLC
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
import com.google.api.core.ApiFutures;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.Callables;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.threeten.bp.Duration;

@Fork(value = 1)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 10)
@Measurement(iterations = 20)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CallableBenchmark {
  private static final String TOPIC_NAME_STRING = TopicName.format("my-project", "my-topic");
  private static final UnaryCallable<PublishRequest, Integer> RETURN_ONE_CALLABLE =
      new UnaryCallable<PublishRequest, Integer>() {
        @Override
        public ApiFuture<Integer> futureCall(PublishRequest request, ApiCallContext context) {
          return ApiFutures.immediateFuture(1);
        }
      };
  private static final RetrySettings RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setTotalTimeout(Duration.ofSeconds(1))
          .setInitialRetryDelay(Duration.ofSeconds(1))
          .setRetryDelayMultiplier(1.2)
          .setMaxRetryDelay(Duration.ofSeconds(1))
          .setInitialRpcTimeout(Duration.ofSeconds(1))
          .setRpcTimeoutMultiplier(1.2)
          .setMaxRpcTimeout(Duration.ofSeconds(1))
          .build();
  private static final UnaryCallSettings<PublishRequest, Integer> callSettings =
      UnaryCallSettings.<PublishRequest, Integer>newUnaryCallSettingsBuilder()
          .setRetrySettings(RETRY_SETTINGS)
          .setRetryableCodes(StatusCode.Code.UNAVAILABLE)
          .build();
  private static final UnaryCallable<PublishRequest, Integer> ONE_UNARY_CALLABLE =
      Callables.retrying(
          RETURN_ONE_CALLABLE,
          callSettings,
          ClientContext.newBuilder()
              .setDefaultCallContext(GrpcCallContext.createDefault())
              .build());
  private static final List<PubsubMessage> MESSAGES = createMessages();

  private static final int MESSAGES_NUM = 100;

  private static List<PubsubMessage> createMessages() {
    ImmutableList.Builder<PubsubMessage> messages = ImmutableList.builder();
    for (int i = 0; i < MESSAGES_NUM; i++) {
      messages.add(
          PubsubMessage.newBuilder()
              .setData(ByteString.copyFromUtf8(String.format("test-message-%d", i)))
              .build());
    }
    return messages.build();
  }

  private void serialize(int numMessages, Blackhole blackhole) throws Exception {
    PublishRequest request =
        PublishRequest.newBuilder()
            .setTopic(TOPIC_NAME_STRING)
            .addAllMessages(MESSAGES.subList(0, numMessages))
            .build();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.writeTo(out);
    blackhole.consume(out.size());
  }

  @State(Scope.Benchmark)
  public static class SerializeParams {
    @Param({"1", "10", "100"})
    public int numMessages;
  }

  @Benchmark
  public void timeSerialize(SerializeParams serializeParams, Blackhole blackhole) throws Exception {
    serialize(serializeParams.numMessages, blackhole);
  }

  @Benchmark
  public void timeRequest(Blackhole blackhole) {
    Integer result =
        ONE_UNARY_CALLABLE.call(PublishRequest.newBuilder().setTopic(TOPIC_NAME_STRING).build());

    blackhole.consume(result);
  }
}
