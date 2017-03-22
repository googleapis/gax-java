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
package com.google.api.gax.core;

import com.google.common.truth.Truth;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ApiFuturesTest {

  @Test
  public void testAddCallback() throws Exception {
    final AtomicInteger flag = new AtomicInteger();
    SettableApiFuture<Integer> future = SettableApiFuture.<Integer>create();
    ApiFutures.addCallback(
        future,
        new ApiFutureCallback<Integer>() {
          @Override
          public void onSuccess(Integer i) {
            flag.set(i + 1);
          }

          @Override
          public void onFailure(Throwable t) {
            flag.set(-1);
          }
        });
    future.set(0);
    Truth.assertThat(flag.get()).isEqualTo(1);
  }

  @Test
  public void testCatch() throws Exception {
    SettableApiFuture<Integer> future = SettableApiFuture.<Integer>create();
    ApiFuture<Integer> fallback =
        ApiFutures.catching(
            future,
            Exception.class,
            new ApiFunction<Exception, Integer>() {
              @Override
              public Integer apply(Exception ex) {
                return 42;
              }
            });
    future.setException(new Exception());
    Truth.assertThat(fallback.get()).isEqualTo(42);
  }

  @Test
  public void testTransform() throws Exception {
    SettableApiFuture<Integer> inputFuture = SettableApiFuture.<Integer>create();
    ApiFuture<String> transformedFuture =
        ApiFutures.transform(
            inputFuture,
            new ApiFunction<Integer, String>() {
              @Override
              public String apply(Integer input) {
                return input.toString();
              }
            });
    inputFuture.set(6);
    Truth.assertThat(transformedFuture.get()).isEqualTo("6");
  }
}
