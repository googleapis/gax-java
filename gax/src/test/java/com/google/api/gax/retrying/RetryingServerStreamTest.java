/*
 * Copyright 2017, Google LLC All rights reserved.
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
package com.google.api.gax.retrying;

import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.core.RecordingScheduler;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.StreamController;
import com.google.api.gax.rpc.testing.FakeApiException;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class RetryingServerStreamTest {

  private FakeApiClock clock;
  private RecordingScheduler executor;
  private AccumulatingCallable innerCallable;
  private TimedRetryAlgorithm retryAlgorithm;
  private MyStreamTracker streamTracker;
  private ApiCallContext context;
  private MyObserver observer;

  @Before
  public void setUp() {
    clock = new FakeApiClock(0);
    executor = RecordingScheduler.create(clock);
    innerCallable = new AccumulatingCallable();
    retryAlgorithm =
        new RetryAlgorithm(
            RetrySettings.newBuilder()
                .setInitialRetryDelay(Duration.ofMillis(2))
                .setRetryDelayMultiplier(2)
                .setMaxRetryDelay(Duration.ofSeconds(1))
                .setTotalTimeout(Duration.ofMinutes(1))
                .build());
    streamTracker = new MyStreamTracker("request");
    context = FakeCallContext.createDefault();
    observer = new MyObserver(true);
  }

  @Test
  public void testNoErrorsAutoFlow() throws Exception {
    String request = "request";
    RetryingServerStream<String, String> stream =
        new RetryingServerStream<>(
            executor, innerCallable, retryAlgorithm, streamTracker, request, context, observer);
    stream.start();

    // Should notify outer observer
    Truth.assertThat(observer.controller).isNotNull();

    Controller controller = innerCallable.popNextCall();
    Truth.assertThat(controller).isNotNull();
    Truth.assertThat(controller.autoFlowControl).isTrue();

    // send a response in auto flow mode
    controller.responseObserver.onResponse("response1");
    controller.responseObserver.onResponse("response2");
    controller.responseObserver.onComplete();

    // Make sure the responses are received
    Truth.assertThat(observer.complete).isTrue();
    Truth.assertThat(observer.responses).containsExactly("response1", "response2").inOrder();
  }

  @Test
  public void testNoErrorsManualFlow() throws Exception {
    String request = "request";

    observer = new MyObserver(false);
    RetryingServerStream<String, String> stream =
        new RetryingServerStream<>(
            executor, innerCallable, retryAlgorithm, streamTracker, request, context, observer);
    stream.start();

    // Should notify outer observer
    Truth.assertThat(observer.controller).isNotNull();

    Controller controller = innerCallable.popNextCall();
    Truth.assertThat(controller).isNotNull();
    Truth.assertThat(controller.autoFlowControl).isFalse();

    // Request & send message 1
    observer.controller.request(1);
    Truth.assertThat(controller.popNextPull()).isEqualTo(1);
    controller.responseObserver.onResponse("response1");

    // Request & send message 1
    observer.controller.request(1);
    Truth.assertThat(controller.popNextPull()).isEqualTo(1);
    controller.responseObserver.onResponse("response2");

    controller.responseObserver.onComplete();

    // Make sure the responses are received
    Truth.assertThat(observer.complete).isTrue();
    Truth.assertThat(observer.responses).containsExactly("response1", "response2").inOrder();
  }

  @Test
  public void testInitialRetry() throws Exception {
    String request = "request";
    RetryingServerStream<String, String> stream =
        new RetryingServerStream<>(
            executor, innerCallable, retryAlgorithm, streamTracker, request, context, observer);
    stream.start();

    // Should notify outer observer
    Truth.assertThat(observer.controller).isNotNull();

    Controller controller = innerCallable.popNextCall();
    Truth.assertThat(controller).isNotNull();
    Truth.assertThat(controller.autoFlowControl).isTrue();

    // Send error
    controller.responseObserver.onError(new FakeApiException(null, Code.UNAVAILABLE, true));

    // After a delay the call was retried
    Truth.assertThat(clock.millisTime())
        .isAtLeast(retryAlgorithm.createFirstAttempt().getRetryDelay().toMillis());
    Truth.assertThat(executor.getIterationsCount()).isEqualTo(1);
    controller = innerCallable.popNextCall();

    controller.responseObserver.onResponse("response");
    Truth.assertThat(observer.responses).containsExactly("response");
  }

  @Test
  public void testMidRetry() throws Exception {
    String request = "request";
    RetryingServerStream<String, String> stream =
        new RetryingServerStream<>(
            executor, innerCallable, retryAlgorithm, streamTracker, request, context, observer);
    stream.start();

    // Should notify outer observer
    Truth.assertThat(observer.controller).isNotNull();

    Controller controller = innerCallable.popNextCall();
    Truth.assertThat(controller).isNotNull();
    Truth.assertThat(controller.autoFlowControl).isTrue();

    controller.responseObserver.onResponse("response1");
    controller.responseObserver.onResponse("response2");
    controller.responseObserver.onError(new FakeApiException(null, Code.UNAVAILABLE, true));

    Truth.assertThat(executor.getIterationsCount()).isEqualTo(1);
    controller = innerCallable.popNextCall();

    controller.responseObserver.onResponse("response3");
    Truth.assertThat(observer.responses)
        .containsExactly("response1", "response2", "response3")
        .inOrder();
  }

  @Test
  public void testMultipleRetry() throws Exception {
    String request = "request";
    RetryingServerStream<String, String> stream =
        new RetryingServerStream<>(
            executor, innerCallable, retryAlgorithm, streamTracker, request, context, observer);
    stream.start();

    Controller controller;
    // Fake 3 errors: the initial call + scheduled retries
    for (int i = 0; i < 3; i++) {
      controller = innerCallable.popNextCall();
      Truth.assertThat(controller).isNotNull();
      Truth.assertThat(controller.autoFlowControl).isTrue();
      controller.responseObserver.onError(new FakeApiException(null, Code.UNAVAILABLE, true));
    }
    // 2 scheduled retries that failed + 1 outstanding
    Truth.assertThat(executor.getIterationsCount()).isEqualTo(3);

    // simulate a success
    controller = innerCallable.popNextCall();
    controller.responseObserver.onResponse("response1");
    Truth.assertThat(observer.responses).containsExactly("response1").inOrder();
  }

  @Test
  public void testRequestCountIsPreserved() throws Exception {
    String request = "request";
    observer = new MyObserver(false);
    RetryingServerStream<String, String> stream =
        new RetryingServerStream<>(
            executor, innerCallable, retryAlgorithm, streamTracker, request, context, observer);
    stream.start();
    observer.controller.request(5);

    Truth.assertThat(observer.controller).isNotNull();
    Controller controller = innerCallable.popNextCall();
    Truth.assertThat(controller).isNotNull();
    Truth.assertThat(controller.autoFlowControl).isFalse();

    Truth.assertThat(controller.popNextPull()).isEqualTo(5);
    // decrement
    controller.responseObserver.onResponse("response");
    // and then error
    controller.responseObserver.onError(new FakeApiException(null, Code.UNAUTHENTICATED, true));

    controller = innerCallable.popNextCall();
    Truth.assertThat(controller.popNextPull()).isEqualTo(4);
  }

  @Test
  public void testCancel() throws Exception {
    String request = "request";
    observer = new MyObserver(false);
    RetryingServerStream<String, String> stream =
        new RetryingServerStream<>(
            executor, innerCallable, retryAlgorithm, streamTracker, request, context, observer);
    stream.start();
    observer.controller.request(1);

    Truth.assertThat(observer.controller).isNotNull();
    Controller controller = innerCallable.popNextCall();
    Truth.assertThat(controller).isNotNull();
    Truth.assertThat(controller.autoFlowControl).isFalse();

    observer.controller.cancel();

    // Check upstream is cancelled
    Truth.assertThat(controller.cancelled).isTrue();

    // and after upstream cancellation is processed, downstream is cancelled, but the cause is replaced
    controller.responseObserver.onError(new RuntimeException("Some external cancellation cause"));
    Truth.assertThat(observer.error).isInstanceOf(CancellationException.class);
  }

  static class AccumulatingCallable extends ServerStreamingCallable<String, String> {
    final BlockingDeque<Controller> controllers = Queues.newLinkedBlockingDeque();

    @Override
    public void call(
        String request, ResponseObserver<String> responseObserver, ApiCallContext context) {
      Controller controller = new Controller(request, responseObserver, context);
      controllers.add(controller);
      responseObserver.onStart(controller);
    }

    Controller popNextCall() throws InterruptedException {
      return controllers.poll(1, TimeUnit.SECONDS);
    }
  }

  static class Controller implements StreamController {
    final String request;
    final ResponseObserver<String> responseObserver;
    final ApiCallContext context;

    boolean cancelled;
    boolean autoFlowControl = true;
    final BlockingQueue<Integer> pulls = Queues.newLinkedBlockingQueue();

    Controller(String request, ResponseObserver<String> responseObserver, ApiCallContext context) {
      this.request = request;
      this.responseObserver = responseObserver;
      this.context = context;
    }

    @Override
    public void cancel() {
      this.cancelled = true;
    }

    @Override
    public void disableAutoInboundFlowControl() {
      autoFlowControl = false;
    }

    @Override
    public void request(int count) {
      pulls.add(count);
    }

    int popNextPull() throws InterruptedException {
      Integer pull = pulls.poll(1, TimeUnit.SECONDS);
      if (pull == null) {
        pull = 0;
      }
      return pull;
    }
  }

  static class MyStreamTracker implements StreamTracker<String, String> {
    String resumeRequest;

    public MyStreamTracker(String resumeRequest) {
      this.resumeRequest = resumeRequest;
    }

    @Override
    public void onProgress(String response) {}

    @Override
    public String getResumeRequest(String originalRequest) {
      return null;
    }
  }

  class RetryAlgorithm implements TimedRetryAlgorithm {
    TimedAttemptSettings prevAttempt;
    TimedAttemptSettings nextAttempt;
    boolean shouldRetry = true;

    public RetryAlgorithm(RetrySettings retrySettings) {
      this.nextAttempt =
          TimedAttemptSettings.newBuilder()
              .setGlobalSettings(retrySettings)
              .setRetryDelay(Duration.ZERO)
              .setRpcTimeout(retrySettings.getTotalTimeout())
              .setRandomizedRetryDelay(Duration.ZERO)
              .setAttemptCount(0)
              .setFirstAttemptStartTimeNanos(clock.nanoTime())
              .build();
    }

    @Override
    public TimedAttemptSettings createFirstAttempt() {
      return nextAttempt;
    }

    @Override
    public TimedAttemptSettings createNextAttempt(TimedAttemptSettings prevSettings) {
      this.prevAttempt = prevAttempt;
      return nextAttempt;
    }

    @Override
    public boolean shouldRetry(TimedAttemptSettings nextAttemptSettings)
        throws CancellationException {
      return shouldRetry;
    }
  }

  static class MyObserver implements ResponseObserver<String> {
    final boolean autoFlow;
    StreamController controller;
    final BlockingDeque<String> responses = Queues.newLinkedBlockingDeque();
    private Throwable error;
    private boolean complete;

    public MyObserver(boolean autoFlow) {
      this.autoFlow = autoFlow;
    }

    @Override
    public void onStart(StreamController controller) {
      this.controller = controller;
      if (!autoFlow) {
        controller.disableAutoInboundFlowControl();
      }
    }

    @Override
    public void onResponse(String response) {
      responses.add(response);
    }

    @Override
    public void onError(Throwable t) {
      this.error = t;
    }

    @Override
    public void onComplete() {
      this.complete = true;
    }
  }
}
