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

import com.google.api.core.BetaApi;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Preconditions;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Takes measurements and stores them in linear buckets from 0 to totalBuckets - 1, along with
 * utilities to calculate percentiles for analysis of results.
 */
@BetaApi
public class Distribution {

  private final AtomicLong[] bucketCounts;
  private long count;
  private double mean;
  private double sumOfSquaredDeviation;

  public Distribution(int totalBuckets) {
    Preconditions.checkArgument(totalBuckets > 0);
    bucketCounts = new AtomicLong[totalBuckets];
    for (int i = 0; i < totalBuckets; ++i) {
      bucketCounts[i] = new AtomicLong();
    }
  }

  /** Get the bucket that records values up to the given percentile. */
  public long getNthPercentile(double percentile) {
    Preconditions.checkArgument(percentile > 0.0);
    Preconditions.checkArgument(percentile <= 100.0);

    long[] bucketCounts = getBucketCounts();
    long total = 0;
    for (long count : bucketCounts) {
      total += count;
    }

    if (total == 0) {
      return 0;
    }
    long count = (long) Math.ceil(total * percentile / 100.0);
    for (int i = 0; i < bucketCounts.length; i++) {
      count -= bucketCounts[i];
      if (count <= 0) {
        return i;
      }
    }
    return 0;
  }

  /** Resets (sets to 0) the recorded values. */
  public synchronized void reset() {
    for (AtomicLong element : bucketCounts) {
      element.set(0);
    }
    count = 0;
    mean = 0;
    sumOfSquaredDeviation = 0;
  }

  /** Numbers of values recorded. */
  public long getCount() {
    return count;
  }

  /** Square deviations of the recorded values. */
  public double getSumOfSquareDeviations() {
    return sumOfSquaredDeviation;
  }

  /** Mean of the recorded values. */
  public double getMean() {
    return mean;
  }

  /** Gets the accumulated count of every bucket of the distribution. */
  public long[] getBucketCounts() {
    long[] counts = new long[bucketCounts.length];
    for (int i = 0; i < counts.length; i++) {
      counts[i] = bucketCounts[i].longValue();
    }
    return counts;
  }

  /** Make a copy of the distribution. */
  public synchronized Distribution copy() {
    Distribution distributionCopy = new Distribution(bucketCounts.length);
    distributionCopy.count = count;
    distributionCopy.mean = mean;
    distributionCopy.sumOfSquaredDeviation = sumOfSquaredDeviation;
    for (int i = 0; i < bucketCounts.length; i++) {
      distributionCopy.bucketCounts[i].set(bucketCounts[i].get());
    }
    return distributionCopy;
  }

  /** Record a new value. */
  public void record(int bucket) {
    Preconditions.checkArgument(bucket >= 0);

    synchronized (this) {
      count++;
      double dev = bucket - mean;
      mean += dev / count;
      sumOfSquaredDeviation += dev * (bucket - mean);
    }

    if (bucket >= bucketCounts.length) {
      // Account for bucket overflow, records everything that is equals or greater of the last
      // bucket.
      bucketCounts[bucketCounts.length - 1].incrementAndGet();
      return;
    }

    bucketCounts[bucket].incrementAndGet();
  }

  @Override
  public String toString() {
    ToStringHelper helper = MoreObjects.toStringHelper(Distribution.class);
    helper.add("bucketCounts", bucketCounts);
    helper.add("count", count);
    helper.add("mean", mean);
    helper.add("sumOfSquaredDeviation", sumOfSquaredDeviation);
    return helper.toString();
  }
}
