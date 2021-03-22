/*
 * Copyright 2021 Google LLC
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
package com.google.api.gax.batching;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.gax.batching.FlowControlEventStats.FlowControlEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FlowControlEventStatsTest {

  @Test
  public void testGetLastEvent() throws InterruptedException {
    final FlowControlEventStats stats = new FlowControlEventStats();
    final long currentTime = System.currentTimeMillis();

    List<Thread> threads = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      final int timeElapsed = i;
      Thread t =
          new Thread(
              new Runnable() {
                @Override
                public void run() {
                  stats.recordFlowControlEvent(
                      FlowControlEvent.create(currentTime + timeElapsed, timeElapsed));
                }
              });
      threads.add(t);
      t.start();
    }

    for (Thread t : threads) {
      t.join(10);
    }

    assertThat(stats.getLastFlowControlEvent().getTimestampMs()).isEqualTo(currentTime + 100);
    assertThat(stats.getLastFlowControlEvent().getThrottledTime(TimeUnit.MILLISECONDS))
        .isEqualTo(100);
  }
}
