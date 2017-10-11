/*
 * Copyright 2017, Google Inc. All rights reserved.
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
 *     * Neither the name of Google Inc. nor the names of its
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

import com.google.api.core.BetaApi;
import com.google.api.gax.rpc.StatusCode;
import com.google.common.base.Strings;
import java.util.Objects;

/** A failure code specific to an HTTP call. */
@BetaApi
public class HttpJsonStatusCode implements StatusCode {
  static final String FAILED_PRECONDITION = "FAILED_PRECONDITION";
  static final String OUT_OF_RANGE = "OUT_OF_RANGE";
  static final String ALREADY_EXISTS = "ALREADY_EXISTS";
  static final String DATA_LOSS = "DATA_LOSS";
  static final String UNKNOWN = "UNKNOWN";

  private final int httpStatus;
  private final StatusCode.Code statusCode;

  /** Creates a new instance with the given status code. */
  public static HttpJsonStatusCode of(int httpStatus, String errorMessage) {
    return new HttpJsonStatusCode(httpStatus, httpStatusToStatusCode(httpStatus, errorMessage));
  }

  public static HttpJsonStatusCode of(StatusCode.Code statusCode) {
    return new HttpJsonStatusCode(statusCodeToHttpStatus(statusCode), statusCode);
  }

  private static StatusCode.Code httpStatusToStatusCode(int httpStatus, String errorMessage) {
    String causeMessage = Strings.nullToEmpty(errorMessage).toUpperCase();
    if (httpStatus == 400) {
      if (causeMessage.contains(OUT_OF_RANGE)) {
        return Code.OUT_OF_RANGE;
      } else if (causeMessage.contains(FAILED_PRECONDITION)) {
        return Code.FAILED_PRECONDITION;
      } else {
        return Code.INVALID_ARGUMENT;
      }
    } else if (httpStatus == 401) {
      return Code.UNAUTHENTICATED;
    } else if (httpStatus == 403) {
      return Code.PERMISSION_DENIED;
    } else if (httpStatus == 404) {
      return Code.NOT_FOUND;
    } else if (httpStatus == 409) {
      if (causeMessage.contains(ALREADY_EXISTS)) {
        return Code.ALREADY_EXISTS;
      } else {
        return Code.ABORTED;
      }
    } else if (httpStatus == 429) {
      return Code.RESOURCE_EXHAUSTED;
    } else if (httpStatus == 499) {
      return Code.CANCELLED;
    } else if (httpStatus == 500) {
      if (causeMessage.contains(DATA_LOSS)) {
        return Code.DATA_LOSS;
      } else if (causeMessage.contains(UNKNOWN)) {
        return Code.UNKNOWN;
      } else {
        return Code.INTERNAL;
      }
    } else if (httpStatus == 503) {
      return Code.UNAVAILABLE;
    } else if (httpStatus == 504) {
      return Code.DEADLINE_EXCEEDED;
    } else {
      throw new IllegalArgumentException("Unrecognized http status code: " + httpStatus);
    }
  }

  private static int statusCodeToHttpStatus(StatusCode.Code statusCode) {
    switch (statusCode) {
      case CANCELLED:
        return 499;
      case NOT_FOUND:
        return 404;
      case UNKNOWN:
        return 500;
      case INVALID_ARGUMENT:
        return 400;
      case DEADLINE_EXCEEDED:
        return 504;
      case ALREADY_EXISTS:
        return 409;
      case PERMISSION_DENIED:
        return 403;
      case RESOURCE_EXHAUSTED:
        return 429;
      case FAILED_PRECONDITION:
        return 400;
      case ABORTED:
        return 409;
      case OUT_OF_RANGE:
        return 400;
      case INTERNAL:
        return 500;
      case UNAVAILABLE:
        return 503;
      case DATA_LOSS:
        return 500;
      case UNAUTHENTICATED:
        return 401;

      default:
        throw new IllegalArgumentException("Unrecognized status code: " + statusCode);
    }
  }

  @Override
  public StatusCode.Code getCode() {
    return statusCode;
  }

  /** Returns the status code from the http call. */
  @Override
  public Integer getTransportCode() {
    return httpStatus;
  }

  private HttpJsonStatusCode(int code, StatusCode.Code statusCode) {
    this.httpStatus = code;
    this.statusCode = statusCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HttpJsonStatusCode that = (HttpJsonStatusCode) o;

    return Objects.equals(statusCode, that.statusCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(statusCode);
  }
}
