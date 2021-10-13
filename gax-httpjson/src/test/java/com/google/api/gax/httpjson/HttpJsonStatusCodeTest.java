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
package com.google.api.gax.httpjson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.api.gax.rpc.StatusCode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class HttpJsonStatusCodeTest {

  @Test
  public void rpcCodeToStatusCodeTest() {
    Set<StatusCode.Code> allCodes = new HashSet<>();
    for (com.google.rpc.Code rpcCode : com.google.rpc.Code.values()) {
      StatusCode.Code statusCode;
      try {
        statusCode = HttpJsonStatusCode.rpcCodeToStatusCode(rpcCode);
      } catch (IllegalArgumentException e) {
        if (rpcCode != com.google.rpc.Code.UNRECOGNIZED) {
          fail("Unrecognized com.google.rpc.Code found " + rpcCode);
        }
        continue;
      }

      assertNotNull(statusCode);
      allCodes.add(statusCode);
    }

    assertEquals(allCodes, new HashSet<>(Arrays.asList(StatusCode.Code.values())));
  }

  @Test
  public void httpStatusToStatusCodeTest() {
    // The HTTP status code conversion logic is currently in the process of being standardized,
    // the tested logic may change in nearest future.
    final String defaultMessage = "anything";
    assertEquals(
        StatusCode.Code.OK, HttpJsonStatusCode.httpStatusToStatusCode(200, defaultMessage));
    assertEquals(
        StatusCode.Code.OUT_OF_RANGE,
        HttpJsonStatusCode.httpStatusToStatusCode(400, HttpJsonStatusCode.OUT_OF_RANGE));
    assertEquals(
        StatusCode.Code.FAILED_PRECONDITION,
        HttpJsonStatusCode.httpStatusToStatusCode(400, HttpJsonStatusCode.FAILED_PRECONDITION));
    assertEquals(
        StatusCode.Code.INVALID_ARGUMENT,
        HttpJsonStatusCode.httpStatusToStatusCode(400, defaultMessage));
    assertEquals(
        StatusCode.Code.UNAUTHENTICATED,
        HttpJsonStatusCode.httpStatusToStatusCode(401, defaultMessage));
    assertEquals(
        StatusCode.Code.PERMISSION_DENIED,
        HttpJsonStatusCode.httpStatusToStatusCode(403, defaultMessage));
    assertEquals(
        StatusCode.Code.NOT_FOUND, HttpJsonStatusCode.httpStatusToStatusCode(404, defaultMessage));
    assertEquals(
        StatusCode.Code.ALREADY_EXISTS,
        HttpJsonStatusCode.httpStatusToStatusCode(409, HttpJsonStatusCode.ALREADY_EXISTS));
    assertEquals(
        StatusCode.Code.ABORTED, HttpJsonStatusCode.httpStatusToStatusCode(409, defaultMessage));
    assertEquals(
        StatusCode.Code.RESOURCE_EXHAUSTED,
        HttpJsonStatusCode.httpStatusToStatusCode(429, defaultMessage));
    assertEquals(
        StatusCode.Code.CANCELLED, HttpJsonStatusCode.httpStatusToStatusCode(499, defaultMessage));
    assertEquals(
        StatusCode.Code.DATA_LOSS,
        HttpJsonStatusCode.httpStatusToStatusCode(500, HttpJsonStatusCode.DATA_LOSS));
    assertEquals(
        StatusCode.Code.UNKNOWN,
        HttpJsonStatusCode.httpStatusToStatusCode(500, HttpJsonStatusCode.UNKNOWN));
    assertEquals(
        StatusCode.Code.INTERNAL, HttpJsonStatusCode.httpStatusToStatusCode(500, defaultMessage));
    assertEquals(
        StatusCode.Code.UNIMPLEMENTED,
        HttpJsonStatusCode.httpStatusToStatusCode(501, defaultMessage));
    assertEquals(
        StatusCode.Code.UNAVAILABLE,
        HttpJsonStatusCode.httpStatusToStatusCode(503, defaultMessage));
    assertEquals(
        StatusCode.Code.DEADLINE_EXCEEDED,
        HttpJsonStatusCode.httpStatusToStatusCode(504, defaultMessage));

    try {
      HttpJsonStatusCode.httpStatusToStatusCode(411, defaultMessage);
      fail();
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      HttpJsonStatusCode.httpStatusToStatusCode(666, defaultMessage);
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
}
