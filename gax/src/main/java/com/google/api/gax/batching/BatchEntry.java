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

import com.google.api.core.BetaApi;
import com.google.api.core.InternalExtensionOnly;
import com.google.api.core.SettableApiFuture;
import java.util.List;

/**
 * This class contains the element and it's corresponding unresolved future, which would be resolved
 * when batch is {@link BatchingDescriptor#splitResponse(Object, List) successful} or {@link
 * BatchingDescriptor#splitException(Throwable, List) failed}.
 *
 * @param <ElementT> The type of each individual element to be batched.
 * @param <ElementResultT> The type of the result for each individual element.
 */
@BetaApi("The surface for batching is not stable yet and may change in the resultFuture.")
@InternalExtensionOnly("For google-cloud-java client use only.")
public class BatchEntry<ElementT, ElementResultT> {

  private final ElementT element;
  private final SettableApiFuture<ElementResultT> resultFuture;

  private BatchEntry(ElementT element, SettableApiFuture<ElementResultT> resultFuture) {
    this.element = element;
    this.resultFuture = resultFuture;
  }

  static <ElementT, ElementResultT> BatchEntry<ElementT, ElementResultT> add(
      ElementT element, SettableApiFuture<ElementResultT> future) {
    return new BatchEntry<>(element, future);
  }

  public ElementT getElement() {
    return element;
  }

  public SettableApiFuture<ElementResultT> getResultFuture() {
    return resultFuture;
  }
}
