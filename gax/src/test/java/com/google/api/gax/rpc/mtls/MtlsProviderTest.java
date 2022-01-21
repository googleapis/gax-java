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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MtlsProviderTest {
  private static class TestCertProviderCommandProcess extends Process {
    private boolean runForever;
    private int exitValue;

    public TestCertProviderCommandProcess(int exitValue, boolean runForever) {
      this.runForever = runForever;
      this.exitValue = exitValue;
    }

    @Override
    public OutputStream getOutputStream() {
      return null;
    }

    @Override
    public InputStream getInputStream() {
      return null;
    }

    @Override
    public InputStream getErrorStream() {
      return null;
    }

    @Override
    public int waitFor() throws InterruptedException {
      return 0;
    }

    @Override
    public int exitValue() {
      if (runForever) {
        throw new IllegalThreadStateException();
      }
      return exitValue;
    }

    @Override
    public void destroy() {}
  }

  static class TestProcessProvider implements MtlsProvider.ProcessProvider {
    private int exitCode;

    public TestProcessProvider(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public Process createProcess(InputStream metadata) throws IOException {
      return new TestCertProviderCommandProcess(exitCode, false);
    }
  }

  @Test
  public void testUseMtlsEndpointAlways() {
    MtlsProvider mtlsProvider =
        new MtlsProvider(
            name -> name.equals("GOOGLE_API_USE_MTLS_ENDPOINT") ? "always" : "false",
            new TestProcessProvider(0),
            "/path/to/missing/file");
    assertEquals(
        MtlsProvider.MtlsEndpointUsagePolicy.ALWAYS, mtlsProvider.getMtlsEndpointUsagePolicy());
  }

  @Test
  public void testUseMtlsEndpointAuto() {
    MtlsProvider mtlsProvider =
        new MtlsProvider(
            name -> name.equals("GOOGLE_API_USE_MTLS_ENDPOINT") ? "auto" : "false",
            new TestProcessProvider(0),
            "/path/to/missing/file");
    assertEquals(
        MtlsProvider.MtlsEndpointUsagePolicy.AUTO, mtlsProvider.getMtlsEndpointUsagePolicy());
  }

  @Test
  public void testUseMtlsEndpointNever() {
    MtlsProvider mtlsProvider =
        new MtlsProvider(
            name -> name.equals("GOOGLE_API_USE_MTLS_ENDPOINT") ? "never" : "false",
            new TestProcessProvider(0),
            "/path/to/missing/file");
    assertEquals(
        MtlsProvider.MtlsEndpointUsagePolicy.NEVER, mtlsProvider.getMtlsEndpointUsagePolicy());
  }

  @Test
  public void testUseMtlsClientCertificateTrue() {
    MtlsProvider mtlsProvider =
        new MtlsProvider(
            name -> name.equals("GOOGLE_API_USE_MTLS_ENDPOINT") ? "auto" : "true",
            new TestProcessProvider(0),
            "/path/to/missing/file");
    assertTrue(mtlsProvider.useMtlsClientCertificate());
  }

  @Test
  public void testUseMtlsClientCertificateFalse() {
    MtlsProvider mtlsProvider =
        new MtlsProvider(
            name -> name.equals("GOOGLE_API_USE_MTLS_ENDPOINT") ? "auto" : "false",
            new TestProcessProvider(0),
            "/path/to/missing/file");
    assertFalse(mtlsProvider.useMtlsClientCertificate());
  }

  @Test
  public void testGetKeyStore() throws IOException {
    MtlsProvider mtlsProvider =
        new MtlsProvider(
            name -> name.equals("GOOGLE_API_USE_MTLS_ENDPOINT") ? "always" : "false",
            new TestProcessProvider(0),
            "/path/to/missing/file");
    assertNull(mtlsProvider.getKeyStore());
  }

  @Test
  public void testGetKeyStoreNonZeroExitCode()
      throws IOException, InterruptedException, GeneralSecurityException {
    try {
      InputStream metadata =
          this.getClass()
              .getClassLoader()
              .getResourceAsStream("com/google/api/gax/rpc/mtls/mtlsCertAndKey.pem");
      MtlsProvider.getKeyStore(metadata, new TestProcessProvider(1));
      fail("should throw an exception");
    } catch (IOException e) {
      assertTrue(
          "expected to fail with nonzero exit code",
          e.getMessage().contains("Cert provider command failed with exit code: 1"));
    }
  }

  @Test
  public void testExtractCertificateProviderCommand() throws IOException {
    InputStream inputStream =
        this.getClass()
            .getClassLoader()
            .getResourceAsStream("com/google/api/gax/rpc/mtls/mtls_context_aware_metadata.json");
    List<String> command = MtlsProvider.extractCertificateProviderCommand(inputStream);
    assertEquals(2, command.size());
    assertEquals("some_binary", command.get(0));
    assertEquals("some_argument", command.get(1));
  }

  @Test
  public void testRunCertificateProviderCommandSuccess() throws IOException, InterruptedException {
    Process certCommandProcess = new TestCertProviderCommandProcess(0, false);
    int exitValue = MtlsProvider.runCertificateProviderCommand(certCommandProcess, 100);
    assertEquals(0, exitValue);
  }

  @Test
  public void testRunCertificateProviderCommandTimeout() throws InterruptedException {
    Process certCommandProcess = new TestCertProviderCommandProcess(0, true);
    try {
      MtlsProvider.runCertificateProviderCommand(certCommandProcess, 100);
      fail("should throw an exception");
    } catch (IOException e) {
      assertTrue(
          "expected to fail with timeout",
          e.getMessage().contains("cert provider command timed out"));
    }
  }
}
