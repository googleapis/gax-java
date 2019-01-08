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
package com.google.api.gax.tracing;

import com.google.api.core.InternalApi;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@InternalApi("For google-cloud-java client use only")
public final class OpencensusTracerFactory implements ApiTracerFactory {
  @Nonnull private final Tracer internalTracer;
  @Nullable private final String clientNameOverride;

  public OpencensusTracerFactory() {
    this(null);
  }

  public OpencensusTracerFactory(@Nullable String clientNameOverride) {
    this(Tracing.getTracer(), clientNameOverride);
  }

  @InternalApi("Visible for testing")
  OpencensusTracerFactory(Tracer internalTracer, @Nullable String clientNameOverride) {
    this.internalTracer = Preconditions.checkNotNull(internalTracer, "internalTracer can't be null");
    this.clientNameOverride = clientNameOverride;
  }

  @Override
  public ApiTracer newTracer(SpanName spanName) {
    Span span = internalTracer.spanBuilder(spanName.toString()).setRecordEvents(true).startSpan();

    return new OpencensusTracer(internalTracer, span);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OpencensusTracerFactory that = (OpencensusTracerFactory) o;
    return Objects.equal(internalTracer, that.internalTracer) &&
        Objects.equal(clientNameOverride, that.clientNameOverride);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(internalTracer, clientNameOverride);
  }
}