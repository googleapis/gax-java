/*
 * Copyright 2016, Google Inc. All rights reserved.
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
package com.google.api.gax.grpc;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.PropertiesProvider;
import com.google.auth.Credentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import io.grpc.CallCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;

/**
 * InstantiatingChannelProvider is a ChannelProvider which constructs a gRPC ManagedChannel with a
 * number of configured inputs every time getChannel(...) is called. These inputs include a port, a
 * service address, and credentials.
 *
 * <p>
 * The credentials can either be supplied directly (by providing a FixedCredentialsProvider to
 * Builder.setCredentialsProvider()) or acquired implicitly from Application Default Credentials (by
 * providing a GoogleCredentialsProvider to Builder.setCredentialsProvider()).
 *
 * <p>
 * The client lib header and generator header values are used to form a value that goes into the
 * http header of requests to the service.
 */
public final class InstantiatingChannelProvider implements ChannelProvider {
  private static final String DEFAULT_VERSION = "";
  private static Properties gaxProperties = new Properties();

  private final ExecutorProvider executorProvider;
  private final CredentialsProvider credentialsProvider;
  private final String serviceAddress;
  private final int port;
  private final String clientLibName;
  private final String clientLibVersion;
  private final String generatorName;
  private final String generatorVersion;
  @Nullable private final Integer maxInboundMessageSize;

  private InstantiatingChannelProvider(
      ExecutorProvider executorProvider,
      CredentialsProvider credentialsProvider,
      String serviceAddress,
      int port,
      String clientLibName,
      String clientLibVersion,
      String generatorName,
      String generatorVersion,
      Integer maxInboundMessageSize) {
    this.executorProvider = executorProvider;
    this.credentialsProvider = credentialsProvider;
    this.serviceAddress = serviceAddress;
    this.port = port;
    this.clientLibName = clientLibName;
    this.clientLibVersion = clientLibVersion;
    this.generatorName = generatorName;
    this.generatorVersion = generatorVersion;
    this.maxInboundMessageSize = maxInboundMessageSize;
  }

  @Override
  public boolean needsExecutor() {
    return executorProvider == null;
  }

  @Override
  public ManagedChannel getChannel() throws IOException {
    if (needsExecutor()) {
      throw new IllegalStateException("getChannel() called when needsExecutor() is true");
    } else {
      return createChannel(executorProvider.getExecutor());
    }
  }

  @Override
  public ManagedChannel getChannel(Executor executor) throws IOException {
    if (!needsExecutor()) {
      throw new IllegalStateException("getChannel(Executor) called when needsExecutor() is false");
    } else {
      return createChannel(executor);
    }
  }

  private ManagedChannel createChannel(Executor executor) throws IOException {
    CallCredentials callCredentials =
        MoreCallCredentials.from(credentialsProvider.getCredentials());

    List<ClientInterceptor> interceptors = Lists.newArrayList();
    interceptors.add(new AuthInterceptor(callCredentials));
    interceptors.add(new HeaderInterceptor(serviceHeader()));

    ManagedChannelBuilder builder =
        ManagedChannelBuilder.forAddress(serviceAddress, port)
            .intercept(interceptors)
            .executor(executor);
    if (maxInboundMessageSize != null) {
      builder.maxInboundMessageSize(maxInboundMessageSize);
    }
    return builder.build();
  }

  /**
   * Gets the credentials which will be used to call the service. If the credentials have not been
   * acquired yet, then they will be acquired when this function is called.
   */
  public Credentials getCredentials() throws IOException {
    return getCredentialsProvider().getCredentials();
  }

  /**
   * The credentials to use in order to call the service. Credentials will not be acquired until
   * they are required.
   */
  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  public String getEndpoint() {
    return serviceAddress + ':' + port;
  }

  @Override
  public boolean shouldAutoClose() {
    return true;
  }

  @VisibleForTesting
  String serviceHeader() {
    if (clientLibName != null && clientLibVersion != null) {
      return String.format(
          "gl-java/%s %s/%s %s/%s gax/%s grpc/%s",
          getJavaVersion(),
          clientLibName,
          clientLibVersion,
          generatorName,
          generatorVersion,
          PropertiesProvider.getGaxVersion(),
          PropertiesProvider.getGrpcVersion());
    } else {
      return String.format(
          "gl-java/%s %s/%s gax/%s grpc/%s",
          getJavaVersion(),
          generatorName,
          generatorVersion,
          PropertiesProvider.getGaxVersion(),
          PropertiesProvider.getGrpcVersion());
    }
  }

  private static String loadGaxProperty(String key) {
    try {
      if (gaxProperties.isEmpty()) {
        gaxProperties.load(
            InstantiatingChannelProvider.class
                .getResourceAsStream("/com/google/api/gax/gax.properties"));
      }
      return gaxProperties.getProperty(key);
    } catch (IOException e) {
      e.printStackTrace(System.err);
    }
    return null;
  }

  private static String getJavaVersion() {
    String javaVersion = Runtime.class.getPackage().getImplementationVersion();
    if (javaVersion == null) {
      javaVersion = DEFAULT_VERSION;
    }
    return javaVersion;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {

    // Default names and versions of the service generator.
    private static final String DEFAULT_GENERATOR_NAME = "gapic";

    private ExecutorProvider executorProvider;
    private CredentialsProvider credentialsProvider;
    private String serviceAddress;
    private int port;
    private String clientLibName;
    private String clientLibVersion;
    private String generatorName;
    private String generatorVersion;
    private Integer maxInboundMessageSize;

    private Builder() {
      generatorName = DEFAULT_GENERATOR_NAME;
      generatorVersion = DEFAULT_VERSION;
    }

    private Builder(InstantiatingChannelProvider provider) {
      this.credentialsProvider = provider.credentialsProvider;
      this.serviceAddress = provider.serviceAddress;
      this.port = provider.port;
      this.clientLibName = provider.clientLibName;
      this.clientLibVersion = provider.clientLibVersion;
      this.generatorName = provider.generatorName;
      this.generatorVersion = provider.generatorVersion;
      this.maxInboundMessageSize = provider.maxInboundMessageSize;
    }

    /**
     * Sets the ExecutorProvider for this ChannelProvider.
     *
     * <p>
     * This is optional; if it is not provided, needsExecutor() will return true, meaning that an
     * Executor must be provided when getChannel is called on the constructed ChannelProvider
     * instance. Note: ClientSettings will automatically provide its own Executor in this
     * circumstance when it calls getChannel.
     */
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      this.executorProvider = executorProvider;
      return this;
    }

    /**
     * Sets the CredentialsProvider which will acquire the credentials for making calls to the
     * service. Credentials will not be acquired until they are required.
     */
    public Builder setCredentialsProvider(CredentialsProvider credentialsProvider) {
      this.credentialsProvider = credentialsProvider;
      return this;
    }

    /** The previously set CredentialsProvider. */
    public CredentialsProvider getCredentialsProvider() {
      return credentialsProvider;
    }

    /** Sets the endpoint used to reach the service, eg "localhost:8080". */
    public Builder setEndpoint(String endpoint) {
      int colon = endpoint.indexOf(':');
      if (colon < 0) {
        throw new IllegalArgumentException(
            String.format("invalid endpoint, expecting \"<host>:<port>\""));
      }
      this.port = Integer.parseInt(endpoint.substring(colon + 1));
      this.serviceAddress = endpoint.substring(0, colon);
      return this;
    }

    public String getEndpoint() {
      return serviceAddress + ':' + port;
    }

    /**
     * Sets the generator name and version for the GRPC custom header.
     */
    public Builder setGeneratorHeader(String name, String version) {
      this.generatorName = name;
      this.generatorVersion = version;
      return this;
    }

    /**
     * Sets the client library name and version for the GRPC custom header.
     */
    public Builder setClientLibHeader(String name, String version) {
      this.clientLibName = name;
      this.clientLibVersion = version;
      return this;
    }

    /**
     * The client library name provided previously.
     */
    public String getClientLibName() {
      return clientLibName;
    }

    /**
     * The client library version provided previously.
     */
    public String getClientLibVersion() {
      return clientLibVersion;
    }

    /**
     * The generator name provided previously.
     */
    public String getGeneratorName() {
      return generatorName;
    }

    /**
     * The generator version provided previously.
     */
    public String getGeneratorVersion() {
      return generatorVersion;
    }

    /** The maximum message size allowed to be received on the channel. */
    public Builder setMaxInboundMessageSize(Integer max) {
      this.maxInboundMessageSize = max;
      return this;
    }

    /** The maximum message size allowed to be received on the channel. */
    public Integer getMaxInboundMessageSize() {
      return maxInboundMessageSize;
    }

    public InstantiatingChannelProvider build() {
      return new InstantiatingChannelProvider(
          executorProvider,
          credentialsProvider,
          serviceAddress,
          port,
          clientLibName,
          clientLibVersion,
          generatorName,
          generatorVersion,
          maxInboundMessageSize);
    }
  }
}
