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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.BetaApi;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

/**
 * The request params encoder, which encodes URL-encoded parameters in one URL parameters string.
 * This class expects that name-value pairs, returned from parameters extractor are already
 * URL-encoded and can perform optional validation of that, but does not encode the name-value pairs
 * themselves.
 *
 * @param <RequestT> request message type
 */
@BetaApi
public class RequestUrlParamsEncoder<RequestT> implements RequestParamsEncoder<RequestT> {
  private static final String STR_ENCODING = "UTF-8";

  private final RequestParamsExtractor<RequestT> paramsExtractor;
  private final boolean validateExtractedParameters;

  /**
   * Creates the encoder.
   *
   * @param paramsExtractor parameters extractor which returns already URL-encoded key-value pairs
   * @param validateExtractedParameters {@code true} if this class should validate that the
   *     extracted parameters are URL-encoded, {@code false} otherwise
   */
  public RequestUrlParamsEncoder(
      RequestParamsExtractor<RequestT> paramsExtractor, boolean validateExtractedParameters) {
    this.paramsExtractor = checkNotNull(paramsExtractor);
    this.validateExtractedParameters = validateExtractedParameters;
  }

  /**
   * Encodes the {@code request} in a form of a URL parameters string, for example {@code
   * "param1=value+1&param2=value2%26"}. This method may optionally validate that the name-value
   * paris are URL-encoded, but it will not perform the actual encoding of them (it will only
   * concatenate the valid individual name-value pairs in a valid URL parameters string). This is
   * so, because in most practical cases the name-value paris are already URL-encoded.
   *
   * @param request request message
   * @throws IllegalArgumentException if is not
   */
  @Override
  public String encode(RequestT request) {
    Map<String, String> params = paramsExtractor.extract(request);
    if (params.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (sb.length() > 0) {
        sb.append("&");
      }
      String name = entry.getKey();
      String value = entry.getValue();
      if (name == null) {
        throw new IllegalArgumentException("Request parameter name cannot be null");
      }

      // Let the server decide if the value is required.
      // Empty value is allowed.
      if (value != null) {
        if (!isValid(name, value)) {
          throw new IllegalArgumentException(
              "Invalid url-encoded request parameter name-value pair: " + name + "=" + value);
        }
        sb.append(name).append("=").append(value);
      }
    }

    return sb.toString();
  }

  // Not sure if we need this at all.
  private boolean isValid(String name, String value) {
    try {
      // hoping that encode/decode do not loose information in the middle
      // (at least for practical use cases)
      return !validateExtractedParameters
          || name.equals(URLEncoder.encode(URLDecoder.decode(name, STR_ENCODING), STR_ENCODING))
              && value.equals(
                  URLEncoder.encode(URLDecoder.decode(value, STR_ENCODING), STR_ENCODING));
    } catch (UnsupportedEncodingException e) {
      return false;
    }
  }
}
