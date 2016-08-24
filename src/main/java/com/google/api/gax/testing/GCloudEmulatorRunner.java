/*
 * Copyright 2016, Google Inc.
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

package com.google.api.gax.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Utility class to start and run an emulator from the Google Cloud SDK.
 */
public class GCloudEmulatorRunner implements EmulatorRunner {
  private final List<String> commandText;
  private final String versionPrefix;
  private final String minVersion;
  private Process process;

  public GCloudEmulatorRunner(List<String> commandText, String versionPrefix, String minVersion) {
    this.commandText = commandText;
    this.versionPrefix = versionPrefix;
    this.minVersion = minVersion;
  }

  @Override
  public boolean isAvailable() {
    try {
      return isGCloudInstalled() && isEmulatorUpdateToDate() && commandText.size() > 0;
    } catch (IOException | InterruptedException e) {
      e.printStackTrace(System.err);
    }
    return false;
  }

  @Override
  public void start() throws IOException {
    process = CommandWrapper.create().command(commandText).start();
  }

  @Override
  public void stop() throws InterruptedException {
    if (process != null) {
      process.destroy();
      process.waitFor();
    }
  }

  @Override
  public Process getProcess() {
    return process;
  }

  private boolean isGCloudInstalled() {
    Map<String, String> env = System.getenv();
    for (String envName : env.keySet()) {
      if (envName.equals("PATH")) {
        return env.get(envName).contains("google-cloud-sdk");
      }
    }
    return false;
  }

  private boolean isEmulatorUpdateToDate() throws IOException, InterruptedException {
    String currentVersion = installedEmulatorVersion(versionPrefix);
    return currentVersion != null && currentVersion.compareTo(minVersion) >= 0;
  }

  private String installedEmulatorVersion(String versionPrefix)
      throws IOException, InterruptedException {
    Process process =
        CommandWrapper.create()
            .command(Arrays.asList("gcloud", "version"))
            .redirectErrorStream()
            .start();
    process.waitFor();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        if (line.startsWith(versionPrefix)) {
          String[] lineComponents = line.split(" ");
          if (lineComponents.length > 1) {
            return lineComponents[1];
          }
        }
      }
      return null;
    }
  }
}
