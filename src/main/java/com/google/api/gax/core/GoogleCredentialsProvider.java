/*
 * Copyright 2016, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.api.gax.core;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.util.List;

/**
 * GoogleCredentialsProvider acquires credentials using Application Default Credentials.
 *
 * <p>
 * For more information on Application Default Credentials, see <a
 * href="https://developers.google.com/identity/protocols/application-default-credentials">
 * https://developers.google.com/identity/protocols/application-default-credentials</a>.
 */
@AutoValue
public abstract class GoogleCredentialsProvider implements CredentialsProvider {

  public abstract List<String> getScopesToApply();

  @Override
  public Credentials getCredentials() throws IOException {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped(getScopesToApply());
    }
    return credentials;
  }

  public static Builder newBuilder() {
    return new AutoValue_GoogleCredentialsProvider.Builder();
  }

  public Builder toBuilder() {
    return new AutoValue_GoogleCredentialsProvider.Builder(this);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    /**
     * Sets the scopes to apply to the credentials that are acquired from Application Default
     * Credentials, before the credentials are sent to the service.
     */
    public abstract Builder setScopesToApply(List<String> val);

    /**
     * The scopes previously provided.
     */
    public abstract List<String> getScopesToApply();

    public abstract GoogleCredentialsProvider build();
  }
}
