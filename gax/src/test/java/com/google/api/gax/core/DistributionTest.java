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
package com.google.api.gax.core;

import com.google.common.truth.Truth;
import org.junit.Before;
import org.junit.Test;

public class DistributionTest {
  private Distribution distribution;

  @Before
  public void setup() {
    distribution = new Distribution(10);
  }

  @Test
  public void testGetCount() {
    distribution.record(0);
    distribution.record(1);
    distribution.record(100);
    Truth.assertThat(distribution.getCount()).isEqualTo(3);
  }

  @Test
  public void testGetMean() {
    distribution.record(0);
    distribution.record(1);
    Truth.assertThat(distribution.getMean()).isWithin(0.01).of(0.5);

    distribution.record(1);
    Truth.assertThat(distribution.getMean()).isWithin(0.01).of(0.66);

    distribution.record(2);
    Truth.assertThat(distribution.getMean()).isWithin(0.01).of(1);
  }

  @Test
  public void testReset() {
    distribution.record(1);
    distribution.reset();
    Truth.assertThat(distribution.getCount()).isEqualTo(0);
    Truth.assertThat(distribution.getMean()).isWithin(0.01).of(0);
  }

  @Test
  public void testCopy() {
    Distribution copy = distribution.copy();
    copy.record(1);
    Truth.assertThat(distribution.getCount()).isEqualTo(0);
    Truth.assertThat(copy.getCount()).isEqualTo(1);

    for (long l : distribution.getBucketCounts()) {
      Truth.assertThat(l).isEqualTo(0);
    }
  }

  @Test
  public void testPercentile() {
    Truth.assertThat(distribution.getNthPercentile(50)).isEqualTo(0);

    distribution.record(1);
    distribution.record(2);
    distribution.record(3);
    Truth.assertThat(distribution.getNthPercentile(50)).isEqualTo(2);
  }
}
