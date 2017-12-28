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
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.StreamController;
import com.google.api.gax.rpc.testing.FakeApiException;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCall;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCallable;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CancellationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class RetryingServerStreamTest {

  private FakeApiClock clock;
  private RecordingScheduler executor;
  private MockServerStreamingCallable<String, String> innerCallable;
  private TimedRetryAlgorithm retryAlgorithm;
  private AccumulatingObserver observer;

  private RetryingServerStream.Builder<String, String> streamBuilder;

  @Before
  public void setUp() {
    clock = new FakeApiClock(0);
    executor = RecordingScheduler.create(clock);
    innerCallable = new MockServerStreamingCallable<>();
    retryAlgorithm =
        new ExponentialRetryAlgorithm(
            RetrySettings.newBuilder()
                .setInitialRetryDelay(Duration.ofMillis(2))
                .setRetryDelayMultiplier(2)
                .setMaxRetryDelay(Duration.ofSeconds(1))
                .setTotalTimeout(Duration.ofMinutes(1))
                .build(),
            clock);
    observer = new AccumulatingObserver(true);

    streamBuilder =
        RetryingServerStream.<String, String>newBuilder()
            .setExecutor(executor)
            .setInnerCallable(innerCallable)
            .setRetryAlgorithm(retryAlgorithm)
            .setStreamTracker(new MyStreamTracker())
            .setInitialRequest("request")
            .setContext(FakeCallContext.createDefault())
            .setOuterObserver(observer);
  }

  @Test
  public void testNoErrorsAutoFlow() throws Exception {
    streamBuilder.build().start();

    // Should notify outer observer
    Truth.assertThat(observer.controller).isNotNull();

    // Should configure the inner controller correctly.
    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();
    Truth.assertThat(call.getController().isAutoFlowControlEnabled()).isTrue();
    Truth.assertThat(call.getRequest()).isEqualTo("request");

    // Send a response in auto flow mode.
    call.getController().getObserver().onResponse("response1");
    call.getController().getObserver().onResponse("response2");
    call.getController().getObserver().onComplete();

    // Make sure the responses are received
    Truth.assertThat(observer.complete).isTrue();
    Truth.assertThat(observer.responses).containsExactly("response1", "response2").inOrder();
  }

  @Test
  public void testNoErrorsManualFlow() throws Exception {
    observer = new AccumulatingObserver(false);
    streamBuilder.setOuterObserver(observer).build().start();

    // Should notify outer observer.
    Truth.assertThat(observer.controller).isNotNull();

    // Should configure the inner controller correctly.
    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();
    Truth.assertThat(call.getController().isAutoFlowControlEnabled()).isFalse();
    Truth.assertThat(call.getRequest()).isEqualTo("request");

    // Request and send message 1.
    observer.controller.request(1);
    Truth.assertThat(call.getController().popLastPull()).isEqualTo(1);
    call.getController().getObserver().onResponse("response1");

    // Request & send message 1.
    observer.controller.request(1);
    Truth.assertThat(call.getController().popLastPull()).isEqualTo(1);
    call.getController().getObserver().onResponse("response2");

    call.getController().getObserver().onComplete();

    // Make sure the responses are received
    Truth.assertThat(observer.complete).isTrue();
    Truth.assertThat(observer.responses).containsExactly("response1", "response2").inOrder();
  }

  @Test
  public void testSimpleRetries() throws Exception {
    streamBuilder.setStreamTracker(new SimpleStreamTracker<String, String>()).build().start();
    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();

    // Send initial error
    call.getController().getObserver().onError(new FakeApiException(null, Code.UNAVAILABLE, true));

    // After a delay the call was retried with the original request
    Truth.assertThat(clock.millisTime())
        .isAtLeast(retryAlgorithm.createFirstAttempt().getRetryDelay().toMillis());
    Truth.assertThat(executor.getIterationsCount()).isEqualTo(1);
    call = innerCallable.popLastCall();

    // Verify the request and send a response
    Truth.assertThat(call.getRequest()).isEqualTo("request");
    call.getController().getObserver().onResponse("response");
    Truth.assertThat(observer.responses).containsExactly("response");

    // Send another error
    call.getController().getObserver().onError(new FakeApiException(null, Code.UNAVAILABLE, true));

    // The retries should end
    Truth.assertThat(observer.error).isNotNull();
  }

  @Test
  public void testInitialRetry() throws Exception {
    streamBuilder.build().start();
    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();

    // Send error
    call.getController().getObserver().onError(new FakeApiException(null, Code.UNAVAILABLE, true));

    // After a delay the call was retried with the original request
    Truth.assertThat(clock.millisTime())
        .isAtLeast(retryAlgorithm.createFirstAttempt().getRetryDelay().toMillis());
    Truth.assertThat(executor.getIterationsCount()).isEqualTo(1);
    call = innerCallable.popLastCall();

    // Verify the request and send an ok response
    Truth.assertThat(call.getRequest()).isEqualTo("request");
    call.getController().getObserver().onResponse("response");
    call.getController().getObserver().onComplete();
    Truth.assertThat(observer.responses).containsExactly("response");
  }

  @Test
  public void testMidRetry() throws Exception {
    streamBuilder.build().start();
    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();

    // Respond to the initial request with a coupple responses and an error.
    Truth.assertThat(call.getRequest()).isEqualTo("request");
    call.getController().getObserver().onResponse("response1");
    call.getController().getObserver().onResponse("response2");
    call.getController().getObserver().onError(new FakeApiException(null, Code.UNAVAILABLE, true));

    // After a delay the call was retried
    Truth.assertThat(clock.millisTime())
        .isAtLeast(retryAlgorithm.createFirstAttempt().getRetryDelay().toMillis());
    Truth.assertThat(executor.getIterationsCount()).isEqualTo(1);
    call = innerCallable.popLastCall();

    // Verify that the request was narrowed and send a response
    Truth.assertThat(call.getRequest()).isEqualTo("request > 2");
    call.getController().getObserver().onResponse("response3");
    Truth.assertThat(observer.responses)
        .containsExactly("response1", "response2", "response3")
        .inOrder();
  }

  @Test
  public void testMultipleRetry() throws Exception {
    streamBuilder.build().start();

    MockServerStreamingCall<String, String> call;
    // Fake 3 errors: the initial call + 2 scheduled retries
    for (int i = 0; i < 3; i++) {
      call = innerCallable.popLastCall();
      Truth.assertThat(call).isNotNull();
      Truth.assertThat(call.getController().isAutoFlowControlEnabled()).isTrue();
      call.getController()
          .getObserver()
          .onError(new FakeApiException(null, Code.UNAVAILABLE, true));
    }
    // 2 scheduled retries that failed + 1 outstanding
    Truth.assertThat(executor.getIterationsCount()).isEqualTo(3);

    // simulate a success
    call = innerCallable.popLastCall();
    call.getController().getObserver().onResponse("response1");
    Truth.assertThat(observer.responses).containsExactly("response1").inOrder();
  }

  @Test
  public void testRequestCountIsPreserved() throws Exception {
    observer = new AccumulatingObserver(false);
    streamBuilder.setOuterObserver(observer).build().start();

    observer.controller.request(5);

    Truth.assertThat(observer.controller).isNotNull();
    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();
    Truth.assertThat(call).isNotNull();
    Truth.assertThat(call.getController().isAutoFlowControlEnabled()).isFalse();

    Truth.assertThat(call.getController().popLastPull()).isEqualTo(5);
    // decrement
    call.getController().getObserver().onResponse("response");
    // and then error
    call.getController()
        .getObserver()
        .onError(new FakeApiException(null, Code.UNAUTHENTICATED, true));

    call = innerCallable.popLastCall();
    Truth.assertThat(call.getController().popLastPull()).isEqualTo(4);
  }

  @Test
  public void testCancel() throws Exception {
    observer = new AccumulatingObserver(false);
    streamBuilder.setOuterObserver(observer).build().start();

    observer.controller.request(1);

    Truth.assertThat(observer.controller).isNotNull();
    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();
    Truth.assertThat(call).isNotNull();
    Truth.assertThat(call.getController().isAutoFlowControlEnabled()).isFalse();

    observer.controller.cancel();

    // Check upstream is cancelled
    Truth.assertThat(call.getController().isCancelled()).isTrue();

    // and after upstream cancellation is processed, downstream is cancelled, but the cause is replaced
    call.getController()
        .getObserver()
        .onError(new RuntimeException("Some external cancellation cause"));
    Truth.assertThat(observer.error).isInstanceOf(CancellationException.class);
  }

  static class MyStreamTracker implements StreamTracker<String, String> {
    private int responseCount;

    @Override
    public StreamTracker<String, String> createNew() {
      return new MyStreamTracker();
    }

    @Override
    public void onProgress(String response) {
      responseCount++;
    }

    @Override
    public String getResumeRequest(String originalRequest) {
      if (responseCount == 0) {
        return originalRequest;
      } else {
        return originalRequest + " > " + responseCount;
      }
    }
  }

  static class AccumulatingObserver implements ResponseObserver<String> {
    final boolean autoFlow;
    StreamController controller;
    final BlockingDeque<String> responses = Queues.newLinkedBlockingDeque();
    private Throwable error;
    private boolean complete;

    AccumulatingObserver(boolean autoFlow) {
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
