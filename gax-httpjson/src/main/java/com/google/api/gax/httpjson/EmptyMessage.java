package com.google.api.gax.httpjson;

import java.util.List;
import javax.annotation.Nullable;

/**
 * A generic empty message that you can re-use to avoid defining duplicated empty messages in your
 * APIs. A typical example is to use it as the request or the response type of an API method.
 */
// TODO(andrealin): use this in a test
public class EmptyMessage implements ApiMessage {

  @Nullable
  @Override
  public Object getFieldValue(String fieldName) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getFieldMask() {
    return null;
  }

  @Nullable
  @Override
  public ApiMessage getApiMessageRequestBody() {
    return null;
  }
}
