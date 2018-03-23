// package com.google.api.gax.httpjson;
//
// import java.util.Set;
//
// public class ApiMessageUtils {
//
//     public static <RequestT extends ApiMessage> HttpRequestFormatter<?>
//         getHttpRequestFormatter(RequestT requestInstance) {
//       return new ApiMessageHttpRequestFormatter<RequestT>(requestInstance);
//     }
//
//   public static <ResponseT extends ApiMessage> HttpResponseParser<ResponseT>
//       getHttpResponseParser(ApiMethodDescriptor<?, ResponseT> methodDescriptor) {
//     return new ApiMessageHttpResponseParser<ResponseT>(methodDescriptor);
//   }
// }
