package com.google.api.gax.core;

import com.google.auth.Credentials;

import java.io.IOException;

/**
 * Provides an interface to hold and acquire the credentials that will be used to call the service.
 */
public interface CredentialsProvider {
  /**
   * Gets the credentials which will be used to call the service. If the credentials have not been
   * acquired yet, then they will be acquired when this function is called.
   */
  Credentials getCredentials() throws IOException;
}
