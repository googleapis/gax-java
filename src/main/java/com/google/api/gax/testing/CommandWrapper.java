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
