/*
 * Copyright 2021 Google LLC
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
package com.google.api.gax.rpc.internal;

import com.google.api.core.InternalApi;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiCallContext.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/** ApiCallCustomContexts encapsulates custom context data to pass in a {@link ApiCallContext} */
@InternalApi
public final class ApiCallCustomContexts {

  private final ImmutableMap<Key, Object> customContexts;

  private ApiCallCustomContexts(ImmutableMap<Key, Object> contexts) {
    this.customContexts = Preconditions.checkNotNull(contexts);
  }

  /** Create an empty custom call context. */
  public static ApiCallCustomContexts createDefault() {
    return new ApiCallCustomContexts(ImmutableMap.<Key, Object>of());
  }

  /** Add a context to custom contexts. Any existing value of the key is overwritten. */
  public <T> ApiCallCustomContexts withCustomContext(Key<T> key, T context) {
    Preconditions.checkNotNull(key);
    Preconditions.checkNotNull(context);
    ImmutableMap.Builder builder = ImmutableMap.<Key, Object>builder();
    if (!customContexts.containsKey(key)) {
      builder.putAll(customContexts).put(key, context);
    } else {
      for (Key oldKey : customContexts.keySet()) {
        if (oldKey.equals(key)) {
          builder.put(oldKey, context);
        } else {
          builder.put(oldKey, customContexts.get(oldKey));
        }
      }
    }
    return new ApiCallCustomContexts(builder.build());
  }

  /** Get a custom context. */
  public <T> T getCustomContext(Key<T> key) {
    Preconditions.checkNotNull(key);
    if (!customContexts.containsKey(key)) {
      return key.getDefault();
    }
    return (T) customContexts.get(key);
  }

  /**
   * Merge new custom contexts into existing custom contexts. Any existing values of the keys are
   * overwritten.
   */
  public ApiCallCustomContexts merge(ApiCallCustomContexts newCustomContexts) {
    Preconditions.checkNotNull(newCustomContexts);
    ImmutableMap.Builder builder = ImmutableMap.<Key, Object>builder();
    for (Key key : customContexts.keySet()) {
      Object oldValue = customContexts.get(key);
      Object newValue = newCustomContexts.customContexts.get(key);
      builder.put(key, newValue != null ? newValue : oldValue);
    }
    for (Key key : newCustomContexts.customContexts.keySet()) {
      if (!customContexts.containsKey(key)) {
        builder.put(key, newCustomContexts.customContexts.get(key));
      }
    }
    return new ApiCallCustomContexts(builder.build());
  }
}
