package com.google.api.gax.httpjson;

import com.google.api.resourcenames.ResourceName;
import java.util.Map;

/* A ResourceName that is essentially a dictionary mapping of fieldNames to values. */
public interface ResourceNameStruct extends ResourceName {
  /* Fetch the comprehensive mapping of fieldNames to values. The set of keys of the resulting Map
  should be constant, for a given instance of this interface. */
  Map<String, String> getFieldValues();

  /* Return a new instance of this interface by parsing a formatted String. This should be treated as a static method. */
  ResourceNameStruct parseFrom(String formattedString);
}
