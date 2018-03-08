/*
 * Copyright 2018 Google LLC
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
// package com.google.api.gax.httpjson;
//
// import static junit.framework.TestCase.fail;
//
// import com.google.api.client.http.GenericUrl;
// import com.google.api.client.http.HttpRequestFactory;
// import com.google.api.client.http.HttpResponse;
// import com.google.api.core.ApiClock;
// import com.google.api.gax.core.BackgroundResource;
// import com.google.api.gax.core.CredentialsProvider;
// import com.google.api.gax.core.ExecutorProvider;
// import com.google.api.gax.rpc.ClientSettings;
// import com.google.api.gax.rpc.HeaderProvider;
// import com.google.api.gax.rpc.TransportChannelProvider;
// import com.google.api.gax.rpc.WatchdogProvider;
// import com.google.api.gax.rpc.testing.FakeClientSettings;
// import java.io.IOException;
// import java.util.concurrent.TimeUnit;
// import org.junit.Test;
// import org.mockito.Mockito;
// import org.threeten.bp.Duration;
//
// public class MockHttpServiceTest {
//
//   @Test
//   public void testExpectResponse() {}
//
//   @Test
//   public void testExpectNull() {}
//
//   @Test
//   public void testExpectException() {}
//
//   @Test
//   public void testExpectResponses() {
//     MockHttpService testTransport = new MockHttpService();
//     FakeClientSettings.Builder builder = new FakeClientSettings.Builder();
//
//     ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
//     CredentialsProvider credentialsProvider = Mockito.mock(CredentialsProvider.class);
//     ApiClock clock = Mockito.mock(ApiClock.class);
//     HeaderProvider headerProvider = Mockito.mock(HeaderProvider.class);
//     WatchdogProvider watchdogProvider = Mockito.mock(WatchdogProvider.class);
//     Duration watchdogCheckInterval = Duration.ofSeconds(13);
//     TransportChannelProvider transportProvider =
//         InstantiatingHttpJsonChannelProvider.newBuilder()
//             .setExecutorProvider(executorProvider)
//             .setHeaderProvider(headerProvider)
//             .setHttpTransport(testTransport)
//             .setEndpoint("test/endpoint").build();
//
//     builder.setExecutorProvider(executorProvider);
//     builder.setTransportChannelProvider(transportProvider);
//     builder.setCredentialsProvider(credentialsProvider);
//     builder.setHeaderProvider(headerProvider);
//     builder.setClock(clock);
//     builder.setWatchdogProvider(watchdogProvider);
//     builder.setWatchdogCheckInterval(watchdogCheckInterval);
//
//     HttpRequestFactory httpRequestFactory = testTransport.createRequestFactory();
//     try {
//       HttpResponse httpResponse = httpRequestFactory.buildDeleteRequest(
//           new GenericUrl("test/endpoint")).execute();
//     } catch (IOException e) {
//       fail();
//     }
//
//
//   }
// }
