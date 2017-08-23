package com.google.api.gax.httpjson;

import com.google.gson.Gson;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by andrealin on 8/23/17.
 */
public interface HttpRequestBuilder<MessageFormatT> {
  Map<String, List<String>> getQueryParams(MessageFormatT apiMessage, Set<String> fieldNames);

  Map<String, String> getPathParams(MessageFormatT apiMessage, Set<String> fieldNames);

  void writeRequestBody(ApiMessage apiMessage, Gson marshaller, Writer writer);
}
