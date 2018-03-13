/*
 * Copyright 2016 Google LLC
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
import com.google.common.base.Function;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link GrpcHeaderInterceptor}. */
@RunWith(Parameterized.class)
public class GrpcMetadataHandlerInterceptorTest {
  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          createTestCase(false, false),
          createTestCase(true, false),
          createTestCase(false, true),
          createTestCase(true, true),
        });
  }

  private static class MutableBoolean {
    private volatile boolean value = false;
  }

  private static Object[] createTestCase(
      boolean expectMetadataHandlerCalled, boolean expectTrailingMetadataHandlerCalled) {
    final MutableBoolean metadataHandlerCalled = new MutableBoolean();
    final MutableBoolean trailingMetadataHandlerCalled = new MutableBoolean();

    CallOptions callOptions = CallOptions.DEFAULT;
    if (expectMetadataHandlerCalled) {
      Function<Metadata, Void> metadataHandler =
          new Function<Metadata, Void>() {
            @Nullable
            @Override
            public Void apply(@Nullable Metadata input) {
              metadataHandlerCalled.value = true;
              return null;
            }
          };
      callOptions = CallOptionsUtil.putMetadataHandlerOption(callOptions, metadataHandler);
    }
    if (expectTrailingMetadataHandlerCalled) {
      Function<Metadata, Void> metadataHandler =
          new Function<Metadata, Void>() {
            @Nullable
            @Override
            public Void apply(@Nullable Metadata input) {
              trailingMetadataHandlerCalled.value = true;
              return null;
            }
          };
      callOptions = CallOptionsUtil.putTrailingMetadataHandlerOption(callOptions, metadataHandler);
    }
    return new Object[] {
      callOptions,
      metadataHandlerCalled,
      trailingMetadataHandlerCalled,
      expectMetadataHandlerCalled,
      expectTrailingMetadataHandlerCalled
    };
  }

  @Mock private Channel channel;

  @Mock private ClientCall<String, Integer> call;

  private static final MethodDescriptor<String, Integer> method = FakeMethodDescriptor.create();

  private CallOptions callOptions;
  private MutableBoolean metadataHandlerCalled;
  private MutableBoolean trailingMetadataHandlerCalled;
  private boolean expectMetadataHandlerCalled;
  private boolean expectTrailingMetadataHandlerCalled;

  public GrpcMetadataHandlerInterceptorTest(
      CallOptions callOptions,
      MutableBoolean metadataHandlerCalled,
      MutableBoolean trailingMetadataHandlerCalled,
      boolean expectMetadataHandlerCalled,
      boolean expectTrailingMetadataHandlerCalled) {
    this.callOptions = callOptions;
    this.metadataHandlerCalled = metadataHandlerCalled;
    this.trailingMetadataHandlerCalled = trailingMetadataHandlerCalled;
    this.expectMetadataHandlerCalled = expectMetadataHandlerCalled;
    this.expectTrailingMetadataHandlerCalled = expectTrailingMetadataHandlerCalled;
  }

  /** Sets up mocks. */
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(channel.newCall(Mockito.<MethodDescriptor<String, Integer>>any(), any(CallOptions.class)))
        .thenReturn(call);
  }

  @Test
  public void testInterceptor() {
    GrpcMetadataHandlerInterceptor interceptor = new GrpcMetadataHandlerInterceptor();
    Channel intercepted = ClientInterceptors.intercept(channel, interceptor);
    @SuppressWarnings("unchecked")
    ClientCall.Listener<Integer> listener = mock(ClientCall.Listener.class);

    ClientCall<String, Integer> interceptedCall = intercepted.newCall(method, callOptions);

    // Confirm false
    assertFalse(metadataHandlerCalled.value);
    assertFalse(trailingMetadataHandlerCalled.value);

    // start() on the intercepted call will eventually reach the call created by the real channel
    interceptedCall.start(listener, new Metadata());
    ArgumentCaptor<ClientCall.Listener> captor = ArgumentCaptor.forClass(ClientCall.Listener.class);
    ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(call).start(captor.capture(), metadataCaptor.capture());

    // Confirm false before calling functions on listener
    assertFalse(metadataHandlerCalled.value);
    assertFalse(trailingMetadataHandlerCalled.value);

    captor.getValue().onHeaders(new Metadata());

    // Confirm values after headers but before close
    assertEquals(expectMetadataHandlerCalled, metadataHandlerCalled.value);
    assertFalse(trailingMetadataHandlerCalled.value);

    captor.getValue().onClose(Status.fromCodeValue(0), new Metadata());

    // Confirm values after close
    assertEquals(expectMetadataHandlerCalled, metadataHandlerCalled.value);
    assertEquals(expectTrailingMetadataHandlerCalled, trailingMetadataHandlerCalled.value);
  }
}
