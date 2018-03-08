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
