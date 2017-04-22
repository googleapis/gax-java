/*
 * Copyright 2017, Google Inc. All rights reserved.
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

import com.google.api.gax.core.RetrySettings;
import com.google.api.gax.core.RpcFuture;
import com.google.caliper.Benchmark;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.grpc.Status.Code;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.joda.time.Duration;

public class CallableBenchmark {
  private static final TopicName TOPIC_NAME_RESOURCE = TopicName.create("my-project", "my-topic");
  private static final String TOPIC_NAME_STRING = TOPIC_NAME_RESOURCE.toString();
  private static final FutureCallable<PublishRequest, Integer> RETURN_ONE_CALLABLE =
      new FutureCallable<PublishRequest, Integer>() {
        @Override
        public RpcFuture<Integer> futureCall(PublishRequest request, CallContext context) {
          return new ListenableFutureDelegate<Integer>(Futures.immediateFuture(1));
        }
      };
  private static final RetrySettings RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setTotalTimeout(Duration.standardSeconds(1))
          .setInitialRetryDelay(Duration.standardSeconds(1))
          .setRetryDelayMultiplier(1.2)
          .setMaxRetryDelay(Duration.standardSeconds(1))
          .setInitialRpcTimeout(Duration.standardSeconds(1))
          .setRpcTimeoutMultiplier(1.2)
          .setMaxRpcTimeout(Duration.standardSeconds(1))
          .build();
  private static final UnaryCallable<PublishRequest, Integer> ONE_UNARY_CALLABLE =
      UnaryCallable.create(RETURN_ONE_CALLABLE)
          .retryableOn(ImmutableSet.of(Code.UNAVAILABLE))
          .retrying(RETRY_SETTINGS, null);
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

  private int serialize(int reps, int numMessages) throws Exception {
    int totalSize = 0;
    for (int i = 0; i < reps; i++) {
      PublishRequest request =
          PublishRequest.newBuilder()
              .setTopic(TOPIC_NAME_STRING)
              .addAllMessages(MESSAGES.subList(0, numMessages))
              .build();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      request.writeTo(out);
      totalSize += out.size();
    }
    return totalSize;
  }

  @Benchmark
  int timeSerialize0(int reps) throws Exception {
    return serialize(reps, 0);
  }

  @Benchmark
  int timeSerialize10(int reps) throws Exception {
    return serialize(reps, 10);
  }

  @Benchmark
  int timeSerialize100(int reps) throws Exception {
    return serialize(reps, 100);
  }

  @Benchmark
  int timeRequest(int reps) {
    int total = 0;
    for (int i = 0; i < reps; i++) {
      total +=
          ONE_UNARY_CALLABLE.call(
              PublishRequest.newBuilder().setTopicWithTopicName(TOPIC_NAME_RESOURCE).build());
    }
    return total;
  }
}
