/*
 * Copyright 2019 Google LLC
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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.util.IOUtils;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.core.RecordingScheduler;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.RequestParamsExtractor;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakeCallContext;
import com.google.api.gax.rpc.testing.FakeChannel;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.api.gax.rpc.testing.FakeTransportChannel;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class RetryingTest {

  @Test(expected = ApiException.class)
  public void testNonRetrySettings() {
    String CALL_OPTIONS_AUTHORITY = "RETRYING_TEST";
    ImmutableSet<StatusCode.Code> emptyRetryCodes = ImmutableSet.of();
    Duration totalTimeout = Duration.ofDays(2);

    @SuppressWarnings("unchecked")
    Marshaller<String> stringMarshaller = Mockito.mock(Marshaller.class);
    RequestParamsExtractor<String> paramsExtractor = Mockito.mock(RequestParamsExtractor.class);
    ManagedChannel managedChannel = Mockito.mock(ManagedChannel.class);

    MethodDescriptor<String, String> methodDescriptor =
        MethodDescriptor.<String, String>newBuilder()
            .setSchemaDescriptor("yaml")
            .setFullMethodName("fake.test/Greet")
            .setResponseMarshaller(stringMarshaller)
            .setRequestMarshaller(stringMarshaller)
            .setType(MethodType.UNARY)
            .build();

    RetrySettings retrySettings =
        RetrySettings.newBuilder()
            .setTotalTimeout(totalTimeout)
            .setInitialRetryDelay(Duration.ZERO)
            .setRetryDelayMultiplier(1.0)
            .setMaxRetryDelay(Duration.ZERO)
            .setMaxAttempts(1)
            .setJittered(true)
            .setInitialRpcTimeout(totalTimeout)
            .setRpcTimeoutMultiplier(1.0)
            .setMaxRpcTimeout(totalTimeout)
            .build();

    @SuppressWarnings("unchecked")
    ClientCall<String, String> clientCall = Mockito.mock(ClientCall.class);
    Mockito
        .doReturn(clientCall)
        .when(managedChannel)
        .newCall(ArgumentMatchers.eq(methodDescriptor), ArgumentMatchers.any(CallOptions.class));

    // Clobber the "authority" property with an identifier that allows us to trace
    // the use of this CallOptions variable.
    CallOptions spyCallOptions = CallOptions.DEFAULT.withAuthority("RETRYING_TEST");
    GrpcCallContext grpcCallContext = GrpcCallContext.createDefault()
        .withChannel(managedChannel)
        .withCallOptions(spyCallOptions);

    ArgumentCaptor<CallOptions> callOptionsArgumentCaptor = ArgumentCaptor.forClass(CallOptions.class);



    Mockito
        .doThrow(new ApiException(new RuntimeException(), FakeStatusCode.of(Code.UNAVAILABLE), false))
        .when(clientCall)
        .halfClose();

    GrpcCallSettings<String, String> grpcCallSettings =
        GrpcCallSettings.<String, String>newBuilder()
            .setMethodDescriptor(methodDescriptor)
            .setParamsExtractor(paramsExtractor)
            .build();
    UnaryCallSettings<String, String> nonRetriedCallSettings =
        UnaryCallSettings.<String, String>newUnaryCallSettingsBuilder()
            .setRetrySettings(retrySettings)
            .setRetryableCodes(emptyRetryCodes)
            .build();
    UnaryCallable<String, String> callable =
        GrpcCallableFactory.createUnaryCallable(
            grpcCallSettings,
            nonRetriedCallSettings,
            ClientContext.newBuilder().setDefaultCallContext(grpcCallContext).build());

    ApiFuture<String> future = callable.futureCall("Is your refrigerator running?");

    Mockito
        .verify(managedChannel)
        .newCall(ArgumentMatchers.eq(methodDescriptor), callOptionsArgumentCaptor.capture());
    CallOptions callOptionsUsed = callOptionsArgumentCaptor.getValue();

    assertThat(callOptionsUsed.getDeadline()).isNotNull();
    assertThat(callOptionsUsed.getDeadline()).isGreaterThan(Deadline.after(1, TimeUnit.DAYS));
    assertThat(callOptionsUsed.getAuthority()).isEqualTo(CALL_OPTIONS_AUTHORITY);
  }
}
