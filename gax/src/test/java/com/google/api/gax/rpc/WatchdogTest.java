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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCall;
import com.google.api.gax.rpc.testing.MockStreamingApi.MockServerStreamingCallable;
import com.google.common.collect.Queues;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class WatchdogTest {
  private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);

  private FakeApiClock clock;
  private final Duration checkInterval = Duration.ofMillis(1000);
  private Duration waitTime = Duration.ofSeconds(10);
  private Duration idleTime = Duration.ofMinutes(5);

  private Watchdog watchdog;
  private MockServerStreamingCallable<String, String> callable;
  private AccumulatingObserver<String> innerObserver;
  private MockServerStreamingCall<String, String> call;

  @Before
  public void setUp() {
    clock = new FakeApiClock(0);
    watchdog = Watchdog.create(clock, checkInterval, EXECUTOR);

    callable = new MockServerStreamingCallable<>();
    innerObserver = new AccumulatingObserver<>();
    callable.call("request", watchdog.watch(innerObserver, waitTime, idleTime));
    call = callable.popLastCall();
  }

  @Test
  public void testRequestPassthrough() throws Exception {
    innerObserver.controller.get().request(1);
    assertThat(call.getController().popLastPull()).isEqualTo(1);
  }

  @Test
  public void testWaitTimeout() throws Exception {
    innerObserver.controller.get(1, TimeUnit.MILLISECONDS).request(1);

    clock.incrementNanoTime(waitTime.toNanos() - 1);
    watchdog.run();
    assertThat(call.getController().isCancelled()).isFalse();

    clock.incrementNanoTime(1);
    watchdog.run();
    assertThat(call.getController().isCancelled()).isTrue();
    call.getController()
        .getObserver()
        .onError(new RuntimeException("Some upstream exception representing cancellation"));

    Throwable actualError = null;
    try {
      innerObserver.done.get();
    } catch (ExecutionException t) {
      actualError = t.getCause();
    }
    assertThat(actualError).isInstanceOf(WatchdogTimeoutException.class);
  }

  @Test
  public void testIdleTimeout() throws InterruptedException {
    clock.incrementNanoTime(idleTime.toNanos() - 1);
    watchdog.run();
    assertThat(call.getController().isCancelled()).isFalse();

    clock.incrementNanoTime(1);
    watchdog.run();
    assertThat(call.getController().isCancelled()).isTrue();
    call.getController()
        .getObserver()
        .onError(new RuntimeException("Some upstream exception representing cancellation"));

    Throwable actualError = null;
    try {
      innerObserver.done.get();
    } catch (ExecutionException t) {
      actualError = t.getCause();
    }
    assertThat(actualError).isInstanceOf(WatchdogTimeoutException.class);
  }

  @Test
  public void testTimedOutBeforeStart() throws InterruptedException {
    MockServerStreamingCallable<String, String> callable1 = new MockServerStreamingCallable<>();
    AccumulatingObserver<String> downstreamObserver1 = new AccumulatingObserver<>();
    ResponseObserver observer = watchdog.watch(downstreamObserver1, waitTime, idleTime);
    clock.incrementNanoTime(idleTime.toNanos() + 1);
    // This should not remove callable1 from watched list
    watchdog.run();
    assertThat(downstreamObserver1.done.isDone()).isFalse();

    callable1.call("request", observer);
    // This should cancel callable1
    watchdog.run();
    MockServerStreamingCall<String, String> call1 = callable1.popLastCall();
    assertThat(call1.getController().isCancelled()).isTrue();
    call1.getController().getObserver().onError(new CancellationException("User cancelled"));
    Throwable error = null;
    try {
      downstreamObserver1.done.get();
    } catch (ExecutionException t) {
      error = t.getCause();
    }
    assertThat(error).isInstanceOf(WatchdogTimeoutException.class);
  }

  @Test
  public void testMultiple() throws Exception {
    // Start stream1
    AccumulatingObserver<String> downstreamObserver1 = new AccumulatingObserver<>();
    callable.call("request", watchdog.watch(downstreamObserver1, waitTime, idleTime));
    MockServerStreamingCall<String, String> call1 = callable.popLastCall();
    downstreamObserver1.controller.get().request(1);

    // Start stream2
    AccumulatingObserver<String> downstreamObserver2 = new AccumulatingObserver<>();
    callable.call("req2", watchdog.watch(downstreamObserver2, waitTime, idleTime));
    MockServerStreamingCall<String, String> call2 = callable.popLastCall();
    downstreamObserver2.controller.get().request(1);

    // Give stream1 a response at the last possible moment
    clock.incrementNanoTime(waitTime.toNanos());
    call1.getController().getObserver().onResponse("resp1");

    // run the callable
    watchdog.run();

    // Call1 should be ok
    assertThat(call1.getController().isCancelled()).isFalse();
    // Should not throw
    assertThat(downstreamObserver1.done.isDone()).isFalse();

    // Call2 should be timed out
    assertThat(call2.getController().isCancelled()).isTrue();
    call2.getController().getObserver().onError(new CancellationException("User cancelled"));
    Throwable error = null;
    try {
      downstreamObserver2.done.get();
    } catch (ExecutionException t) {
      error = t.getCause();
    }
    assertThat(error).isInstanceOf(WatchdogTimeoutException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testWatchdogBeingClosed() {
    ScheduledFuture future = Mockito.mock(ScheduledFuture.class);
    ScheduledExecutorService mockExecutor = getMockExecutorService(future);
    Watchdog underTest = Watchdog.create(clock, checkInterval, mockExecutor);
    assertThat(underTest).isInstanceOf(BackgroundResource.class);

    underTest.close();
    underTest.shutdown();

    Mockito.verify(mockExecutor)
        .scheduleAtFixedRate(
            underTest, checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
    Mockito.verify(future, Mockito.times(2)).cancel(false);

    underTest.shutdownNow();
    Mockito.verify(future).cancel(true);
    Mockito.verifyNoMoreInteractions(mockExecutor);
  }

  @Test
  public void awaitTermination_shouldReturnTrueIfFutureIsDone() throws Exception {
    int duration = 1000;
    TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    ScheduledFuture future = Mockito.mock(ScheduledFuture.class);
    ScheduledExecutorService mockExecutor = getMockExecutorService(future);
    Watchdog watchdog = Watchdog.create(clock, checkInterval, mockExecutor);
    watchdog.shutdown();

    boolean actual = watchdog.awaitTermination(duration, timeUnit);

    assertThat(actual).isTrue();
  }

  @Test
  public void awaitTermination_shouldReturnFalseIfGettingFutureTimedOut() throws Exception {
    int duration = 1000;
    TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    ScheduledFuture future = Mockito.mock(ScheduledFuture.class);
    Mockito.doThrow(new TimeoutException()).when(future).get(duration, timeUnit);
    ScheduledExecutorService mockExecutor = getMockExecutorService(future);
    Watchdog watchdog = Watchdog.create(clock, checkInterval, mockExecutor);

    boolean actual = watchdog.awaitTermination(duration, timeUnit);

    assertThat(actual).isFalse();
  }

  @Test
  public void awaitTermination_shouldReturnTrueIfFutureIsAlreadyCancelled() throws Exception {
    int duration = 1000;
    TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    ScheduledFuture future = Mockito.mock(ScheduledFuture.class);
    Mockito.doThrow(new CancellationException()).when(future).get(duration, timeUnit);
    ScheduledExecutorService mockExecutor = getMockExecutorService(future);
    Watchdog watchdog = Watchdog.create(clock, checkInterval, mockExecutor);

    boolean actual = watchdog.awaitTermination(duration, timeUnit);

    assertThat(actual).isTrue();
  }

  @Test
  public void awaitTermination_shouldReturnFalseIfGettingFutureThrowsExecutionException()
      throws Exception {
    int duration = 1000;
    TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    ScheduledFuture future = Mockito.mock(ScheduledFuture.class);
    Mockito.doThrow(new ExecutionException(new RuntimeException()))
        .when(future)
        .get(duration, timeUnit);
    ScheduledExecutorService mockExecutor = getMockExecutorService(future);
    Watchdog watchdog = Watchdog.create(clock, checkInterval, mockExecutor);

    boolean actual = watchdog.awaitTermination(duration, timeUnit);

    assertThat(actual).isTrue();
  }

  private ScheduledExecutorService getMockExecutorService(ScheduledFuture future) {
    ScheduledExecutorService mockExecutor = Mockito.mock(ScheduledExecutorService.class);
    Mockito.when(
            mockExecutor.scheduleAtFixedRate(
                Mockito.any(Watchdog.class),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.any(TimeUnit.class)))
        .thenReturn(future);
    return mockExecutor;
  }

  static class AccumulatingObserver<T> implements ResponseObserver<T> {
    SettableApiFuture<StreamController> controller = SettableApiFuture.create();
    Queue<T> responses = Queues.newLinkedBlockingDeque();
    SettableApiFuture<Void> done = SettableApiFuture.create();

    @Override
    public void onStart(StreamController controller) {
      controller.disableAutoInboundFlowControl();
      this.controller.set(controller);
    }

    @Override
    public void onResponse(T response) {
      responses.add(response);
    }

    @Override
    public void onError(Throwable t) {
      done.setException(t);
    }

    @Override
    public void onComplete() {
      done.set(null);
    }
  }
}
