/*
 * Copyright 2017, Google LLC All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.mockito.Mockito;

public class RequestUrlParamsEncoderTest {

  @Test
  public void testEncodeValidationSuccess() throws Exception {
    @SuppressWarnings("unchecked")
    RequestParamsExtractor<Integer> extractor =
        getMockExtractor(2, ImmutableMap.of("param1", "value+1", "param2", "value+2+%26"));

    RequestUrlParamsEncoder<Integer> encoder = new RequestUrlParamsEncoder<>(extractor, true);
    String encodedParams = encoder.encode(2);

    assertEquals("param1=value+1&param2=value+2+%26", encodedParams);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEncodeValidationFail() throws Exception {
    RequestParamsExtractor<Integer> extractor =
        getMockExtractor(1, ImmutableMap.of("param1", "value+1", "param2", "value+2+&"));
    RequestUrlParamsEncoder<Integer> encoder = new RequestUrlParamsEncoder<>(extractor, true);
    encoder.encode(1);
  }

  @Test
  public void testEncodeNoValidationSuccess() throws Exception {
    RequestParamsExtractor<Integer> extractor =
        getMockExtractor(1, ImmutableMap.of("param1", "value+1", "param2", "value+2+&"));
    RequestUrlParamsEncoder<Integer> encoder = new RequestUrlParamsEncoder<>(extractor, false);
    String encodedParams = encoder.encode(1);

    assertEquals("param1=value+1&param2=value+2+&", encodedParams);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEncodeNullName() throws Exception {
    RequestParamsExtractor<Integer> extractor =
        getMockExtractor(1, Collections.singletonMap((String) null, "value1"));
    RequestUrlParamsEncoder<Integer> encoder = new RequestUrlParamsEncoder<>(extractor, false);
    encoder.encode(1);
  }

  @Test
  public void testEncodeNullValue() throws Exception {
    RequestParamsExtractor<Integer> extractor =
        getMockExtractor(1, Collections.singletonMap("param1", (String) null));
    RequestUrlParamsEncoder<Integer> encoder = new RequestUrlParamsEncoder<>(extractor, false);
    String encodedParams = encoder.encode(1);
    assertEquals("", encodedParams);
  }

  @Test
  public void testEncodeEmptyValue() throws Exception {
    RequestParamsExtractor<Integer> extractor =
        getMockExtractor(1, Collections.singletonMap("param1", ""));
    RequestUrlParamsEncoder<Integer> encoder = new RequestUrlParamsEncoder<>(extractor, false);
    String encodedParams = encoder.encode(1);
    assertEquals("param1=", encodedParams);
  }

  @Test
  public void testEncodeNullAndEmptyParams() throws Exception {
    RequestParamsExtractor<Integer> extractor =
        getMockExtractor(1, Collections.<String, String>emptyMap());
    RequestUrlParamsEncoder<Integer> encoder = new RequestUrlParamsEncoder<>(extractor, false);
    String encodedParams = encoder.encode(1);
    assertEquals("", encodedParams);

    extractor = getMockExtractor(1, null);
    encoder = new RequestUrlParamsEncoder<>(extractor, false);
    NullPointerException exception = null;
    try {
      encoder.encode(1);
    } catch (NullPointerException e) {
      exception = e;
    }
    assertNotNull(exception);
  }

  private RequestParamsExtractor<Integer> getMockExtractor(
      Integer input, Map<String, String> output) {
    @SuppressWarnings("unchecked")
    RequestParamsExtractor<Integer> extractor =
        (RequestParamsExtractor<Integer>) Mockito.mock(RequestParamsExtractor.class);
    when(extractor.extract(input)).thenReturn(output);
    return extractor;
  }
}
