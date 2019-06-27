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
import static org.mockito.Mockito.times;

import com.google.api.core.ApiFuture;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.RequestParamsExtractor;
import com.google.api.gax.rpc.ServerStream;
import com.google.api.gax.rpc.ServerStreamingCallSettings;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.testing.FakeStatusCode;
import com.google.common.collect.ImmutableSet;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class TimeoutTest {
  private static final String CALL_OPTIONS_AUTHORITY = "RETRYING_TEST";
  private static final int DEADLINE_IN_DAYS = 7;
  private static final int DEADLINE_IN_MINUTES = 10;
  private static final int DEADLINE_IN_SECONDS = 20;
  private static final ImmutableSet<StatusCode.Code> emptyRetryCodes = ImmutableSet.of();
  private static final Duration totalTimeout = Duration.ofDays(DEADLINE_IN_DAYS);
  private static final Duration maxRpcTimeout = Duration.ofMinutes(DEADLINE_IN_MINUTES);
  private static final Duration initialRpcTimeout = Duration.ofSeconds(DEADLINE_IN_SECONDS);

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);
  @Mock private Marshaller<String> stringMarshaller;
  @Mock private RequestParamsExtractor<String> paramsExtractor;
  @Mock private ManagedChannel managedChannel;

  @Test
  public void testNonRetryUnarySettings() {
    RetrySettings retrySettings =
        RetrySettings.newBuilder()
            .setTotalTimeout(totalTimeout)
            .setInitialRetryDelay(Duration.ZERO)
            .setRetryDelayMultiplier(1.0)
            .setMaxRetryDelay(Duration.ZERO)
            .setMaxAttempts(1)
            .setJittered(true)
            .setInitialRpcTimeout(initialRpcTimeout)
            .setRpcTimeoutMultiplier(1.0)
            .setMaxRpcTimeout(maxRpcTimeout)
            .build();
    CallOptions callOptionsUsed = setupUnaryCallable(retrySettings);

    // Verify that the gRPC channel used the CallOptions with our custom timeout of ~2 Days.
    assertThat(callOptionsUsed.getDeadline()).isNotNull();
    assertThat(callOptionsUsed.getDeadline())
        .isGreaterThan(Deadline.after(DEADLINE_IN_SECONDS - 1, TimeUnit.SECONDS));
    assertThat(callOptionsUsed.getDeadline())
        .isLessThan(Deadline.after(DEADLINE_IN_SECONDS, TimeUnit.SECONDS));
    assertThat(callOptionsUsed.getAuthority()).isEqualTo(CALL_OPTIONS_AUTHORITY);
  }

  @Test
  public void testNonRetryUnarySettingsWithoutInitialRpcTimeout() {
    RetrySettings retrySettings =
        RetrySettings.newBuilder()
            .setTotalTimeout(totalTimeout)
            .setInitialRetryDelay(Duration.ZERO)
            .setRetryDelayMultiplier(1.0)
            .setMaxRetryDelay(Duration.ZERO)
            .setMaxAttempts(1)
            .setJittered(true)
            .setRpcTimeoutMultiplier(1.0)
            .setMaxRpcTimeout(maxRpcTimeout)
            .build();
    CallOptions callOptionsUsed = setupUnaryCallable(retrySettings);

    // Verify that the gRPC channel used the CallOptions with our custom timeout of ~2 Days.
    assertThat(callOptionsUsed.getDeadline()).isNotNull();
    assertThat(callOptionsUsed.getDeadline())
        .isGreaterThan(Deadline.after(DEADLINE_IN_MINUTES - 1, TimeUnit.MINUTES));
    assertThat(callOptionsUsed.getDeadline())
        .isLessThan(Deadline.after(DEADLINE_IN_MINUTES, TimeUnit.MINUTES));
    assertThat(callOptionsUsed.getAuthority()).isEqualTo(CALL_OPTIONS_AUTHORITY);
  }

  @Test
  public void testNonRetryUnarySettingsWithoutIndividualRpcTimeout() {
    RetrySettings retrySettings =
        RetrySettings.newBuilder()
            .setTotalTimeout(totalTimeout)
            .setInitialRetryDelay(Duration.ZERO)
            .setRetryDelayMultiplier(1.0)
            .setMaxRetryDelay(Duration.ZERO)
            .setMaxAttempts(1)
            .setJittered(true)
            .setRpcTimeoutMultiplier(1.0)
            .setRpcTimeoutMultiplier(1.0)
            .build();
    CallOptions callOptionsUsed = setupUnaryCallable(retrySettings);

    // Verify that the gRPC channel used the CallOptions with our custom timeout of ~2 Days.
    assertThat(callOptionsUsed.getDeadline()).isNotNull();
    assertThat(callOptionsUsed.getDeadline())
        .isGreaterThan(Deadline.after(DEADLINE_IN_DAYS - 1, TimeUnit.DAYS));
    assertThat(callOptionsUsed.getDeadline())
        .isLessThan(Deadline.after(DEADLINE_IN_DAYS, TimeUnit.DAYS));
    assertThat(callOptionsUsed.getAuthority()).isEqualTo(CALL_OPTIONS_AUTHORITY);
  }

  @Test
  public void testNonRetryServerStreamingSettings() {
    RetrySettings retrySettings =
        RetrySettings.newBuilder()
            .setTotalTimeout(totalTimeout)
            .setInitialRetryDelay(Duration.ZERO)
            .setRetryDelayMultiplier(1.0)
            .setMaxRetryDelay(Duration.ZERO)
            .setMaxAttempts(1)
            .setJittered(true)
            .setInitialRpcTimeout(initialRpcTimeout)
            .setRpcTimeoutMultiplier(1.0)
            .setMaxRpcTimeout(maxRpcTimeout)
            .build();
    CallOptions callOptionsUsed = setupServerStreamingCallable(retrySettings);

    // Verify that the gRPC channel used the CallOptions with our custom timeout of ~2 Days.
    assertThat(callOptionsUsed.getDeadline()).isNotNull();
    assertThat(callOptionsUsed.getDeadline())
        .isGreaterThan(Deadline.after(DEADLINE_IN_SECONDS - 1, TimeUnit.SECONDS));
    assertThat(callOptionsUsed.getDeadline())
        .isLessThan(Deadline.after(DEADLINE_IN_SECONDS, TimeUnit.SECONDS));
    assertThat(callOptionsUsed.getAuthority()).isEqualTo(CALL_OPTIONS_AUTHORITY);
  }

  @Test
  public void testNonRetryServerStreamingSettingsWithoutInitialRpcTimeout() {
    RetrySettings retrySettings =
        RetrySettings.newBuilder()
            .setTotalTimeout(totalTimeout)
            .setInitialRetryDelay(Duration.ZERO)
            .setRetryDelayMultiplier(1.0)
            .setMaxRetryDelay(Duration.ZERO)
            .setMaxAttempts(1)
            .setJittered(true)
            .setRpcTimeoutMultiplier(1.0)
            .setMaxRpcTimeout(maxRpcTimeout)
            .build();
    CallOptions callOptionsUsed = setupServerStreamingCallable(retrySettings);

    // Verify that the gRPC channel used the CallOptions with our custom timeout of ~2 Days.
    assertThat(callOptionsUsed.getDeadline()).isNotNull();
    assertThat(callOptionsUsed.getDeadline())
        .isGreaterThan(Deadline.after(DEADLINE_IN_MINUTES - 1, TimeUnit.MINUTES));
    assertThat(callOptionsUsed.getDeadline())
        .isLessThan(Deadline.after(DEADLINE_IN_MINUTES, TimeUnit.MINUTES));
    assertThat(callOptionsUsed.getAuthority()).isEqualTo(CALL_OPTIONS_AUTHORITY);
  }

  @Test
  public void testNonRetryServerStreamingSettingsWithoutIndividualRpcTimeout() {
    RetrySettings retrySettings =
        RetrySettings.newBuilder()
            .setTotalTimeout(totalTimeout)
            .setInitialRetryDelay(Duration.ZERO)
            .setRetryDelayMultiplier(1.0)
            .setMaxRetryDelay(Duration.ZERO)
            .setMaxAttempts(1)
            .setJittered(true)
            .setRpcTimeoutMultiplier(1.0)
            .setRpcTimeoutMultiplier(1.0)
            .build();
    CallOptions callOptionsUsed = setupServerStreamingCallable(retrySettings);

    // Verify that the gRPC channel used the CallOptions with our custom timeout of ~2 Days.
    assertThat(callOptionsUsed.getDeadline()).isNotNull();
    assertThat(callOptionsUsed.getDeadline())
        .isGreaterThan(Deadline.after(DEADLINE_IN_DAYS - 1, TimeUnit.DAYS));
    assertThat(callOptionsUsed.getDeadline())
        .isLessThan(Deadline.after(DEADLINE_IN_DAYS, TimeUnit.DAYS));
    assertThat(callOptionsUsed.getAuthority()).isEqualTo(CALL_OPTIONS_AUTHORITY);
  }

  private CallOptions setupUnaryCallable(RetrySettings retrySettings) {
    MethodDescriptor<String, String> methodDescriptor =
        MethodDescriptor.<String, String>newBuilder()
            .setSchemaDescriptor("yaml")
            .setFullMethodName("fake.test/RingRing")
            .setResponseMarshaller(stringMarshaller)
            .setRequestMarshaller(stringMarshaller)
            .setType(MethodType.UNARY)
            .build();

    @SuppressWarnings("unchecked")
    ClientCall<String, String> clientCall = Mockito.mock(ClientCall.class);
    Mockito.doReturn(clientCall)
        .when(managedChannel)
        .newCall(ArgumentMatchers.eq(methodDescriptor), ArgumentMatchers.any(CallOptions.class));

    // Clobber the "authority" property with an identifier that allows us to trace
    // the use of this CallOptions variable.
    CallOptions spyCallOptions = CallOptions.DEFAULT.withAuthority("RETRYING_TEST");
    GrpcCallContext grpcCallContext =
        GrpcCallContext.createDefault().withChannel(managedChannel).withCallOptions(spyCallOptions);

    ArgumentCaptor<CallOptions> callOptionsArgumentCaptor =
        ArgumentCaptor.forClass(CallOptions.class);

    // Throw an exception during the gRPC channel business so we don't have to deal with
    // processing the channel output.
    Mockito.doThrow(
            new ApiException(new RuntimeException(), FakeStatusCode.of(Code.UNAVAILABLE), false))
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

    try {
      ApiFuture<String> future = callable.futureCall("Is your refrigerator running?");
    } catch (ApiException e) {
    }

    Mockito.verify(managedChannel, times(1))
        .newCall(ArgumentMatchers.eq(methodDescriptor), callOptionsArgumentCaptor.capture());
    return callOptionsArgumentCaptor.getValue();
  }

  private CallOptions setupServerStreamingCallable(RetrySettings retrySettings) {
    MethodDescriptor<String, String> methodDescriptor =
        MethodDescriptor.<String, String>newBuilder()
            .setSchemaDescriptor("yaml")
            .setFullMethodName("fake.test/RingRing")
            .setResponseMarshaller(stringMarshaller)
            .setRequestMarshaller(stringMarshaller)
            .setType(MethodType.SERVER_STREAMING)
            .build();

    @SuppressWarnings("unchecked")
    ClientCall<String, String> clientCall = Mockito.mock(ClientCall.class);
    Mockito.doReturn(clientCall)
        .when(managedChannel)
        .newCall(ArgumentMatchers.eq(methodDescriptor), ArgumentMatchers.any(CallOptions.class));

    // Clobber the "authority" property with an identifier that allows us to trace
    // the use of this CallOptions variable.
    CallOptions spyCallOptions = CallOptions.DEFAULT.withAuthority("RETRYING_TEST");
    GrpcCallContext grpcCallContext =
        GrpcCallContext.createDefault().withChannel(managedChannel).withCallOptions(spyCallOptions);

    ArgumentCaptor<CallOptions> callOptionsArgumentCaptor =
        ArgumentCaptor.forClass(CallOptions.class);

    // Throw an exception during the gRPC channel business so we don't have to deal with
    // processing the channel output.
    Mockito.doThrow(
            new ApiException(new RuntimeException(), FakeStatusCode.of(Code.UNAVAILABLE), false))
        .when(clientCall)
        .halfClose();

    GrpcCallSettings<String, String> grpcCallSettings =
        GrpcCallSettings.<String, String>newBuilder()
            .setMethodDescriptor(methodDescriptor)
            .setParamsExtractor(paramsExtractor)
            .build();
    ServerStreamingCallSettings<String, String> nonRetriedCallSettings =
        ServerStreamingCallSettings.<String, String>newBuilder()
            .setRetrySettings(retrySettings)
            .setRetryableCodes(emptyRetryCodes)
            .build();
    ServerStreamingCallable<String, String> callable =
        GrpcCallableFactory.createServerStreamingCallable(
            grpcCallSettings,
            nonRetriedCallSettings,
            ClientContext.newBuilder().setDefaultCallContext(grpcCallContext).build());

    try {
      ServerStream<String> stream = callable.call("Is your refrigerator running?");
    } catch (ApiException e) {
    }

    Mockito.verify(managedChannel, times(1))
        .newCall(ArgumentMatchers.eq(methodDescriptor), callOptionsArgumentCaptor.capture());
    return callOptionsArgumentCaptor.getValue();
  }
}
