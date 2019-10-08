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
package com.google.api.gax.batching;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents the number of failed exceptions while performing Batching. It also provides
 * the count of exceptions types and count of each failed statusCodes occurred in the Batching
 * process.
 */
public class BatchingException extends RuntimeException {

  private final long numOfFailure;
  private final Map<Class, AtomicInteger> exceptionCount;
  private final Map<StatusCode, AtomicInteger> statusCodeCount;

  BatchingException(
      long numOfFailure,
      Map<Class, AtomicInteger> exceptionCount,
      Map<StatusCode, AtomicInteger> statusCodeCount) {
    this.numOfFailure = numOfFailure;
    this.exceptionCount = exceptionCount;
    this.statusCodeCount = statusCodeCount;
  }

  public long getTotalFailureCount() {
    return numOfFailure;
  }

  public Map<Class, AtomicInteger> getFailureTypesCount() {
    return exceptionCount;
  }

  public Map<StatusCode, AtomicInteger> getFailureStatusCodeCount() {
    return statusCodeCount;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("Failed to commit ")
        .append(numOfFailure)
        .append(" mutations\n")
        .append("Mutations failed for Exception types: ")
        .append(exceptionCount.entrySet())
        .append("\n");

    if (!exceptionCount.isEmpty()) {
      sb.append("Total ApiException failure are: ")
          .append(exceptionCount.get(ApiException.class))
          .append(" with Status Code as: ")
          .append(statusCodeCount.entrySet());
    }
    return sb.toString();
  }
}
