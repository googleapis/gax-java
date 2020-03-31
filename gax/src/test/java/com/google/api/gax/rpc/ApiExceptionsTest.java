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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.core.ApiFutures;
import com.google.api.core.ListenableFutureToApiFuture;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.IOException;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApiExceptionsTest {

  @Test
  public void noException() {
    Integer result = ApiExceptions.callAndTranslateApiException(ApiFutures.immediateFuture(2));
    assertThat(result).isEqualTo(2);
  }

  @Test
  public void throwsApiException() {
    Exception throwable =
        new UnavailableException(null, FakeStatusCode.of(StatusCode.Code.UNAVAILABLE), false);
    try {
      ApiExceptions.callAndTranslateApiException(ApiFutures.immediateFailedFuture(throwable));
      Assert.fail("ApiExceptions should have thrown an exception");
    } catch (ApiException expected) {
      assertThat(expected).isSameInstanceAs(throwable);
    }
  }

  @Test
  public void throwsIOException() {
    try {
      ApiExceptions.callAndTranslateApiException(
          ApiFutures.immediateFailedFuture(new IOException()));
      Assert.fail("ApiExceptions should have thrown an exception");
    } catch (UncheckedExecutionException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(IOException.class);
    }
  }

  @Test
  public void throwsRuntimeException() {
    try {
      ApiExceptions.callAndTranslateApiException(
          ApiFutures.immediateFailedFuture(new IllegalArgumentException()));
      Assert.fail("ApiExceptions should have thrown an exception");
    } catch (IllegalArgumentException expected) {
      assertThat(expected).isInstanceOf(IllegalArgumentException.class);
    }
  }

  /**
   * Make sure that the caller's stacktrace is preserved when the future is unwrapped. The
   * stacktrace will be preserved as a suppressed RuntimeException.
   */
  @Test
  public void containsCurrentStacktrace() {
    final String currentMethod = "containsCurrentStacktrace";

    // Throw an error in an executor, which will cause it to lose the current stack frame
    ListeningExecutorService executor =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

    ListenableFuture<?> futureError =
        executor.submit(
            new Runnable() {
              @Override
              public void run() {
                throw new IllegalArgumentException();
              }
            });
    ListenableFutureToApiFuture<?> futureErrorWrapper =
        new ListenableFutureToApiFuture<>(futureError);
    executor.shutdown();

    // Unwrap the future
    Exception actualError = null;
    try {
      ApiExceptions.callAndTranslateApiException(futureErrorWrapper);
    } catch (Exception e) {
      actualError = e;
    }

    // Sanity check that the current stack trace is not in the exception
    assertThat(actualError).isNotNull();
    assertThat(isMethodInStacktrace(currentMethod, actualError)).isFalse();

    // Verify that it is preserved as a suppressed exception.
    assertThat(actualError.getSuppressed()[0]).isInstanceOf(AsyncTaskException.class);
    assertThat(isMethodInStacktrace(currentMethod, actualError.getSuppressed()[0])).isTrue();
  }

  private static boolean isMethodInStacktrace(String method, Throwable t) {
    for (StackTraceElement e : t.getStackTrace()) {
      if (method.equals(e.getMethodName())) {
        return true;
      }
    }

    return false;
  }
}
