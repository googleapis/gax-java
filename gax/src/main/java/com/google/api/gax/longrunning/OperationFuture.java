/*
 * Copyright 2017 Google LLC
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
package com.google.api.gax.longrunning;

import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.core.InternalApi;
import com.google.api.gax.retrying.RetryingFuture;
import java.util.concurrent.ExecutionException;

/**
 * An ApiFuture which tracks polling of a service. The polling is done periodically, based on the
 * {@link com.google.api.gax.retrying.TimedRetryAlgorithm}.
 *
 * <p>Implementations are expected to be thread-safe.
 */
@BetaApi("The surface for long-running operations is not stable yet and may change in the future.")
public interface OperationFuture<ResponseT, MetadataT> extends ApiFuture<ResponseT> {
  /**
   * Gets the metadata of the operation tracked by this {@link OperationFuture}. This method returns
   * the current poll metadata result or the initial call metadata if no poll has completed yet. The
   * returned future completes immediately after the initial call has completed.
   *
   * <p>Note, some APIs may return {@code null} in metadata response message. In such cases this
   * method may return a non-null future whose {@code get()} method will return the initial call
   * metadata. This behavior is API specific an should be considered a valid case, which indicates
   * that the recent poll request has completed, but no specific metadata was provided by the
   * server. (i.e. most probably providing metadata for an intermediate result is not supported by
   * the server).
   *
   * <p>If this operation future is completed, this method always returns the metadata from the last
   * poll request (which completed the operation future).
   *
   * <p>If this operation future failed, this method may (depending on the failure type) return a
   * non-failing future, representing the metadata from the last poll request (which failed the
   * operation future).
   *
   * <p>If this operation future was cancelled, this method returns a canceled metatata future as
   * well.
   */
  ApiFuture<MetadataT> getMetadata();

  /**
   * Returns the value of the name of the operation from the initial operation object returned from
   * the initial call to start the operation. Blocks if the initial call to start the operation
   * hasn't returned yet.
   */
  String getName() throws InterruptedException, ExecutionException;

  // INTERNAL ONLY METHODS BELOW
  /**
   * Returns the {@link OperationSnapshot} future of the initial request which started this {@code
   * OperationFuture}.
   */
  @InternalApi
  ApiFuture<OperationSnapshot> getInitialFuture();

  /** Returns the {@link RetryingFuture} which continues to poll {@link OperationSnapshot}. */
  @InternalApi
  RetryingFuture<OperationSnapshot> getPollingFuture();

  /**
   * DEPRECATED: Subsumed by getMetadata. Calling getMetadata.isDone() will return false if the
   * initial future has not yet completed.
   *
   * <p>Peeks at the metadata of the operation tracked by this {@link OperationFuture}. If the
   * initial future hasn't completed yet this method returns {@code null}, otherwise it returns the
   * latest metadata returned from the server (i.e. either initial call metadata or the metadata
   * received from the latest completed poll iteration).
   *
   * <p>If not {@code null}, the returned result is guaranteed to be an already completed future, so
   * {@link ApiFuture#isDone()} will always be {@code true} and {@link ApiFuture#get()} will always
   * be non-blocking.
   *
   * <p>Note, some APIs may return {@code null} in metadata response message. In such cases this
   * method may return a non-null future whose {@code get()} method will return {@code null}. This
   * behavior is API specific an should be considered a valid case, which indicates that the recent
   * poll request has completed, but no specific metadata was provided by the server (i.e. most
   * probably providing metadata for an intermediate result is not supported by the server).
   *
   * <p>This method should be used to check operation progress without blocking current thread.
   * Since this method returns metadata from the latest completed poll, it is potentially slightly
   * stale compared to the most recent data. To get the most recent data and/or get notified when
   * the current scheduled poll request completes use the {@link #getMetadata()} method instead.
   *
   * <p>If this operation future is completed, this method always returns the metadata from the last
   * poll request (which completed the operation future).
   *
   * <p>If this operation future failed, this method may (depending on the failure type) return a
   * non-failing future, representing the metadata from the last poll request (which failed the
   * operation future).
   *
   * <p>If this operation future was cancelled, this method returns a canceled metatata future as
   * well.
   *
   * <p>In general this method behaves similarly to {@link RetryingFuture#peekAttemptResult()}.
   */
  @Deprecated
  ApiFuture<MetadataT> peekMetadata();
}
