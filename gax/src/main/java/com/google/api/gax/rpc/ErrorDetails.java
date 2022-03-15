package com.google.api.gax.rpc;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.toprettystring.ToPrettyString;
import java.util.List;
import java.util.Map;

@AutoValue
public abstract class ErrorDetails {

  public abstract String reason();

  public abstract String domain();

  public abstract Map<String, String> errorInfoMetadata();

  public abstract List<String> detailsStringList();

  public static Builder builder() {
    return new AutoValue_ErrorDetails.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setReason(String reason);

    public abstract Builder setDomain(String domain);

    public abstract Builder setErrorInfoMetadata(Map<String, String> errorInfoMetadata);

    public abstract Builder setDetailsStringList(List<String> detailsStringList);

    public abstract ErrorDetails build();
  }

  @ToPrettyString
  abstract String toPrettyString();
}
