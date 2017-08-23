package com.google.api.gax.httpjson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Created by andrealin on 8/23/17.
 */
public class ApiMessageHttpRequestBuilder implements HttpRequestBuilder<ApiMessage> {
  public Map<String, List<String>> getQueryParams(ApiMessage apiMessage, Set<String> fieldNames) {
    return apiMessage.populateFieldsInMap(fieldNames);
  }

  public Map<String, String> getPathParams(ApiMessage apiMessage, Set<String> fieldNames) {
    Map<String, String> pathParams = new HashMap<>();
    Map<String, List<String>> pathParamMap = apiMessage.populateFieldsInMap(fieldNames);
    Iterator iterator = pathParamMap.entrySet().iterator();
    while(iterator.hasNext()) {
      Map.Entry<String, List<String>> pair = (Entry<String, List<String>>) iterator.next();
      pathParams.put(pair.getKey(), pair.getValue().get(0));
    }
    return pathParams;
  }

  public void getRequestBody(ApiMessage apiMessage, Gson marshaller, Writer writer) {
      marshaller.toJson(apiMessage, writer);
  }
}
