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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility class that executes system commands on both Windows and Unix.
 */
public class CommandWrapper {
  private final List<String> prefix;
  private List<String> command;
  private String nullFilename;
  private boolean redirectOutputToNull;
  private boolean redirectErrorStream;
  private boolean redirectErrorInherit;
  private Path directory;

  private CommandWrapper() {
    this.prefix = new ArrayList<>();
    if (isWindows()) {
      this.prefix.add("cmd");
      this.prefix.add("/C");
      this.nullFilename = "NUL:";
    } else {
      this.prefix.add("bash");
      this.nullFilename = "/dev/null";
    }
  }

  public CommandWrapper command(List<String> command) {
    this.command = new ArrayList<>(command.size() + this.prefix.size());
    this.command.addAll(prefix);
    this.command.addAll(command);
    return this;
  }

  public CommandWrapper redirectOutputToNull() {
    this.redirectOutputToNull = true;
    return this;
  }

  public CommandWrapper redirectErrorStream() {
    this.redirectErrorStream = true;
    return this;
  }

  public CommandWrapper redirectErrorInherit() {
    this.redirectErrorInherit = true;
    return this;
  }

  public CommandWrapper directory(Path directory) {
    this.directory = directory;
    return this;
  }

  public ProcessBuilder builder() {
    ProcessBuilder builder = new ProcessBuilder(command);
    if (redirectOutputToNull) {
      builder.redirectOutput(new File(nullFilename));
    }
    if (directory != null) {
      builder.directory(directory.toFile());
    }
    if (redirectErrorStream) {
      builder.redirectErrorStream(true);
    }
    if (redirectErrorInherit) {
      builder.redirectError(ProcessBuilder.Redirect.INHERIT);
    }
    return builder;
  }

  public Process start() throws IOException {
    return builder().start();
  }

  public static CommandWrapper create() {
    return new CommandWrapper();
  }

  public static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
  }
}
