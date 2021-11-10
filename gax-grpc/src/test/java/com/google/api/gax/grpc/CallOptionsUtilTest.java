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
package com.google.api.gax.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import io.grpc.CallOptions;
import io.grpc.Metadata.Key;
import java.util.Map;
import org.junit.Test;

public class CallOptionsUtilTest {
  @Test
  public void testPutAndGetDynamicHeaderOption() {
    String encodedRequestParams = "param1=value&param2.param3=value23";
    CallOptions options =
        CallOptionsUtil.putRequestParamsDynamicHeaderOption(
            CallOptions.DEFAULT, encodedRequestParams);

    Map<Key<String>, String> headers = CallOptionsUtil.getDynamicHeadersOption(options);

    assertEquals(
        ImmutableMap.of(CallOptionsUtil.REQUEST_PARAMS_HEADER_KEY, encodedRequestParams), headers);
  }

  @Test
  public void testPutAndGetDynamicHeaderOptionEmpty() {
    CallOptions options =
        CallOptionsUtil.putRequestParamsDynamicHeaderOption(CallOptions.DEFAULT, "");
    assertSame(CallOptions.DEFAULT, options);
    Map<Key<String>, String> headers = CallOptionsUtil.getDynamicHeadersOption(options);
    assertTrue(headers.isEmpty());
  }

  @Test(expected = NullPointerException.class)
  public void testPutAndGetHeaderOptionNull() {
    CallOptionsUtil.putRequestParamsDynamicHeaderOption(CallOptions.DEFAULT, null);
  }
}
