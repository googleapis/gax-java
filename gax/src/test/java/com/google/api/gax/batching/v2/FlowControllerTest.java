/*
 * Copyright 2019 Google LLC
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
package com.google.api.gax.batching.v2;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.batching.v2.FlowControlException.MaxOutstandingElementCountReachedException;
import com.google.api.gax.batching.v2.FlowControlException.MaxOutstandingRequestBytesReachedException;
import com.google.api.gax.batching.v2.FlowControlSettings.LimitExceededBehavior;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FlowController}. */
@RunWith(JUnit4.class)
public class FlowControllerTest {

  @Test
  public void testReserveRelease_ok() {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(10)
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Ignore)
                .build());

    flowController.reserve(20);
    flowController.release(1, 20);
  }

  @Test
  public void testInvalidArguments() {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(10)
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    flowController.reserve(0);
    Exception actualException = null;
    try {
      flowController.reserve(-1);
    } catch (IllegalArgumentException expected) {
      actualException = expected;
    }
    assertThat(actualException).isInstanceOf(IllegalArgumentException.class);
    flowController.release(1, 0);
  }

  @Test
  public void testReserveRelease_noLimits_ok() {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    flowController.reserve(1);
    flowController.release(1, 1);
  }

  @Test
  public void testReserveRelease_ignore_ok() {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(1)
                .setMaxOutstandingRequestBytes(1L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    flowController.reserve(1);
    flowController.release(1, 1);
  }

  @Test
  public void testReserveRelease_blockedByElementCount_noBytesLimit() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(2)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());
    flowController.reserve(1);

    assertBlockingReserveReleaseBehavior(flowController, 20);
  }

  @Test
  public void testReserveRelease_blockedByNumberOfBytes() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(100)
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    assertBlockingReserveReleaseBehavior(flowController, 10);
  }

  @Test
  public void testReserveRelease_blockedByNumberOfBytes_noElementCountLimit() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    assertBlockingReserveReleaseBehavior(flowController, 10);
  }

  private static void assertBlockingReserveReleaseBehavior(
      final FlowController flowController, final int maxNumBytes) throws Exception {

    // This will stop from reserving maxNumBytes.
    flowController.reserve(1);

    final SettableFuture<?> permitsReserved = SettableFuture.create();
    Future<?> finished =
        Executors.newCachedThreadPool()
            .submit(
                new Runnable() {
                  @Override
                  public void run() {
                    permitsReserved.set(null);
                    flowController.reserve(maxNumBytes);
                  }
                });

    permitsReserved.get();
    flowController.release(1, 1);
    finished.get();
    flowController.release(1, maxNumBytes);
  }

  /*




  */

  @Test
  public void testReserveRelease_rejectedByElementCount_noBytesLimit() {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(1)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    assertRejectedReserveReleaseBehavior(
        flowController, 5, MaxOutstandingElementCountReachedException.class);
  }

  @Test
  public void testReserveRelease_rejectedByNumberOfBytes() {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(100)
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    assertRejectedReserveReleaseBehavior(
        flowController, 10, MaxOutstandingRequestBytesReachedException.class);
  }

  @Test
  public void testReserveRelease_rejectedByNumberOfBytes_noElementCountLimit() {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    assertRejectedReserveReleaseBehavior(
        flowController, 10, MaxOutstandingRequestBytesReachedException.class);
  }

  @Test
  public void testRestoreAfterFail() throws FlowControlException {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(2)
                .setMaxOutstandingRequestBytes(5L)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    flowController.reserve(1);
    FlowControlException actualError = null;
    try {
      flowController.reserve(5);
    } catch (MaxOutstandingRequestBytesReachedException e) {
      actualError = e;
    }

    assertThat(actualError).isInstanceOf(MaxOutstandingRequestBytesReachedException.class);
    flowController.release(2, 5);
  }

  private void assertRejectedReserveReleaseBehavior(
      FlowController flowController,
      int maxNumBytes,
      Class<? extends FlowControlException> expectedException)
      throws FlowControlException {

    flowController.reserve(1);
    FlowControlException actualError = null;
    try {
      flowController.reserve(maxNumBytes);
    } catch (FlowControlException e) {
      actualError = e;
    }
    assertThat(actualError).isInstanceOf(expectedException);
    flowController.release(2, maxNumBytes);
  }
}
