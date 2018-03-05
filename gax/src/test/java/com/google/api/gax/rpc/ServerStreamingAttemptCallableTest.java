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
package com.google.api.gax.rpc;

import com.google.api.core.AbstractApiFuture;
import com.google.api.core.ApiFuture;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.api.gax.retrying.ServerStreamingAttemptException;
import com.google.api.gax.retrying.StreamResumptionStrategy;
import com.google.api.gax.retrying.TimedAttemptSettings;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.testing.FakeApiException;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCall;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCallable;
import com.google.common.collect.Queues;
import com.google.common.truth.Truth;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class ServerStreamingAttemptCallableTest {
  private MockServerStreamingCallable<String, String> innerCallable;
  private AccumulatingObserver observer;
  private FakeRetryingFuture fakeRetryingFuture;
  private StreamResumptionStrategy<String, String> resumptionStrategy;

  @Before
  public void setUp() {
    innerCallable = new MockServerStreamingCallable<>();
    observer = new AccumulatingObserver(true);
    resumptionStrategy = new MyStreamResumptionStrategy();
  }

  private ServerStreamingAttemptCallable<String, String> createCallable() {
    ServerStreamingAttemptCallable<String, String> callable =
        new ServerStreamingAttemptCallable<>(
            innerCallable,
            resumptionStrategy,
            "request",
            FakeCallContext.createDefault(),
            observer);

    fakeRetryingFuture = new FakeRetryingFuture(callable);
    callable.setExternalFuture(fakeRetryingFuture);

    return callable;
  }

  @Test
  public void testNoErrorsAutoFlow() {
    ServerStreamingAttemptCallable<String, String> callable = createCallable();
    callable.start();

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
    Truth.assertThat(observer.responses).containsExactly("response1", "response2").inOrder();
    fakeRetryingFuture.assertSuccess();
  }

  @Test
  public void testNoErrorsManualFlow() {
    observer = new AccumulatingObserver(false);
    ServerStreamingAttemptCallable<String, String> callable = createCallable();
    callable.start();

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
    Truth.assertThat(observer.responses).containsExactly("response1", "response2").inOrder();
    fakeRetryingFuture.assertSuccess();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testInitialRetry() {
    resumptionStrategy = new MyStreamResumptionStrategy();
    ServerStreamingAttemptCallable<String, String> callable = createCallable();
    callable.start();

    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();

    // Send initial error
    FakeApiException initialError = new FakeApiException(null, Code.UNAVAILABLE, true);
    call.getController().getObserver().onError(initialError);

    // Should notify the outer future
    Throwable outerError = null;
    try {
      fakeRetryingFuture.getAttemptResult().get(1, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      outerError = e.getCause();
    } catch (Throwable e) {
      outerError = e;
    }
    Truth.assertThat(outerError).isInstanceOf(ServerStreamingAttemptException.class);
    Truth.assertThat(((ServerStreamingAttemptException) outerError).hasSeenResponses()).isFalse();
    Truth.assertThat(((ServerStreamingAttemptException) outerError).canResume()).isTrue();
    Truth.assertThat(outerError.getCause()).isEqualTo(initialError);

    // Make the retry call
    callable.call();
    call = innerCallable.popLastCall();

    // Verify the request and send a response
    Truth.assertThat(call.getRequest()).isEqualTo("request > 0");
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testMidRetry() {
    resumptionStrategy = new MyStreamResumptionStrategy();
    ServerStreamingAttemptCallable<String, String> callable = createCallable();
    callable.start();

    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();

    // Respond to the initial request with a coupple responses and an error.
    Truth.assertThat(call.getRequest()).isEqualTo("request");
    call.getController().getObserver().onResponse("response1");
    call.getController().getObserver().onResponse("response2");

    FakeApiException innerError = new FakeApiException(null, Code.UNAVAILABLE, true);
    call.getController().getObserver().onError(innerError);

    // Should notify the outer future
    Throwable outerError = null;
    try {
      fakeRetryingFuture.getAttemptResult().get(1, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      outerError = e.getCause();
    } catch (Throwable e) {
      outerError = e;
    }
    Truth.assertThat(outerError).isInstanceOf(ServerStreamingAttemptException.class);
    Truth.assertThat(((ServerStreamingAttemptException) outerError).hasSeenResponses()).isTrue();
    Truth.assertThat(((ServerStreamingAttemptException) outerError).canResume()).isTrue();
    Truth.assertThat(outerError.getCause()).isEqualTo(innerError);

    // Make the retry call
    callable.call();
    call = innerCallable.popLastCall();

    // Verify that the request was narrowed and send a response
    Truth.assertThat(call.getRequest()).isEqualTo("request > 2");
    call.getController().getObserver().onResponse("response3");
    Truth.assertThat(observer.responses)
        .containsExactly("response1", "response2", "response3")
        .inOrder();
  }

  @Test
  public void testRequestCountIsPreserved() {
    observer = new AccumulatingObserver(false);
    ServerStreamingAttemptCallable<String, String> callable = createCallable();
    callable.start();

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

    // Make the retry call
    callable.call();
    call = innerCallable.popLastCall();

    // Verify that the count is correct
    Truth.assertThat(call.getController().popLastPull()).isEqualTo(4);
  }

  @Test
  public void testCancel() {
    observer = new AccumulatingObserver(false);
    ServerStreamingAttemptCallable<String, String> callable = createCallable();
    callable.start();

    observer.controller.request(1);

    Truth.assertThat(observer.controller).isNotNull();
    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();
    Truth.assertThat(call).isNotNull();
    Truth.assertThat(call.getController().isAutoFlowControlEnabled()).isFalse();

    observer.controller.cancel();

    // Check upstream is cancelled
    Truth.assertThat(call.getController().isCancelled()).isTrue();

    // and after upstream cancellation is processed, downstream is cancelled, but the cause is replaced
    RuntimeException innerException =
        new RuntimeException("Some internal representation of cancel");
    call.getController().getObserver().onError(innerException);

    Throwable outerError = null;
    try {
      fakeRetryingFuture.getAttemptResult().get(1, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      outerError = e.getCause();
    } catch (Throwable e) {
      outerError = e;
    }

    Truth.assertThat(outerError).isInstanceOf(ServerStreamingAttemptException.class);
    Truth.assertThat(outerError.getCause()).isInstanceOf(CancellationException.class);

    // Make sure that the stack trace of the cancellation is preserved
    boolean includesMeInStackTrace = false;
    for (StackTraceElement e : outerError.getCause().getStackTrace()) {
      if (ServerStreamingAttemptCallableTest.class.getName().equals(e.getClassName())) {
        includesMeInStackTrace = true;
        break;
      }
    }
    Truth.assertWithMessage("Cancel caller included in stack trace")
        .that(includesMeInStackTrace)
        .isTrue();
  }

  @Test
  public void testResponseSubstitution() {
    resumptionStrategy =
        new MyStreamResumptionStrategy() {
          @Override
          public String processResponse(String response) {
            return super.processResponse(response) + "+suffix";
          }
        };

    observer = new AccumulatingObserver(false);
    ServerStreamingAttemptCallable<String, String> callable = createCallable();
    callable.start();

    MockServerStreamingCall<String, String> call = innerCallable.popLastCall();

    // Send initial response & then error
    call.getController().getObserver().onResponse("first");
    call.getController().getObserver().onError(new FakeApiException(null, Code.UNAVAILABLE, true));

    // Make the retry call
    callable.call();
    call = innerCallable.popLastCall();

    // Send another couple of responses (the first one will be ignored)
    call.getController().getObserver().onResponse("second");
    call.getController().getObserver().onResponse("third");
    call.getController().getObserver().onComplete();

    // Verify the request and send a response
    Truth.assertThat(observer.responses)
        .containsExactly("first+suffix", "second+suffix", "third+suffix");
  }

  static class MyStreamResumptionStrategy implements StreamResumptionStrategy<String, String> {
    private int responseCount;

    @Override
    public StreamResumptionStrategy<String, String> createNew() {
      return new MyStreamResumptionStrategy();
    }

    @Override
    public String processResponse(String response) {
      responseCount++;
      return response;
    }

    @Override
    public String getResumeRequest(String originalRequest) {
      return originalRequest + " > " + responseCount;
    }

    @Override
    public boolean canResume() {
      return true;
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

  private static class FakeRetryingFuture extends AbstractApiFuture<Void>
      implements RetryingFuture<Void> {
    private final ServerStreamingAttemptCallable<String, String> attemptCallable;
    private ApiFuture<Void> attemptFuture;
    private TimedAttemptSettings attemptSettings;

    FakeRetryingFuture(ServerStreamingAttemptCallable<String, String> attemptCallable) {
      this.attemptCallable = attemptCallable;
      attemptSettings =
          TimedAttemptSettings.newBuilder()
              .setGlobalSettings(
                  RetrySettings.newBuilder().setTotalTimeout(Duration.ofHours(1)).build())
              .setFirstAttemptStartTimeNanos(0)
              .setAttemptCount(0)
              .setRandomizedRetryDelay(Duration.ofMillis(1))
              .setRetryDelay(Duration.ofMillis(1))
              .setRpcTimeout(Duration.ofMinutes(1))
              .build();
    }

    @Override
    public void setAttemptFuture(ApiFuture<Void> attemptFuture) {
      this.attemptFuture = attemptFuture;
    }

    @Override
    public ServerStreamingAttemptCallable<String, String> getCallable() {
      return attemptCallable;
    }

    @Override
    public TimedAttemptSettings getAttemptSettings() {
      return attemptSettings;
    }

    @Override
    public ApiFuture<Void> peekAttemptResult() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ApiFuture<Void> getAttemptResult() {
      return attemptFuture;
    }

    void assertSuccess() {
      Throwable actualError = null;
      try {
        attemptFuture.get(1, TimeUnit.SECONDS);
      } catch (Throwable t) {
        actualError = t;
      }
      Truth.assertThat(actualError).isNull();
    }
  }
}
