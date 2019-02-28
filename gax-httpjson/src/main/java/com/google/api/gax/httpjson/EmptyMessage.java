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
package com.google.api.gax.httpjson;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A generic empty message that can be re-used to avoid defining duplicated empty messages in your
 * APIs. A typical example is to use it as the response type of an API method.
 */
public class EmptyMessage implements ApiMessage {

  @Nullable
  @Override
  /* Overriden method. Will always return null, because there are no fields in this message type. */
  public Object getFieldValue(String fieldName) {
    return null;
  }

  @Nullable
  @Override
  /* Overriden method. Will always return null, because there are no fields in this message type. */
  public List<String> getFieldMask() {
    return null;
  }

  @Nullable
  @Override
  /* Overriden method. Will always return null, because there are no fields in this message type. */
  public ApiMessage getApiMessageRequestBody() {
    return null;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof EmptyMessage;
  }

  // This is a singleton class.
  private static final EmptyMessage DEFAULT_INSTANCE = new EmptyMessage();

  public static EmptyMessage getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private EmptyMessage() {}

  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }

  public static Builder newBuilder(EmptyMessage prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }

  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this);
  }

  public static class Builder {
    Builder() {}

    public Builder mergeFrom(EmptyMessage other) {
      return this;
    }

    public Builder(EmptyMessage other) {}

    public EmptyMessage build() {
      return DEFAULT_INSTANCE;
    }

    public Builder clone() {
      return new Builder();
    }
  }

  @Override
  public String toString() {
    return "EmptyMessage{}";
  }

  @Override
  public int hashCode() {
    return Objects.hash(DEFAULT_INSTANCE);
  }
}
