/*
 * Copyright 2018 Google LLC
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

@InternalApi
public class Headers {
  public static ImmutableMap<String, List<String>> mergeHeaders(
      Map<String, List<String>> firstHeader, Map<String, List<String>> secondHeader) {
    ImmutableMap.Builder<String, List<String>> headerBuilder = ImmutableMap.builder();
    for (Map.Entry<String, List<String>> entry : firstHeader.entrySet()) {
      String key = entry.getKey();
      List<String> firstValue = entry.getValue();
      List<String> secondValue = secondHeader.get(key);
      ImmutableList.Builder<String> mergedValueBuilder = ImmutableList.builder();
      mergedValueBuilder.addAll(firstValue);
      if (secondValue != null) {
        mergedValueBuilder.addAll(secondValue);
      }
      headerBuilder.put(key, mergedValueBuilder.build());
    }
    for (Map.Entry<String, List<String>> entry : secondHeader.entrySet()) {
      String key = entry.getKey();
      if (!firstHeader.containsKey(key)) {
        ImmutableList.Builder<String> mergedValueBuilder = ImmutableList.builder();
        headerBuilder.put(key, mergedValueBuilder.addAll(entry.getValue()).build());
      }
    }
    return headerBuilder.build();
  }
}
