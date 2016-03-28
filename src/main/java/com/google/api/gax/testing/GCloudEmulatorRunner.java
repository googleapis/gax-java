/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.api.gax.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Utility class to start and run an emulator from gcloud SDK.
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
    process = CommandWrapper.create()
        .command(commandText)
        .start();
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

  private boolean isEmulatorUpdateToDate()
      throws IOException, InterruptedException {
    String currentVersion = installedEmulatorVersion(versionPrefix);
    return currentVersion != null && currentVersion.compareTo(minVersion) >= 0;
  }

  private String installedEmulatorVersion(String versionPrefix)
      throws IOException, InterruptedException {
    Process process =
        CommandWrapper.create().command(Arrays.asList("gcloud", "version"))
        .redirectErrorStream().start();
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
