package com.google.api.gax.httpjson;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ApiMessageHttpResponseFormatter<ResponseT extends ApiMessage>
    implements HttpResponseFormatter<ResponseT> {

  private final ApiMethodDescriptor<?, ResponseT> methodDescriptor;
  private final Gson responseMarshaller;

  /* Constructs an ApiMessageHttpRequestFormatter given any instance of the desired ResourceNameStruct implementing class. */
  public ApiMessageHttpResponseFormatter(final ApiMethodDescriptor<?, ResponseT> methodDescriptor) {
    this.methodDescriptor = methodDescriptor;

    final Gson baseGson = new GsonBuilder().create();
    TypeAdapter responseTypeAdapter;

    if (methodDescriptor.getResponseType() == null) {
      this.responseMarshaller = null;
    } else {
      responseTypeAdapter =
          new TypeAdapter<ResponseT>() {
            @Override
            public void write(JsonWriter out, ResponseT value) {
              baseGson.toJson(value, methodDescriptor.getResponseType(), out);
            }

            @Override
            public ResponseT read(JsonReader in) {
              return baseGson.fromJson(in, methodDescriptor.getResponseType());
            }
          };
      this.responseMarshaller =
          new GsonBuilder()
              .registerTypeAdapter(methodDescriptor.getResponseType(), responseTypeAdapter)
              .create();
    }
  }

  @Override
  public ResponseT parse(InputStream httpResponseBody) {
    if (methodDescriptor.getResponseType() == null) {
      return null;
    } else {
      return responseMarshaller.fromJson(
          new InputStreamReader(httpResponseBody), methodDescriptor.getResponseType());
    }
  }

  @VisibleForTesting
  public void writeResponse(Appendable output, Object response) {
    responseMarshaller.toJson(response, output);
  }
}
