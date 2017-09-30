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
package com.google.api.gax.grpc;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.DataLossException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.TranslateExceptionParameters;
import com.google.api.gax.rpc.TransportDescriptor;
import com.google.api.gax.rpc.UnavailableException;
import com.google.api.gax.rpc.UnknownException;
import com.google.common.truth.Truth;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GrpcTransportDescriptorTest {
  private static boolean NOT_RETRYABLE = false;
  private static boolean IS_RETRYABLE = true;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void translateException_StatusException_noRetry() throws Exception {
    SettableApiFuture<Integer> result = SettableApiFuture.create();

    TransportDescriptor transportDescriptor = GrpcTransportDescriptor.create();
    Throwable originalException = new StatusException(Status.INVALID_ARGUMENT);
    TranslateExceptionParameters parameters =
        TranslateExceptionParameters.newBuilder()
            .setThrowable(originalException)
            .setCancelled(false)
            .setRetryableCodes(Collections.<StatusCode>emptySet())
            .setResultFuture(result)
            .build();
    transportDescriptor.translateException(parameters);

    assertInnerExceptionIsInstanceOf(
        result, InvalidArgumentException.class, NOT_RETRYABLE, originalException);
  }

  @Test
  public void translateException_StatusException_withRetry() throws Exception {
    SettableApiFuture<Integer> result = SettableApiFuture.create();

    TransportDescriptor transportDescriptor = GrpcTransportDescriptor.create();
    Throwable originalException = new StatusException(Status.UNAVAILABLE);
    TranslateExceptionParameters parameters =
        TranslateExceptionParameters.newBuilder()
            .setThrowable(originalException)
            .setCancelled(false)
            .setRetryableCodes(
                Collections.<StatusCode>singleton(GrpcStatusCode.of(Code.UNAVAILABLE)))
            .setResultFuture(result)
            .build();
    transportDescriptor.translateException(parameters);

    assertInnerExceptionIsInstanceOf(
        result, UnavailableException.class, IS_RETRYABLE, originalException);
  }

  @Test
  public void translateException_StatusRuntimeException_noRetry() throws Exception {
    SettableApiFuture<Integer> result = SettableApiFuture.create();

    TransportDescriptor transportDescriptor = GrpcTransportDescriptor.create();
    Throwable originalException = new StatusRuntimeException(Status.INVALID_ARGUMENT);
    TranslateExceptionParameters parameters =
        TranslateExceptionParameters.newBuilder()
            .setThrowable(originalException)
            .setCancelled(false)
            .setRetryableCodes(Collections.<StatusCode>emptySet())
            .setResultFuture(result)
            .build();
    transportDescriptor.translateException(parameters);

    assertInnerExceptionIsInstanceOf(
        result, InvalidArgumentException.class, NOT_RETRYABLE, originalException);
  }

  @Test
  public void translateException_StatusRuntimeException_withRetry() throws Exception {
    SettableApiFuture<Integer> result = SettableApiFuture.create();

    TransportDescriptor transportDescriptor = GrpcTransportDescriptor.create();
    Throwable originalException = new StatusRuntimeException(Status.UNAVAILABLE);
    TranslateExceptionParameters parameters =
        TranslateExceptionParameters.newBuilder()
            .setThrowable(originalException)
            .setCancelled(false)
            .setRetryableCodes(
                Collections.<StatusCode>singleton(GrpcStatusCode.of(Code.UNAVAILABLE)))
            .setResultFuture(result)
            .build();
    transportDescriptor.translateException(parameters);

    assertInnerExceptionIsInstanceOf(
        result, UnavailableException.class, IS_RETRYABLE, originalException);
  }

  @Test
  public void translateException_cancelled() throws Exception {
    SettableApiFuture<Integer> result = SettableApiFuture.create();

    TransportDescriptor transportDescriptor = GrpcTransportDescriptor.create();
    Throwable originalException = new CancellationException();
    TranslateExceptionParameters parameters =
        TranslateExceptionParameters.newBuilder()
            .setThrowable(originalException)
            .setCancelled(true)
            .setRetryableCodes(Collections.<StatusCode>emptySet())
            .setResultFuture(result)
            .build();
    transportDescriptor.translateException(parameters);

    Truth.assertThat(result.isDone()).isFalse();
  }

  @Test
  public void translateException_ApiException() throws Exception {
    SettableApiFuture<Integer> result = SettableApiFuture.create();

    TransportDescriptor transportDescriptor = GrpcTransportDescriptor.create();
    Throwable originalException = new RuntimeException("stuff went wrong");
    Throwable apiException =
        new DataLossException(originalException, GrpcStatusCode.of(Code.UNKNOWN), IS_RETRYABLE);
    TranslateExceptionParameters parameters =
        TranslateExceptionParameters.newBuilder()
            .setThrowable(apiException)
            .setCancelled(false)
            .setRetryableCodes(Collections.<StatusCode>emptySet())
            .setResultFuture(result)
            .build();
    transportDescriptor.translateException(parameters);

    assertInnerExceptionIsInstanceOf(
        result, DataLossException.class, IS_RETRYABLE, originalException);
  }

  @Test
  public void translateException_RuntimeException() throws Exception {
    SettableApiFuture<Integer> result = SettableApiFuture.create();

    TransportDescriptor transportDescriptor = GrpcTransportDescriptor.create();
    Throwable originalException = new RuntimeException("stuff went wrong");
    TranslateExceptionParameters parameters =
        TranslateExceptionParameters.newBuilder()
            .setThrowable(originalException)
            .setCancelled(false)
            .setRetryableCodes(Collections.<StatusCode>emptySet())
            .setResultFuture(result)
            .build();
    transportDescriptor.translateException(parameters);

    assertInnerExceptionIsInstanceOf(
        result, UnknownException.class, NOT_RETRYABLE, originalException);
  }

  public void assertInnerExceptionIsInstanceOf(
      Future<Integer> result, Class<?> clazz, boolean retryable, Throwable originalException)
      throws Exception {
    Throwable innerException;
    try {
      result.get();
      innerException = null;
    } catch (ExecutionException e) {
      innerException = e.getCause();
    }
    Truth.assertThat(innerException).isInstanceOf(clazz);
    ApiException apiException = (ApiException) innerException;
    Truth.assertThat(apiException.isRetryable()).isEqualTo(retryable);
    Truth.assertThat(apiException.getCause()).isSameAs(originalException);
  }
}
