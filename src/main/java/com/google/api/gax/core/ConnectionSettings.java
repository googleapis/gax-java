/*
 * Copyright 2015, Google Inc.
 * All rights reserved.
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

@AutoValue
public abstract class ConnectionSettings {

  interface CredentialsProvider {
    Credentials getCredentials() throws IOException;
  }

  /**
   * Credentials to use in order to call the service.
   */
  public Credentials getCredentials() throws IOException {
    return getCredentialsProvider().getCredentials();
  }

  abstract CredentialsProvider getCredentialsProvider();

  /**
   * The path used to reach the service.
   */
  public abstract String getServiceAddress();

  /**
   * The port used to reach the service.
   */
  public abstract int getPort();

  public static Builder builder() {
    return new AutoValue_ConnectionSettings.Builder();
  }

  public Builder toBuilder() {
    return new AutoValue_ConnectionSettings.Builder(this);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    abstract Builder setCredentialsProvider(CredentialsProvider provider);

    /**
     * Sets the credentials to use in order to call the service.
     */
    public Builder provideCredentialsWith(Credentials credentials) {
      return setCredentialsProvider(new CredentialsProvider() {
        @Override
        public Credentials getCredentials() {
          return credentials;
        }
      });
    }

    /**
     * Sets the credentials using application default, applying the given scopes if needed.
     */
    public Builder provideCredentialsWith(List<String> scopes) {
      return setCredentialsProvider(new CredentialsProvider() {
        @Override
        public Credentials getCredentials() throws IOException {
          GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
          if (credentials.createScopedRequired()) {
            credentials = credentials.createScoped(scopes);
          }
          return credentials;
        }
      });
    }

    /**
     * Sets the path used to reach the service.
     */
    public abstract Builder setServiceAddress(String serviceAddress);

    /**
     * Sets the port used to reach the service.
     */
    public abstract Builder setPort(int port);

    /**
     * Builds the ConnectionSettings.
     */
    public abstract ConnectionSettings build();
  }
}
