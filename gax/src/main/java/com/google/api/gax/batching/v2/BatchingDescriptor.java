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
 * Interface which represents an object that transforms entry/result data for the purposes of
 * batching.
 *
 * <p>Implementations of BatchingDescriptor must guarantee that all methods are stateless and thread
 * safe. It should work with the generated code.
 */
public interface BatchingDescriptor<EntryT, EntryResultT, RequestT, ResponseT> {

  /** Returns a new RequestBuilder object with the type <EntryT, RequestT>. */
  RequestBuilder<EntryT, RequestT> newRequestBuilder(RequestT prototype);

  /**
   * Splits the response from an RPC into respective future of {@link EntryResultT} to mark them
   * resolved.
   */
  void splitResponse(ResponseT batchResponse, List<SettableApiFuture<EntryResultT>> batch);

  /** Marks the future of {@link EntryResultT} with exception received while perform batching. */
  void splitException(Throwable throwable, List<SettableApiFuture<EntryResultT>> batch);

  /**
   * Returns total bytes of a single entry object, which would be used for ElementCount Threshold.
   */
  long countBytes(EntryT entry);
}
