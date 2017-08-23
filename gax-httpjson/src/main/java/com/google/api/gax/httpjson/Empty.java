package com.google.api.gax.httpjson;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by andrealin on 8/23/17.
 */
public class Empty implements ApiMessage {

  @Override
  public Map<String, List<String>> populateFieldsInMap(Set fieldNames) {
    return null;
  }

  @Override
  public Object getRequestBodyObject() {
    return null;
  }
}
