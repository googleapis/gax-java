/*
 * Copyright 2016, Google LLC All rights reserved.
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
package com.google.api.gax.core;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class DistributionTest {
  // These tests come from examples in https://en.wikipedia.org/wiki/Percentile#The_nearest-rank_method
  @Test
  public void testPercentile() {
    Distribution dist;

    dist = with(15, 20, 35, 40, 50);
    assertThat(dist.getNthPercentile(5)).isEqualTo(15);
    assertThat(dist.getNthPercentile(30)).isEqualTo(20);
    assertThat(dist.getNthPercentile(40)).isEqualTo(20);
    assertThat(dist.getNthPercentile(50)).isEqualTo(35);
    assertThat(dist.getNthPercentile(100)).isEqualTo(50);

    dist = with(3, 6, 7, 8, 8, 10, 13, 15, 16, 20);
    assertThat(dist.getNthPercentile(25)).isEqualTo(7);
    assertThat(dist.getNthPercentile(50)).isEqualTo(8);
    assertThat(dist.getNthPercentile(75)).isEqualTo(15);
    assertThat(dist.getNthPercentile(100)).isEqualTo(20);

    dist = with(3, 6, 7, 8, 8, 9, 10, 13, 15, 16, 20);
    assertThat(dist.getNthPercentile(25)).isEqualTo(7);
    assertThat(dist.getNthPercentile(50)).isEqualTo(9);
    assertThat(dist.getNthPercentile(75)).isEqualTo(15);
    assertThat(dist.getNthPercentile(100)).isEqualTo(20);
  }

  private Distribution with(Integer... values) {
    int max = Collections.max(Arrays.asList(values));
    Distribution dist = new Distribution(max + 1);
    for (int value : values) {
      dist.record(value);
    }
    return dist;
  }
}
