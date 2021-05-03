/*
 * Copyright 2021 Google LLC
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
 *     * Neither the name of Google LLC nor the names of its
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

package com.google.api.gax.rpc.mtls;

import com.google.api.client.json.JsonParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.SecurityUtils;
import com.google.api.core.BetaApi;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;

/**
 * Provider class for mutual TLS. It is used to configure the mutual TLS in the transport with the
 * default client certificate on device.
 */
@BetaApi
public class MtlsProvider {
  interface EnvironmentProvider {
    String getenv(String name);
  }

  static class SystemEnvironmentProvider implements EnvironmentProvider {
    @Override
    public String getenv(String name) {
      return System.getenv(name);
    }
  }

  interface ProcessProvider {
    public Process createProcess(InputStream metadata) throws IOException;
  }

  static class DefaultProcessProvider implements ProcessProvider {
    @Override
    public Process createProcess(InputStream metadata) throws IOException {
      if (metadata == null) {
        return null;
      }
      List<String> command = extractCertificateProviderCommand(metadata);
      return new ProcessBuilder(command).start();
    }
  }

  private static final String DEFAULT_CONTEXT_AWARE_METADATA_PATH =
      System.getProperty("user.home") + "/.secureConnect/context_aware_metadata.json";

  private String metadataPath;
  private EnvironmentProvider envProvider;
  private ProcessProvider processProvider;

  public enum UseMtlsEndpoint {
    NEVER,
    AUTO,
    ALWAYS;
  }

  @VisibleForTesting
  MtlsProvider(
      EnvironmentProvider envProvider, ProcessProvider processProvider, String metadataPath) {
    this.envProvider = envProvider;
    this.processProvider = processProvider;
    this.metadataPath = metadataPath;
  }

  public MtlsProvider() {
    this(
        new SystemEnvironmentProvider(),
        new DefaultProcessProvider(),
        DEFAULT_CONTEXT_AWARE_METADATA_PATH);
  }

  /**
   * Returns if mutual TLS client certificate should be used. If the value is true, the key store
   * from {@link #getKeyStore()} will be used to configure mutual TLS transport.
   */
  public boolean useMtlsClientCertificate() {
    String useClientCertificate = envProvider.getenv("GOOGLE_API_USE_CLIENT_CERTIFICATE");
    return "true".equals(useClientCertificate);
  }

  /**
   * Returns AUTO, NEVER or ALWAYS. NEVER means always use regular endpoint; ALWAYS means always use
   * mTLS endpoint; AUTO means auto switch to mTLS endpoint if client certificate exists and should
   * be used.
   */
  public UseMtlsEndpoint useMtlsEndpoint() {
    String useMtlsEndpoint = envProvider.getenv("GOOGLE_API_USE_MTLS_ENDPOINT");
    if ("never".equals(useMtlsEndpoint)) {
      return UseMtlsEndpoint.NEVER;
    } else if ("always".equals(useMtlsEndpoint)) {
      return UseMtlsEndpoint.ALWAYS;
    }
    return UseMtlsEndpoint.AUTO;
  }

  /** The mutual TLS key store created with the default client certificate on device. */
  public KeyStore getKeyStore() throws IOException {
    try (InputStream stream = new FileInputStream(metadataPath)) {
      return getKeyStore(stream, processProvider);
    } catch (FileNotFoundException exception) {
      // file doesn't exist
      return null;
    }
  }

  @VisibleForTesting
  static KeyStore getKeyStore(InputStream metadata, ProcessProvider processProvider)
      throws IOException {
    try {
      Process process = processProvider.createProcess(metadata);

      // Run the command and timeout after 1000 milliseconds.
      int exitCode = runCertificateProviderCommand(process, 1000);
      if (exitCode != 0) {
        throw new IOException("Cert provider command failed with exit code: " + exitCode);
      }

      // Create mTLS key store with the input certificates from shell command.
      return SecurityUtils.createMtlsKeyStore(process.getInputStream());
    } catch (InterruptedException e) {
      throw new IOException("Interrupted executing certificate provider command", e);
    } catch (GeneralSecurityException e) {
      throw new IOException("Failed to get key store", e);
    }
  }

  @VisibleForTesting
  static ImmutableList<String> extractCertificateProviderCommand(InputStream contextAwareMetadata)
      throws IOException {
    JsonParser parser = new GsonFactory().createJsonParser(contextAwareMetadata);
    ContextAwareMetadataJson json = parser.parse(ContextAwareMetadataJson.class);
    return json.getCommands();
  }

  @VisibleForTesting
  static int runCertificateProviderCommand(Process commandProcess, long timeoutMilliseconds)
      throws IOException, InterruptedException {
    long startTime = System.currentTimeMillis();
    long remainTime = timeoutMilliseconds;

    while (remainTime > 0) {
      Thread.sleep(Math.min(remainTime + 1, 100));
      remainTime -= System.currentTimeMillis() - startTime;

      try {
        // Check if process is terminated by polling the exitValue, which throws
        // IllegalThreadStateException if not terminated.
        return commandProcess.exitValue();
      } catch (IllegalThreadStateException ignored) {
      }
    }

    commandProcess.destroy();
    throw new IOException("cert provider command timed out");
  }
}
