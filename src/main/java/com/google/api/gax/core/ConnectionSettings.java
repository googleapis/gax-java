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
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;

/**
 * Holds the settings required to connect to a remote server. This includes a port, a
 * service address, and credentials.
 *
 * <p>Currently only Google Cloud Platform services are supported.
 *
 * <p>The credentials can either be supplied directly (through
 * Builder.provideCredentialsWith(Credentials) or acquired implicitly from Application Default
 * Credentials (using Builder.provideCredentialsWith(List). The scopes provided
 * to the second function will be used to scope the credentials acquired through ADC.
 *
 * <p>For more information on Application Default Credentials, see
 * <a href="https://developers.google.com/identity/protocols/application-default-credentials">
 * https://developers.google.com/identity/protocols/application-default-credentials</a>.
 */
@AutoValue
public abstract class ConnectionSettings {

  /*
   * package-private so that the AutoValue derived class can access it
   */
  interface CredentialsProvider {
    Credentials getCredentials() throws IOException;
  }

  /**
   * Gets the credentials which will be used to call the service. If the credentials
   * have not been acquired yet, then they will be acquired when this function is called.
   */
  public Credentials getCredentials() throws IOException {
    return getCredentialsProvider().getCredentials();
  }

  /*
   * package-private so that the AutoValue derived class can access it
   */
  abstract CredentialsProvider getCredentialsProvider();

  /**
   * The address used to reach the service.
   */
  public abstract String getServiceAddress();

  /**
   * The port used to reach the service.
   */
  public abstract int getPort();

  public static Builder newBuilder() {
    return new AutoValue_ConnectionSettings.Builder();
  }

  public Builder toBuilder() {
    return new AutoValue_ConnectionSettings.Builder(this);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    /*
     * package-private so that the AutoValue derived class can access it
     */
    abstract Builder setCredentialsProvider(CredentialsProvider provider);

    /**
     * Sets the credentials to use in order to call the service.
     */
    public Builder provideCredentialsWith(final Credentials credentials) {
      return setCredentialsProvider(new CredentialsProvider() {
        @Override
        public Credentials getCredentials() {
          return credentials;
        }
      });
    }

    /**
     * Causes the credentials to be acquired using Application Default Credentials,
     * and applies the given scopes to the result if that is required from the
     * type of credentials.
     */
    public Builder provideCredentialsWith(List<String> scopes) {
      final List<String> scopesToApply = Lists.newArrayList(scopes);
      return setCredentialsProvider(new CredentialsProvider() {
        @Override
        public Credentials getCredentials() throws IOException {
          GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
          if (credentials.createScopedRequired()) {
            credentials = credentials.createScoped(scopesToApply);
          }
          return credentials;
        }
      });
    }

    /**
     * Sets the address used to reach the service.
     */
    public abstract Builder setServiceAddress(String serviceAddress);

    /**
     * Sets the port used to reach the service.
     */
    public abstract Builder setPort(int port);

    /**
     * Builds the ConnectionSettings. This doesn't actually acquire the credentials
     * yet at this point.
     */
    public abstract ConnectionSettings build();
  }
}
