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

import com.google.api.core.ApiFuture;

/**
 * This interface exposes operations for performing batching on various clients.
 *
 * <p>For example, In case of:
 *
 * <pre>{@code
 * Bigtable:
 *     Batcher<MutateRowsRequest.Entry, MutateRowsResponse.Entry>
 * PubSub:
 *     Batcher<String, String>
 * Logging:
 *     Batcher<LogEntry, Void>
 * }</pre>
 *
 * @param <ElementT> Type for which this class performs batching.
 * @param <ResultT> Response type of a entry object.
 */
public interface Batcher<ElementT, ResultT> extends AutoCloseable {

  /**
   * Accepts a single {@link ElementT} object and queues up elements until either a duration of
   * maxDelay has passed or any threshold in a given set of thresholds is breached.
   *
   * @param entry an {@link ElementT} object.
   * @return Returns an ApiFuture that resolves once the batch has been processed by the batch
   *     receiver and the flow controller resources have been released.
   *     <p>Note: Cancelling this simply marks the future cancelled, It would not stop the RPC.
   */
  ApiFuture<ResultT> add(ElementT entry);

  /**
   * Flushes any pending asynchronous elements. Logs are automatically flushed based on time,
   * element and byte count threshold that be configured via {@link
   * com.google.api.gax.batching.BatchingSettings}.
   *
   * <p>Note: This is a blocking operation.
   */
  void flush() throws InterruptedException;

  /**
   * Prevents new elements from being added, flushes the existing elements and waits for all of them
   * to finish.
   */
  @Override
  void close() throws InterruptedException;
}
