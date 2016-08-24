/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
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

package com.google.api.gax.grpc;

import java.util.Collection;

import org.junit.Test;

public class BundleExecutorTest {

  BundlingDescriptor<Integer, Integer> integerDescriptor =
      new BundlingDescriptor<Integer, Integer>() {

        @Override
        public String getBundlePartitionKey(Integer request) {
          return new Integer(request % 2).toString();
        }

        @Override
        public Integer mergeRequests(Collection<Integer> requests) {
          return null;
        }

        @Override
        public void splitResponse(
            Integer bundleResponse, Collection<? extends RequestIssuer<Integer, Integer>> bundle) {}

        @Override
        public void splitException(
            Throwable throwable, Collection<? extends RequestIssuer<Integer, Integer>> bundle) {}

        @Override
        public long countElements(Integer request) {
          return 1;
        }

        @Override
        public long countBytes(Integer request) {
          return 1;
        }
      };

  @Test
  public void testValidate() {
    BundleExecutor<Integer, Integer> executor =
        new BundleExecutor<Integer, Integer>(integerDescriptor, "0");
    CallContext<Integer> callContextOk = CallContext.of(2);
    BundlingContext<Integer, Integer> bundlingContextOk =
        new BundlingContext<Integer, Integer>(callContextOk, null, null);
    executor.validateItem(bundlingContextOk);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testValidateFailure() {
    BundleExecutor<Integer, Integer> executor =
        new BundleExecutor<Integer, Integer>(integerDescriptor, "0");
    CallContext<Integer> callContextOk = CallContext.of(3);
    BundlingContext<Integer, Integer> bundlingContextOk =
        new BundlingContext<Integer, Integer>(callContextOk, null, null);
    executor.validateItem(bundlingContextOk);
  }
}
