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

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * This class keeps the statistics about failed operations(both at RPC and ElementT level) in {@link
 * Batcher}. This provides the count of individual exception failure and count of each failed {@link
 * StatusCode.Code} occurred in the batching process.
 */
class BatcherStats {

  private final Map<Class, Integer> requestExceptionCounts = new ConcurrentHashMap<>();
  private final Map<StatusCode.Code, Integer> requestStatusCounts = new ConcurrentHashMap<>();
  private final AtomicInteger partialBatchFailures = new AtomicInteger(0);
  private final Map<Class, Integer> entryExceptionCounts = new ConcurrentHashMap<>();
  private final Map<StatusCode.Code, Integer> entryStatusCounts = new ConcurrentHashMap<>();

  private final Object lock = new Object();

  synchronized void recordBatchFailure(Throwable throwable) {
    Class exceptionClass = throwable.getClass();

    synchronized (lock) {
      if (throwable instanceof ApiException) {
        StatusCode.Code code = ((ApiException) throwable).getStatusCode().getCode();
        exceptionClass = ApiException.class;

        if (requestStatusCounts.containsKey(code)) {
          Integer codeCount = requestStatusCounts.get(code);
          requestStatusCounts.put(code, ++codeCount);

        } else {
          requestStatusCounts.put(code, 1);
        }
      }

      if (requestExceptionCounts.containsKey(exceptionClass)) {
        Integer exCount = requestExceptionCounts.get(exceptionClass);
        requestExceptionCounts.put(exceptionClass, ++exCount);
      } else {
        requestExceptionCounts.put(exceptionClass, 1);
      }
    }
  }

  <T> void recordBatchElementsCompletion(List<SettableApiFuture<T>> batchElementResultFutures) {
    final AtomicBoolean markBatchFailure = new AtomicBoolean();

    for (ApiFuture<T> elementResult : batchElementResultFutures) {
      ApiFutures.addCallback(
          elementResult,
          new ApiFutureCallback<T>() {
            @Override
            public void onFailure(Throwable throwable) {
              if (markBatchFailure.compareAndSet(false, true)) {
                partialBatchFailures.incrementAndGet();
              }
              Class exceptionClass = throwable.getClass();

              synchronized (lock) {
                if (throwable instanceof ApiException) {
                  StatusCode.Code code = ((ApiException) throwable).getStatusCode().getCode();
                  exceptionClass = ApiException.class;

                  if (entryStatusCounts.containsKey(code)) {
                    Integer statusCount = entryStatusCounts.get(code);
                    entryStatusCounts.put(code, ++statusCount);
                  } else {
                    entryStatusCounts.put(code, 1);
                  }
                }

                if (entryExceptionCounts.containsKey(exceptionClass)) {
                  Integer exCount = entryExceptionCounts.get(exceptionClass);
                  entryExceptionCounts.put(exceptionClass, ++exCount);
                } else {
                  entryExceptionCounts.put(exceptionClass, 1);
                }
              }
            }

            @Override
            public void onSuccess(T result) {}
          },
          directExecutor());
    }
  }

  /** Calculates and formats the message with request and entry failure count. */
  @Nullable
  BatchingException asException() {
    synchronized (lock) {
      int partialFailures = partialBatchFailures.get();
      if (requestExceptionCounts.isEmpty() && partialFailures == 0) {
        return null;
      }

      StringBuilder sb = new StringBuilder();
      int batchFailures = requestExceptionCounts.size();

      if (requestExceptionCounts.isEmpty()) {
        sb.append("Batching finished with ");
      } else {
        sb.append(String.format("%d batches failed to apply due to: ", batchFailures));

        for (Class request : requestExceptionCounts.keySet()) {
          sb.append(
              String.format(
                  "%d %s ", requestExceptionCounts.get(request), request.getSimpleName()));
          if (request.equals(ApiException.class)) {

            sb.append("(");
            for (StatusCode.Code statusCode : requestStatusCounts.keySet()) {
              sb.append(String.format("%d %s ", requestStatusCounts.get(statusCode), statusCode));
            }
            sb.append(") ");
          }
        }
        if (partialFailures > 0) {
          sb.append("and ");
        }
      }

      sb.append(String.format("%d partial failures.", partialFailures));
      if (partialFailures > 0) {
        int totalEntriesFailureCount = 0;
        for (Integer count : entryExceptionCounts.values()) {
          totalEntriesFailureCount += count;
        }

        sb.append(
            String.format(
                " The %d partial failures contained %d entries that failed with: ",
                partialFailures, totalEntriesFailureCount));

        for (Class entry : entryExceptionCounts.keySet()) {
          sb.append(
              String.format("%d %s ", entryExceptionCounts.get(entry), entry.getSimpleName()));
          if (entry.equals(ApiException.class)) {
            sb.append("(");
            for (StatusCode.Code code : entryStatusCounts.keySet()) {
              sb.append(String.format("%d %s ", entryStatusCounts.get(code), code));
            }
            sb.append(") ");
          }
        }
        sb.append(".");
      }
      return new BatchingException(sb.toString());
    }
  }
}
