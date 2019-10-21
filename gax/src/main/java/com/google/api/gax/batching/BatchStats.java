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
 * in the documentation
 * /or other materials provided with the
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

import com.google.api.core.ApiFutureCallback;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * This class keeps the statistics about failed operations(both at RPC and ElementT level) in {@link
 * Batcher}. This provides the count of individual exception failure and count of each failed {@link
 * StatusCode.Code} occurred in the batching process.
 */
class BatchStats {

  private final Map<Class, AtomicInteger> requestExceptionCounts = new ConcurrentHashMap<>();
  private final Map<StatusCode.Code, AtomicInteger> requestStatusCounts = new ConcurrentHashMap<>();
  private final AtomicInteger partialBatchFailures = new AtomicInteger(0);
  private final Map<Class, AtomicInteger> entryExceptionCounts = new ConcurrentHashMap<>();
  private final Map<StatusCode.Code, AtomicInteger> entryStatusCounts = new ConcurrentHashMap<>();

  private final Object errorLock = new Object();
  private final Object statusLock = new Object();

  <T> ApiFutureCallback<T> getRequestCallback() {
    return new ApiFutureCallback<T>() {
      public void onFailure(Throwable t) {
        recordRequestException(t);
      }

      @Override
      public void onSuccess(T result) {}
    };
  }

  <T> ApiFutureCallback<T> getEntryCallback() {
    return new ApiFutureCallback<T>() {
      public void onFailure(Throwable t) {
        recordEntryException(t);
      }

      @Override
      public void onSuccess(T result) {}
    };
  }

  private void recordRequestException(Throwable throwable) {
    Class exceptionClass = throwable.getClass();

    if (throwable instanceof ApiException) {
      StatusCode.Code code = ((ApiException) throwable).getStatusCode().getCode();
      exceptionClass = ApiException.class;

      synchronized (statusLock) {
        if (requestStatusCounts.containsKey(code)) {
          requestStatusCounts.get(code).incrementAndGet();
        } else {
          requestStatusCounts.put(code, new AtomicInteger(1));
        }
      }
    }

    synchronized (errorLock) {
      if (requestExceptionCounts.containsKey(exceptionClass)) {
        requestExceptionCounts.get(exceptionClass).incrementAndGet();
      } else {
        synchronized (errorLock) {
          requestExceptionCounts.put(exceptionClass, new AtomicInteger(1));
        }
      }
    }
  }

  private void recordEntryException(Throwable throwable) {
    Class exceptionClass = throwable.getClass();

    if (throwable instanceof ApiException) {
      StatusCode.Code code = ((ApiException) throwable).getStatusCode().getCode();
      exceptionClass = ApiException.class;

      synchronized (statusLock) {
        if (entryStatusCounts.containsKey(code)) {
          entryStatusCounts.get(code).incrementAndGet();
        } else {
          entryStatusCounts.put(code, new AtomicInteger(1));
        }
      }
    }

    synchronized (errorLock) {
      if (entryExceptionCounts.containsKey(exceptionClass)) {
        entryExceptionCounts.get(exceptionClass).incrementAndGet();
      } else {
        partialBatchFailures.incrementAndGet();
        entryExceptionCounts.put(exceptionClass, new AtomicInteger(1));
      }
    }
  }

  /** Calculates and formats the message with request and entry failure count. */
  @Nullable
  BatchingException asException() {
    if (requestExceptionCounts.isEmpty() && partialBatchFailures.get() == 0) {
      return null;
    }

    StringBuilder sb = new StringBuilder();
    int batchFailures = requestExceptionCounts.size();

    if (requestExceptionCounts.isEmpty()) {
      sb.append("Batching finished with ");
    } else {
      sb.append(String.format("%d batches failed to apply due to: ", batchFailures));

      // compose the exception and return it
      for (Class req : requestExceptionCounts.keySet()) {
        sb.append(
            String.format("%d %s ", requestExceptionCounts.get(req).get(), req.getSimpleName()));
        if (req.equals(ApiException.class)) {
          sb.append("(");
          for (StatusCode.Code statusCode : requestStatusCounts.keySet()) {
            sb.append(
                String.format("%d %s ", requestStatusCounts.get(statusCode).get(), statusCode));
          }
          sb.append(") ");
        }
      }
    }

    if (partialBatchFailures.get() > 0) {
      sb.append(String.format("%d partial failures.", partialBatchFailures.get()));

      int totalEntriesEx = 0;
      for (AtomicInteger ai : entryExceptionCounts.values()) {
        totalEntriesEx += ai.get();
      }

      sb.append(
          String.format(
              " The %d partial failures contained %d entries that failed with: ",
              partialBatchFailures.get(), totalEntriesEx));

      for (Class entry : entryExceptionCounts.keySet()) {
        sb.append(
            String.format("%d %s ", entryExceptionCounts.get(entry).get(), entry.getSimpleName()));
        if (entry.equals(ApiException.class)) {
          sb.append("(");
          for (StatusCode.Code code : entryStatusCounts.keySet()) {
            sb.append(String.format("%d %s ", entryStatusCounts.get(code).get(), code));
          }
          sb.append(") ");
        }
      }
    }
    sb.append(".");
    return new BatchingException(sb.toString());
  }
}
