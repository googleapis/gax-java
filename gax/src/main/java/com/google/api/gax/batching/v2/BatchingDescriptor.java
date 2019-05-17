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

import com.google.api.core.SettableApiFuture;
import java.util.List;

/**
 * An adapter that packs and unpacks the elements in and out of batch requests and responses.
 *
 * <p>This interface should be implemented by either a service specific client or autogenerated by
 * gapic-generator.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * class ListBatchingDescriptor implements BatchingDescriptor<String, String, List<String>,
 * List<String>> {
 *
 *   RequestBuilder<String, List<String>> newRequestBuilder(List<String> prototype) {
 *     return new RequestBuilder<String, List<String>>() {
 *       List<String> list = prototype.clone();
 *
 *       void add(String element) {
 *         list.add(element);
 *       }
 *
 *       List<String> build() {
 *         return list.clone();
 *       }
 *     };
 *   }
 *
 *   void splitResponse(List<String> batchResponse, List<SettableApiFuture<String>> batch) {
 *     for (int i = 0; i < batchResponse.size(); i++) {
 *       batch.get(i).set(batchResponse.get(i);
 *     }
 *   }
 *
 *   void splitException(Throwable throwable, List<SettableApiFuture<String>> batch) {
 *     for (SettableApiFuture<String> result : batch) {
 *       result.setException(throwable);
 *     }
 *   }
 *
 *   long countBytes(String element) {
 *     return element.length();
 *   }
 * }
 * }</pre>
 */
public interface BatchingDescriptor<ElementT, ResultT, RequestT, ResponseT> {

  /**
   * Creates a new wrapper for the underlying request builder. It's used to pack the current batch
   * request with elements.
   */
  RequestBuilder<ElementT, RequestT> newRequestBuilder(RequestT prototype);

  /** Unpacks the batch response into individual elements results. */
  void splitResponse(ResponseT batchResponse, List<SettableApiFuture<ResultT>> batch);

  /** Unpacks the batch response error into individual element errors. */
  void splitException(Throwable throwable, List<SettableApiFuture<ResultT>> batch);

  /** Returns the size of the passed in element object in bytes. */
  long countBytes(ElementT element);
}
