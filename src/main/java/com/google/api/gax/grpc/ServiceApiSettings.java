package com.google.api.gax.grpc;

import com.google.api.gax.core.ConnectionSettings;
import com.google.api.gax.core.RetryParams;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * A settings class to configure a service api class.
 *
 * A note on channels: whichever service API class that this instance of ServiceApiSettings
 * is passed to will call shutdown() on the channel provided by {@link getChannel}.
 * Setting a channel is intended for use by unit tests to override the channel,
 * and should not be used in production.
 */
public class ServiceApiSettings {

  private String clientLibName;
  private String clientLibVersion;
  private String serviceGeneratorName;
  private String serviceGeneratorVersion;
  private ChannelProvider channelProvider;
  private ExecutorProvider executorProvider;
  private final ImmutableList<? extends ApiCallSettings> allCallSettings;

  /**
   * The number of threads to use with the default executor.
   */
  public static final int DEFAULT_EXECUTOR_THREADS = 4;

  /**
   * Default names and versions of the client and the service generator.
   */
  private static final String DEFAULT_GENERATOR_NAME = "gapic";
  private static final String DEFAULT_CLIENT_LIB_NAME = "gax";
  private static final String DEFAULT_VERSION = "0.1.0";

  /**
   * Constructs an instance of ServiceApiSettings.
   */
  public ServiceApiSettings(ImmutableList<? extends ApiCallSettings> allCallSettings) {
    clientLibName = DEFAULT_CLIENT_LIB_NAME;
    clientLibVersion = DEFAULT_VERSION;
    serviceGeneratorName = DEFAULT_GENERATOR_NAME;
    serviceGeneratorVersion = DEFAULT_VERSION;

    channelProvider = new ChannelProvider() {
      @Override
      public ManagedChannel getChannel(Executor executor) {
        throw new RuntimeException("No Channel or ConnectionSettings provided.");
      }
      @Override
      public boolean shouldAutoClose() {
        return true;
      }
    };
    executorProvider = new ExecutorProvider() {
      private ScheduledExecutorService executor = null;
      @Override
      public ScheduledExecutorService getExecutor() {
        if (executor != null) {
          return executor;
        }
        executor = MoreExecutors.getExitingScheduledExecutorService(
            new ScheduledThreadPoolExecutor(DEFAULT_EXECUTOR_THREADS));
        return executor;
      }
    };
    this.allCallSettings = allCallSettings;
  }

  private interface ChannelProvider {
    boolean shouldAutoClose();
    ManagedChannel getChannel(Executor executor) throws IOException;
  }

  /**
   * Sets a channel for this ServiceApiSettings to use. This prevents a channel
   * from being created.
   *
   * See class documentation for more details on channels.
   */
  public ServiceApiSettings provideChannelWith(
      final ManagedChannel channel, final boolean shouldAutoClose) {
    channelProvider = new ChannelProvider() {
      @Override
      public ManagedChannel getChannel(Executor executor) {
        return channel;
      }
      @Override
      public boolean shouldAutoClose() {
        return shouldAutoClose;
      }
    };
    return this;
  }

  /**
   * Provides the connection settings necessary to create a channel.
   */
  public ServiceApiSettings provideChannelWith(
      final ConnectionSettings settings,final boolean shouldAutoClose) {
    channelProvider = new ChannelProvider() {
      private ManagedChannel channel = null;
      @Override
      public ManagedChannel getChannel(Executor executor) throws IOException {
        if (channel != null) {
          return channel;
        }

        List<ClientInterceptor> interceptors = Lists.newArrayList();
        interceptors.add(new ClientAuthInterceptor(settings.getCredentials(), executor));
        interceptors.add(new HeaderInterceptor(serviceHeader()));

        channel = NettyChannelBuilder.forAddress(settings.getServiceAddress(), settings.getPort())
            .negotiationType(NegotiationType.TLS)
            .intercept(interceptors)
            .build();
        return channel;
      }

      @Override
      public boolean shouldAutoClose() {
        return shouldAutoClose;
      }

      private String serviceHeader() {
        // GAX version only works when the package is invoked as a jar. Otherwise returns null.
        String gaxVersion = ChannelProvider.class.getPackage().getImplementationVersion();
        if (gaxVersion == null) {
          gaxVersion = DEFAULT_VERSION;
        }
        String javaVersion = Runtime.class.getPackage().getImplementationVersion();
        return String.format("%s/%s;%s/%s;gax/%s;java/%s",
            clientLibName, clientLibVersion, serviceGeneratorName, serviceGeneratorVersion,
            gaxVersion, javaVersion);
      }
    };
    return this;
  }

  /**
   * The channel used to send requests to the service.
   *
   * If no channel was set, a default channel will be instantiated, using
   * the connection settings provided.
   *
   * See class documentation for more details on channels.
   */
  public ManagedChannel getChannel() throws IOException {
    return channelProvider.getChannel(getExecutor());
  }

  /**
   * Returns true if the channel should be automatically closed with the API wrapper class.
   */
  public boolean shouldAutoCloseChannel() {
    return channelProvider.shouldAutoClose();
  }

  private interface ExecutorProvider {
    ScheduledExecutorService getExecutor();
  }

  /**
   * Sets the executor to use for channels, retries, and bundling.
   *
   * It is up to the user to terminate the {@code Executor} when it is no longer needed.
   */
  public ServiceApiSettings setExecutor(final ScheduledExecutorService executor) {
    executorProvider = new ExecutorProvider() {
      @Override
      public ScheduledExecutorService getExecutor() {
        return executor;
      }
    };
    return this;
  }

  /**
   * The executor to be used by the client.
   *
   * If no executor was set, a default {@link java.util.concurrent.ScheduledThreadPoolExecutor}
   * with {@link DEFAULT_EXECUTOR_THREADS} will be instantiated.
   *
   * The default executor is guaranteed to not prevent JVM from normally exiting,
   * but may wait for up to 120 seconds after all non-daemon threads exit to give received tasks
   * time to complete.
   *
   * If this behavior is not desirable, the user may specify a custom {@code Executor}.
   */
  public ScheduledExecutorService getExecutor() {
    return executorProvider.getExecutor();
  }

  /**
   * Returns all of the API call settings of this API, which can be individually configured.
   */
  public ImmutableList<? extends ApiCallSettings> allCallSettings() {
    return allCallSettings;
  }

  /**
   * Sets the retry codes on all of the methods of the API.
   */
  public ServiceApiSettings setRetryableCodesOnAllMethods(Status.Code... codes) {
    return setRetryableCodesOnAllMethods(Sets.newHashSet(codes));
  }

  /**
   * Sets the retry codes on all of the methods of the API.
   */
  public ServiceApiSettings setRetryableCodesOnAllMethods(Set<Status.Code> retryableCodes) {
    for (ApiCallSettings settings : allCallSettings) {
      settings.setRetryableCodes(retryableCodes);
    }
    return this;
  }

  /**
   * Sets the retry params for all of the methods of the API.
   */
  public ServiceApiSettings setRetryParamsOnAllMethods(RetryParams retryParams) {
    for (ApiCallSettings settings : allCallSettings) {
      settings.setRetryParams(retryParams);
    }
    return this;
  }

  /**
   * Sets the generator name and version for the GRPC custom header.
   */
  public ServiceApiSettings setGeneratorHeader(String name, String version) {
    this.serviceGeneratorName = name;
    this.serviceGeneratorVersion = version;
    return this;
  }

  /**
   * Sets the client library name and version for the GRPC custom header.
   */
  public ServiceApiSettings setClientLibHeader(String name, String version) {
    this.clientLibName = name;
    this.clientLibVersion = version;
    return this;
  }
}
