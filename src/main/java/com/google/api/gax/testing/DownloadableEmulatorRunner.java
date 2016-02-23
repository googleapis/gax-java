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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class to start and run an emulator from a download URL.
 */
public class DownloadableEmulatorRunner implements EmulatorRunner {
  private final List<String> commandText;
  private final String md5CheckSum;
  private final URL downloadUrl;
  private final String fileName;
  private Process process;
  private static final Logger log = Logger.getLogger(DownloadableEmulatorRunner.class.getName());

  public DownloadableEmulatorRunner(List<String> commandText, URL downloadUrl, String md5CheckSum) {
    this.commandText = commandText;
    this.md5CheckSum = md5CheckSum;
    this.downloadUrl = downloadUrl;
    String[] splitUrl = downloadUrl.toString().split("/");
    this.fileName = splitUrl[splitUrl.length - 1];
  }

  @Override
  public boolean isAvailable() {
    try {
      downloadZipFile();
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public void start() throws IOException {
    Path emulatorPath = downloadEmulator();
    process = CommandWrapper.create()
        .command(commandText)
        .directory(emulatorPath)
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

  private Path downloadEmulator() throws IOException {
    // Retrieve the file name from the download link
    String[] splittedUrl = downloadUrl.toString().split("/");
    String fileName = splittedUrl[splittedUrl.length - 1];

    // Each run is associated with its own folder that is deleted once test completes.
    Path emulatorPath = Files.createTempDirectory(fileName.split("\\.")[0]);
    File emulatorFolder = emulatorPath.toFile();
    emulatorFolder.deleteOnExit();

    File zipFile = downloadZipFile();
    // Unzip the emulator
    try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFile))) {
      if (log.isLoggable(Level.FINE)) {
        log.fine("Unzipping emulator");
      }
      ZipEntry entry = zipIn.getNextEntry();
      while (entry != null) {
        File filePath = new File(emulatorPath.toFile(), entry.getName());
        if (!entry.isDirectory()) {
          extractFile(zipIn, filePath);
        } else {
          filePath.mkdir();
        }
        zipIn.closeEntry();
        entry = zipIn.getNextEntry();
      }
    }
    return emulatorPath;
  }

  private File downloadZipFile() throws IOException {
    // Check if we already have a local copy of the emulator and download it if not.
    File zipFile = new File(System.getProperty("java.io.tmpdir"), fileName);
    if (!zipFile.exists() || (md5CheckSum != null && !md5CheckSum.equals(md5(zipFile)))) {
      if (log.isLoggable(Level.FINE)) {
        log.fine("Fetching emulator");
      }
      ReadableByteChannel rbc = Channels.newChannel(downloadUrl.openStream());
      try (FileOutputStream fos = new FileOutputStream(zipFile)) {
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
      }
    } else {
      if (log.isLoggable(Level.FINE)) {
        log.fine("Using cached emulator");
      }
    }
    return zipFile;
  }

  private void extractFile(ZipInputStream zipIn, File filePath) throws IOException {
    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
      byte[] bytesIn = new byte[1024];
      int read;
      while ((read = zipIn.read(bytesIn)) != -1) {
        bos.write(bytesIn, 0, read);
      }
    }
  }

  private static String md5(File zipFile) throws IOException {
    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      try (InputStream is = new BufferedInputStream(new FileInputStream(zipFile))) {
        byte[] bytes = new byte[4 * 1024 * 1024];
        int len;
        while ((len = is.read(bytes)) >= 0) {
          md5.update(bytes, 0, len);
        }
      }
      return String.format("%032x", new BigInteger(1, md5.digest()));
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }
}
