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
    process = CommandWrapper.create().command(commandText).directory(emulatorPath).start();
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
