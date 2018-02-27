/*
 * Copyright 2017 Google LLC
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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;

public class FixedHeaderProviderTest {
  @Test
  public void testCreateSuccess() {
    Map<String, String> headers =
        ImmutableMap.of("User-Agent", "hello1", "Custom-Header", "hello2");
    FixedHeaderProvider headerProvider = FixedHeaderProvider.create(headers);
    assertEquals(headers, headerProvider.getHeaders());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateFail() {
    Map<String, String> headers = ImmutableMap.of("User-Agent", "hello1", "user-agent", "hello2");
    FixedHeaderProvider.create(headers);
  }

  @Test
  public void testCreateVarargSuccess() {
    Map<String, String> headers =
        ImmutableMap.of("User-Agent", "hello1", "Custom-Header", "hello2");
    FixedHeaderProvider headerProvider =
        FixedHeaderProvider.create("User-Agent", "hello1", "Custom-Header", "hello2");
    assertEquals(headers, headerProvider.getHeaders());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateVarargFail() {
    FixedHeaderProvider.create("User-Agent", "hello1", "user-agent", "hello2");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateVarargOddNumberOfParamsFail() {
    FixedHeaderProvider.create("User-Agent", "hello1", "Custom-Header");
  }
}
