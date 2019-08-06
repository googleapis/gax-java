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

/**
 * An extension of FlowController that keep tracks of the number of permits and calls to reserve and
 * release.
 */
public class TrackedFlowController extends FlowController {
  private long elementsReserved = 0;
  private long elementsReleased = 0;
  private long bytesReserved = 0;
  private long bytesReleased = 0;
  private int batchFinished = 0;

  public TrackedFlowController(FlowControlSettings settings) {
    super(settings);
  }

  @Override
  public void reserve(long bytes) throws FlowControlException {
    this.elementsReserved++;
    this.bytesReserved += bytes;
    super.reserve(bytes);
  }

  @Override
  public void release(int elementsReleased, long bytes) {
    this.elementsReleased += elementsReleased;
    this.bytesReleased += bytes;
    this.batchFinished++;
    super.release(elementsReleased, bytes);
  }

  long getElementsReserved() {
    return elementsReserved;
  }

  long getElementsReleased() {
    return elementsReleased;
  }

  long getBytesReserved() {
    return bytesReserved;
  }

  long getBytesReleased() {
    return bytesReleased;
  }

  int getBatchFinished() {
    return batchFinished;
  }
}
