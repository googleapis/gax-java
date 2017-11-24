package com.google.api.gax.rpc;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.rpc.ReapingStreamingCallable.IdleConnectionException;
import com.google.common.collect.Queues;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class ReapingStreamingCallableTest {

  private UpstreamCallable<String, String> upstream;

  private ScheduledExecutorService executor;

  private FakeApiClock clock;
  private Duration waitTime = Duration.ofSeconds(10);
  private Duration idleTime = Duration.ofMinutes(5);
  private Duration checkInterval = Duration.ofSeconds(5);

  private ReapingStreamingCallable<String, String> reaper;

  @Before
  public void setUp() throws Exception {
    clock = new FakeApiClock(0);
    executor = Mockito.mock(ScheduledExecutorService.class);

    upstream = new UpstreamCallable<>();

    reaper = new ReapingStreamingCallable<>(
        upstream,
        executor,
        clock, waitTime, idleTime, checkInterval);
  }

  @Test
  public void testRequestPassthrough()
      throws InterruptedException, ExecutionException, TimeoutException {
    DownstreamObserver<String> downstreamObserver = new DownstreamObserver<>(false);
    reaper.call("req", downstreamObserver);
    downstreamObserver.controller.get(1, TimeUnit.MILLISECONDS).request(1);

    Call<String, String> call = upstream.calls.poll(1, TimeUnit.MILLISECONDS);
    assertEquals(1, (int) call.upstreamController.requests.poll(1, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testWaitTimeout() throws InterruptedException, ExecutionException, TimeoutException {
    DownstreamObserver<String> downstreamObserver = new DownstreamObserver<>(false);
    reaper.call("req", downstreamObserver);
    downstreamObserver.controller.get(1, TimeUnit.MILLISECONDS).request(1);

    Call<String, String> call = upstream.calls.poll(1, TimeUnit.MILLISECONDS);

    clock.incrementNanoTime(waitTime.toNanos() - 1);
    reaper.checkAll();
    assertThat(call.upstreamController.cancelRequest, is(nullValue()));

    clock.incrementNanoTime(1);
    reaper.checkAll();
    assertThat(call.upstreamController.cancelRequest,
        instanceOf(IdleConnectionException.class));
  }

  @Test
  public void testIdleTimeout() throws InterruptedException {
    DownstreamObserver<String> downstreamObserver = new DownstreamObserver<>(false);
    reaper.call("req", downstreamObserver);

    Call<String, String> call = upstream.calls.poll(1, TimeUnit.MILLISECONDS);

    clock.incrementNanoTime(idleTime.toNanos() - 1);
    reaper.checkAll();
    assertThat(call.upstreamController.cancelRequest, is(nullValue()));

    clock.incrementNanoTime(1);
    reaper.checkAll();
    assertThat(call.upstreamController.cancelRequest,
        instanceOf(IdleConnectionException.class));
  }

  @Test
  public void testMultiple() throws InterruptedException, ExecutionException {
    // Start stream1
    DownstreamObserver<String> downstreamObserver1 = new DownstreamObserver<>(false);
    reaper.call("req", downstreamObserver1);
    Call<String, String> call1 = upstream.calls.poll(1, TimeUnit.MILLISECONDS);
    downstreamObserver1.controller.get().request(1);

    // Start stream2
    DownstreamObserver<String> downstreamObserver2 = new DownstreamObserver<>(false);
    reaper.call("req2", downstreamObserver2);
    Call<String, String> call2 = upstream.calls.poll(1, TimeUnit.MILLISECONDS);
    downstreamObserver2.controller.get().request(1);

    // Give stream1 a response at the last possible moment
    clock.incrementNanoTime(waitTime.toNanos());
    call1.observer.onResponse("resp1");

    // run the reaper
    reaper.checkAll();

    // Call1 should be ok
    assertThat(call1.upstreamController.cancelRequest, is(nullValue()));

    // Call2 should be timed out
    assertThat(call2.upstreamController.cancelRequest,
        instanceOf(IdleConnectionException.class));
  }

  static class Call<ReqT, RespT> {

    final ReqT request;
    final ResponseObserver<RespT> observer;
    private final UpstreamController upstreamController;

    public Call(ReqT request, ResponseObserver<RespT> observer,
        UpstreamController upstreamController) {
      this.request = request;
      this.observer = observer;
      this.upstreamController = upstreamController;
    }
  }

  static class UpstreamCallable<ReqT, RespT> extends ServerStreamingCallable<ReqT, RespT> {

    private BlockingQueue<Call<ReqT, RespT>> calls = Queues.newLinkedBlockingDeque();

    @Override
    public void call(ReqT request, ResponseObserver<RespT> observer, ApiCallContext context) {
      Call<ReqT, RespT> call = new Call<>(request, observer, new UpstreamController());
      calls.add(call);
      call.observer.onStart(call.upstreamController);
    }
  }

  static class UpstreamController extends StreamController {

    private BlockingQueue<Integer> requests = Queues.newLinkedBlockingDeque();
    private boolean autoFlowControl = true;
    private Throwable cancelRequest;

    @Override
    public void cancel(Throwable cause) {
      cancelRequest = cause;
    }

    @Override
    public void disableAutoInboundFlowControl() {
      autoFlowControl = false;
    }

    @Override
    public void request(int count) {
      requests.add(count);
    }
  }

  static class DownstreamObserver<T> implements ResponseObserver<T> {

    boolean autoFlowControl;
    SettableApiFuture<StreamController> controller = SettableApiFuture.create();
    Queue<T> responses = Queues.newLinkedBlockingDeque();
    SettableApiFuture<Void> done = SettableApiFuture.create();


    public DownstreamObserver(boolean autoFlowControl) {
      this.autoFlowControl = autoFlowControl;
    }

    @Override
    public void onStart(StreamController controller) {
      if (!autoFlowControl) {
        controller.disableAutoInboundFlowControl();
      }
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