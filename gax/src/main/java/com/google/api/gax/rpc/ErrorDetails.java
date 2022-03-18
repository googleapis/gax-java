/*
 * Copyright 2022 Google LLC
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
package com.google.api.gax.rpc;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.toprettystring.ToPrettyString;
import com.google.rpc.BadRequest;
import com.google.rpc.DebugInfo;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Help;
import com.google.rpc.LocalizedMessage;
import com.google.rpc.PreconditionFailure;
import com.google.rpc.QuotaFailure;
import com.google.rpc.RequestInfo;
import com.google.rpc.ResourceInfo;
import com.google.rpc.RetryInfo;
import javax.annotation.Nullable;

@AutoValue
public abstract class ErrorDetails {

  @Nullable
  public abstract ErrorInfo errorInfo();

  @Nullable
  public abstract RetryInfo retryInfo();

  @Nullable
  public abstract DebugInfo debugInfo();

  @Nullable
  public abstract QuotaFailure quotaFailure();

  @Nullable
  public abstract PreconditionFailure preconditionFailure();

  @Nullable
  public abstract BadRequest badRequest();

  @Nullable
  public abstract RequestInfo requestInfo();

  @Nullable
  public abstract ResourceInfo resourceInfo();

  @Nullable
  public abstract Help help();

  @Nullable
  public abstract LocalizedMessage localizedMessage();

  public static Builder builder() {
    return new AutoValue_ErrorDetails.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setErrorInfo(ErrorInfo errorInfo);

    public abstract Builder setRetryInfo(RetryInfo retryInfo);

    public abstract Builder setDebugInfo(DebugInfo debugInfo);

    public abstract Builder setQuotaFailure(QuotaFailure quotaFailure);

    public abstract Builder setPreconditionFailure(PreconditionFailure preconditionFailure);

    public abstract Builder setBadRequest(BadRequest badRequest);

    public abstract Builder setRequestInfo(RequestInfo requestInfo);

    public abstract Builder setResourceInfo(ResourceInfo resourceInfo);

    public abstract Builder setHelp(Help help);

    public abstract Builder setLocalizedMessage(LocalizedMessage localizedMessage);

    public abstract ErrorDetails build();
  }

  @ToPrettyString
  abstract String toPrettyString();
}
