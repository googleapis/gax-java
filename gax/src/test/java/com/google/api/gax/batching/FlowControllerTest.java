/*
 * Copyright 2016, Google Inc. All rights reserved.
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
package com.google.api.gax.batching;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.gax.batching.FlowController.LimitExceededBehavior;
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
  public void testReserveRelease_ok() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(10L)
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    flowController.reserve(1, 1);
    flowController.release(1, 1);
  }

  @Test
  public void testInvalidArguments() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(10L)
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    flowController.reserve(0, 0);
    try {
      flowController.reserve(-1, 1);
      fail("Must have thrown an illegal argument error");
    } catch (IllegalArgumentException expected) {
      // Expected
    }
    try {
      flowController.reserve(1, -1);
      fail("Must have thrown an illegal argument error");
    } catch (IllegalArgumentException expected) {
      // Expected
    }
  }

  @Test
  public void testReserveRelease_noLimits_ok() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    flowController.reserve(1, 1);
    flowController.release(1, 1);
  }

  @Test
  public void testReserveRelease_ignore_ok() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(1L)
                .setMaxOutstandingRequestBytes(1L)
                .setLimitExceededBehavior(LimitExceededBehavior.Ignore)
                .build());

    flowController.reserve(1, 1);
    flowController.release(1, 1);
  }

  @Test
  public void testReserveRelease_blockedByElementCount() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(10L)
                .setMaxOutstandingRequestBytes(100L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    testBlockingReserveRelease(flowController, 10, 10);
  }

  @Test
  public void testReserveRelease_blockedByElementCount_noBytesLimit() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    testBlockingReserveRelease(flowController, 10, 10);
  }

  @Test
  public void testReserveRelease_blockedByNumberOfBytes() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(100L)
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    testBlockingReserveRelease(flowController, 10, 10);
  }

  @Test
  public void testReserveRelease_blockedByNumberOfBytes_noElementCountLimit() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    testBlockingReserveRelease(flowController, 10, 10);
  }

  private static void testBlockingReserveRelease(
      final FlowController flowController, final int maxElementCount, final int maxNumBytes)
      throws Exception {

    flowController.reserve(1, 1);

    final SettableFuture<?> permitsReserved = SettableFuture.create();
    Future<?> finished =
        Executors.newCachedThreadPool()
            .submit(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      permitsReserved.set(null);
                      flowController.reserve(maxElementCount, maxNumBytes);
                    } catch (FlowController.FlowControlException e) {
                      throw new AssertionError(e);
                    }
                  }
                });

    permitsReserved.get();
    flowController.release(1, 1);

    finished.get();
  }

  @Test
  public void testReserveRelease_rejectedByElementCount() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(10L)
                .setMaxOutstandingRequestBytes(100L)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    testRejectedReserveRelease(
        flowController, 10, 10, FlowController.MaxOutstandingElementCountReachedException.class);
  }

  @Test
  public void testReserveRelease_rejectedByElementCount_noBytesLimit() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    testRejectedReserveRelease(
        flowController, 10, 10, FlowController.MaxOutstandingElementCountReachedException.class);
  }

  @Test
  public void testReserveRelease_rejectedByNumberOfBytes() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(100L)
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    testRejectedReserveRelease(
        flowController, 10, 10, FlowController.MaxOutstandingRequestBytesReachedException.class);
  }

  @Test
  public void testReserveRelease_rejectedByNumberOfBytes_noElementCountLimit() throws Exception {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingRequestBytes(10L)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    testRejectedReserveRelease(
        flowController, 10, 10, FlowController.MaxOutstandingRequestBytesReachedException.class);
  }

  @Test
  public void testRestoreAfterFail() throws FlowController.FlowControlException {
    FlowController flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(2L)
                .setMaxOutstandingRequestBytes(1L)
                .setLimitExceededBehavior(LimitExceededBehavior.ThrowException)
                .build());

    flowController.reserve(1, 1);

    try {
      flowController.reserve(1, 1);
      throw new IllegalStateException("flowController should not have any bytes left");
    } catch (FlowController.MaxOutstandingRequestBytesReachedException e) {
      // Ignore.
    }

    flowController.reserve(1, 0);
  }

  private void testRejectedReserveRelease(
      FlowController flowController,
      int maxElementCount,
      int maxNumBytes,
      Class<? extends FlowController.FlowControlException> expectedException)
      throws FlowController.FlowControlException {

    flowController.reserve(1, 1);

    try {
      flowController.reserve(maxElementCount, maxNumBytes);
      fail("Should thrown a FlowController.FlowControlException");
    } catch (FlowController.FlowControlException e) {
      assertTrue(expectedException.isInstance(e));
    }

    flowController.release(1, 1);

    flowController.reserve(maxElementCount, maxNumBytes);
  }
}
