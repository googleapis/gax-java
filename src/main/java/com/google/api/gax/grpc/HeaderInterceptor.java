package com.google.api.gax.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * An intercepter to handle custom header.
 *
 * Package-private for internal usage.
 */
class HeaderInterceptor implements ClientInterceptor {
  private static final Metadata.Key<String> HEADER_KEY =
      Metadata.Key.of("x-google-apis-agent", Metadata.ASCII_STRING_MARSHALLER);
  private final String header;

  public HeaderInterceptor(String header) {
    this.header = header;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method,
      CallOptions callOptions,
      Channel next) {
    ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
    return new SimpleForwardingClientCall<ReqT, RespT>(call) {
      @Override
      public void start(ClientCall.Listener<RespT> responseListener, Metadata headers) {
        headers.put(HEADER_KEY, header);
        super.start(responseListener, headers);
      }
    };
  }
}
