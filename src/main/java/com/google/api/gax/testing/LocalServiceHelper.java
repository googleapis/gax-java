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

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.Strings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class to start and stop a local service which is used by unit testing.
 */
public class LocalServiceHelper {
  private final int port;
  private EmulatorRunner activeRunner;
  private List<EmulatorRunner> runners;
  private ProcessStreamReader processReader;
  private ProcessErrorStreamReader processErrorReader;
  private static final Logger log = Logger.getLogger(LocalServiceHelper.class.getName());

  private static final int DEFAULT_PORT = 8080;
  private static final int STREAM_READER_SLEEP_INTERVAL_IN_MS = 200;

  public static int findAvailablePort(int defaultPort) {
    try (ServerSocket tempSocket = new ServerSocket(0)) {
      return tempSocket.getLocalPort();
    } catch (IOException e) {
      return defaultPort;
    }
  }

  private static class ProcessStreamReader extends Thread {
    private final BufferedReader reader;
    private volatile boolean terminated;

    ProcessStreamReader(InputStream inputStream) {
      super("Local InputStream reader");
      setDaemon(true);
      reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    void terminate() throws IOException {
      terminated = true;
      reader.close();
      interrupt();
    }

    @Override
    public void run() {
      while (!terminated) {
        try {
          if (reader.ready()) {
            String line = reader.readLine();
            if (line == null) {
              terminated = true;
            }
          } else {
            sleep(STREAM_READER_SLEEP_INTERVAL_IN_MS);
          }
        } catch (IOException e) {
          e.printStackTrace(System.err);
        } catch (InterruptedException e) {
          break;
        }
      }
    }

    public static ProcessStreamReader start(InputStream inputStream) {
      ProcessStreamReader thread = new ProcessStreamReader(inputStream);
      thread.start();
      return thread;
    }
  }

  private static class ProcessErrorStreamReader extends Thread {
    private static final int LOG_LENGTH_LIMIT = 50000;
    private static final String LOGGING_CLASS =
        "com.google.apphosting.client.serviceapp.BaseApiServlet";

    private final BufferedReader errorReader;
    private StringBuilder currentLog;
    private Level currentLogLevel;
    private boolean collectionMode;
    private volatile boolean terminated;

    ProcessErrorStreamReader(InputStream errorStream, String blockUntil) throws IOException {
      super("Local ErrorStream reader");
      setDaemon(true);
      errorReader = new BufferedReader(new InputStreamReader(errorStream));
      if (!Strings.isNullOrEmpty(blockUntil)) {
        String line;
        do {
          line = errorReader.readLine();
        } while (line != null && !line.contains(blockUntil));
      }
    }

    void terminate() throws IOException {
      terminated = true;
      errorReader.close();
      interrupt();
    }

    @Override
    public void run() {
      String previousLine = "";
      String nextLine = "";
      while (!terminated) {
        try {
          if (errorReader.ready()) {
            previousLine = nextLine;
            nextLine = errorReader.readLine();
            if (nextLine == null) {
              terminated = true;
            } else {
              processLogLine(previousLine, nextLine);
            }
          } else {
            sleep(STREAM_READER_SLEEP_INTERVAL_IN_MS);
          }
        } catch (IOException e) {
          e.printStackTrace(System.err);
        } catch (InterruptedException e) {
          break;
        }
      }
      processLogLine(previousLine, firstNonNull(nextLine, ""));
      writeLog(currentLogLevel, currentLog);
    }

    private void processLogLine(String previousLine, String nextLine) {
      // Each log is two lines with the following format:
      //     [Date] [Time] [LOGGING_CLASS] [method]
      //     [LEVEL]: error message
      // Exceptions and stack traces are included in error stream, separated by a newline
      Level nextLogLevel = getLevel(nextLine);
      if (nextLogLevel != null) {
        writeLog(currentLogLevel, currentLog);
        currentLog = new StringBuilder();
        currentLogLevel = nextLogLevel;
        collectionMode = previousLine.contains(LOGGING_CLASS);
      } else if (collectionMode) {
        if (currentLog.length() > LOG_LENGTH_LIMIT) {
          collectionMode = false;
        } else if (currentLog.length() == 0) {
          // strip level out of the line
          currentLog.append(previousLine.split(":", 2)[1]);
          currentLog.append(System.getProperty("line.separator"));
        } else {
          currentLog.append(previousLine);
          currentLog.append(System.getProperty("line.separator"));
        }
      }
    }

    private static void writeLog(Level level, StringBuilder msg) {
      if (level != null && msg != null && msg.length() != 0) {
        log.log(level, msg.toString().trim());
      }
    }

    private static Level getLevel(String line) {
      try {
        return Level.parse(line.split(":")[0]);
      } catch (IllegalArgumentException e) {
        return null; // level wasn't supplied in this log line
      }
    }

    public static ProcessErrorStreamReader start(InputStream errorStream, String blockUntil)
        throws IOException {
      ProcessErrorStreamReader thread = new ProcessErrorStreamReader(errorStream, blockUntil);
      thread.start();
      return thread;
    }
  }

  public LocalServiceHelper(List<EmulatorRunner> runners, int port) {
    this.port = port > 0 ? port : DEFAULT_PORT;
    this.runners = runners;
  }

  /**
   * Starts the local service as a subprocess.
   * Block the the execution until |blockUntilOutput| is found from stderr of the emulator.
   * @throws IOException
   * @throws InterruptedException
   */
  public void start(String blockUntilOutput) throws IOException, InterruptedException {
    for (EmulatorRunner runner : runners) {
      // Iterate through all emulator runners until find first available runner.
      if (runner.isAvailable()) {
        activeRunner = runner;
        runner.start();
        break;
      }
    }
    if (activeRunner != null) {
      processReader = ProcessStreamReader.start(activeRunner.getProcess().getInputStream());
      processErrorReader = ProcessErrorStreamReader.start(
          activeRunner.getProcess().getErrorStream(), blockUntilOutput);
    } else {
      // No available runner found.
      throw new IOException("No available emulator runner is found.");
    }
  }

  /**
   * Stops the local service
   * @throws IOException
   * @throws InterruptedException
   */
  public void stop() throws IOException, InterruptedException {
    if (processReader != null) {
      processReader.terminate();
      processReader = null;
    }
    if (processErrorReader != null) {
      processErrorReader.terminate();
      processErrorReader = null;
    }
    if (activeRunner != null) {
      activeRunner.stop();
      activeRunner = null;
    }
  }

  public void sendPostRequest(String request) throws IOException {
    URL url = new URL("http", "localhost", this.port, request);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("POST");
    con.setDoOutput(true);
    OutputStream out = con.getOutputStream();
    out.write("".getBytes());
    out.flush();
  }
}
