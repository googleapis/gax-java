/*
 * Copyright 2017, Google Inc. All rights reserved.
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
package com.google.api.gax.bundling;

import com.google.api.gax.core.FlowController;
import com.google.api.gax.core.FlowController.FlowControlException;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

/** Wraps a {@link FlowController} for use by Bundling. */
public class BundlingFlowController<T> {

  private final FlowController flowController;
  private final ElementCounter<T> elementCounter;
  private final ElementCounter<T> byteCounter;

  public BundlingFlowController(
      FlowController flowController,
      ElementCounter<T> elementCounter,
      ElementCounter<T> byteCounter) {
    this.flowController = flowController;
    this.elementCounter = elementCounter;
    this.byteCounter = byteCounter;
  }

  public void reserve(T bundle) throws FlowControlException {
    Preconditions.checkNotNull(bundle);
    int elements = Ints.checkedCast(elementCounter.count(bundle));
    int bytes = Ints.checkedCast(byteCounter.count(bundle));
    flowController.reserve(elements, bytes);
  }

  public void release(T bundle) {
    Preconditions.checkNotNull(bundle);
    int elements = Ints.checkedCast(elementCounter.count(bundle));
    int bytes = Ints.checkedCast(byteCounter.count(bundle));
    flowController.release(elements, bytes);
  }
}
