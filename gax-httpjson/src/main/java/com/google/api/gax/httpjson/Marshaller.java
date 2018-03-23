package com.google.api.gax.httpjson;

import java.io.InputStream;

public interface Marshaller<T> {
  InputStream stream(T var1);

  T parse(InputStream var1);
}
