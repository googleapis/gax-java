package com.google.api.gax.grpc;

import com.google.api.gax.core.ConnectionSettings;
import com.google.api.gax.core.RetrySettings;
import com.google.common.collect.Lists;
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

import javax.annotation.Nullable;

/**
 * A settings class to configure a service api class.
 *
 * A note on channels: whichever service API class that this instance of ServiceApiSettings
 * is passed to will call shutdown() on the channel provided by {@link getChannel}.
 * Setting a channel is intended for use by unit tests to override the channel,
 * and should not be used in production.
 */
public class ServiceApiSettings {

  private final ManagedChannel channel;
  private final boolean shouldAutoCloseChannel;
  private final ScheduledExecutorService executor;

  @Nullable
  private final ConnectionSettings connectionSettings;

  private final String generatorName;
  private final String generatorVersion;
  private final String clientLibName;
  private final String clientLibVersion;

  /**
   * Constructs an instance of ServiceApiSettings.
   */
  protected ServiceApiSettings(ManagedChannel channel,
                               boolean shouldAutoCloseChannel,
                               ScheduledExecutorService executor,
                               ConnectionSettings connectionSettings,
                               String generatorName,
                               String generatorVersion,
                               String clientLibName,
                               String clientLibVersion) {
    this.channel = channel;
    this.executor = executor;
    this.connectionSettings = connectionSettings;
    this.shouldAutoCloseChannel = shouldAutoCloseChannel;
    this.clientLibName = clientLibName;
    this.clientLibVersion = clientLibVersion;
    this.generatorName = generatorName;
    this.generatorVersion = generatorVersion;
  }

  public ManagedChannel getChannel() {
    return channel;
  }

  public ScheduledExecutorService getExecutor() {
    return executor;
  }

  public boolean shouldAutoCloseChannel() {
    return shouldAutoCloseChannel;
  }

  public static class Builder {

    // The number of threads to use with the default executor.
    private static final int DEFAULT_EXECUTOR_THREADS = 4;

    // Default names and versions of the client and the service generator.
    private static final String DEFAULT_GENERATOR_NAME = "gapic";
    private static final String DEFAULT_CLIENT_LIB_NAME = "gax";
    private static final String DEFAULT_VERSION = "0.1.0";

    private String clientLibName;
    private String clientLibVersion;
    private String serviceGeneratorName;
    private String serviceGeneratorVersion;

    private ChannelProvider channelProvider;
    private ExecutorProvider executorProvider;

    private interface ChannelProvider {
      ConnectionSettings connectionSettings();
      boolean shouldAutoClose();
      ManagedChannel getChannel(Executor executor) throws IOException;
    }

    private interface ExecutorProvider {
      ScheduledExecutorService getExecutor();
    }

    public Builder() {
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
        @Override
        public ConnectionSettings connectionSettings() {
          return null;
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
    }

    /**
     * Create a builder from a ServiceApiSettings object.
     */
    protected Builder(ServiceApiSettings settings) {
      this();
      if (settings.connectionSettings != null) {
        provideChannelWith(settings.connectionSettings);
      } else {
        provideChannelWith(settings.channel, settings.shouldAutoCloseChannel);
      }
      setClientLibHeader(settings.clientLibName, settings.clientLibVersion);
      setGeneratorHeader(settings.generatorName, settings.generatorVersion);
    }

    /**
     * Sets the executor to use for channels, retries, and bundling.
     *
     * It is up to the user to terminate the {@code Executor} when it is no longer needed.
     */
    public Builder setExecutor(final ScheduledExecutorService executor) {
      executorProvider = new ExecutorProvider() {
        @Override
        public ScheduledExecutorService getExecutor() {
          return executor;
        }
      };
      return this;
    }

    /**
     * Sets a channel for this ServiceApiSettings to use. This prevents a channel
     * from being created.
     *
     * See class documentation for more details on channels.
     */
    public Builder provideChannelWith(
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
        @Override
        public ConnectionSettings connectionSettings() {
          return null;
        }
      };
      return this;
    }

   /**
    * Provides the connection settings necessary to create a channel.
    */
   public Builder provideChannelWith(
       final ConnectionSettings settings) {
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
       public ConnectionSettings connectionSettings() {
         return settings;
       }

       @Override
       public boolean shouldAutoClose() {
         return true;
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
    public ManagedChannel getOrBuildChannel() throws IOException {
      return channelProvider.getChannel(this.getOrBuildExecutor());
    }

    /**
     * The Executor used for channels, retries, and bundling..
     * If no executor was set, a default executor will be instantiated.
     */
    public ScheduledExecutorService getOrBuildExecutor() {
      return executorProvider.getExecutor();
    }

    /**
     * Sets the generator name and version for the GRPC custom header.
     */
    public Builder setGeneratorHeader(String name, String version) {
      this.serviceGeneratorName = name;
      this.serviceGeneratorVersion = version;
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

    public String getClientLibName() {
      return clientLibName;
    }

    public String getClientLibVersion() {
      return clientLibVersion;
    }

    public String getGeneratorName() {
      return serviceGeneratorName;
    }

    public String getGeneratorVersion() {
      return serviceGeneratorVersion;
    }

    public ConnectionSettings getConnectionSettings() {
      return channelProvider.connectionSettings();
    }

    public boolean shouldAutoCloseChannel() {
      return channelProvider.shouldAutoClose();
    }

    /**
     *  Performs a merge, using only non-null fields
     */
    protected Builder applyToAllApiMethods(
        Iterable<ApiCallSettings.Builder> methodSettingsBuilders,
        ApiCallSettings.Builder newSettingsBuilder) throws Exception {
      Set<Status.Code> newRetryableCodes = newSettingsBuilder.getRetryableCodes();
      RetrySettings.Builder newRetrySettingsBuilder = newSettingsBuilder.getRetrySettingsBuilder();
      for (ApiCallSettings.Builder settingsBuilder : methodSettingsBuilders) {
        if (newRetryableCodes != null) {
          settingsBuilder.setRetryableCodes(newRetryableCodes);
        }
        if (newRetrySettingsBuilder != null) {
          settingsBuilder.getRetrySettingsBuilder().merge(newRetrySettingsBuilder);
        }
        // TODO(shinfan): Investigate on bundling and page-streaming settings.
      }
      return this;
    }

    public ServiceApiSettings build() throws IOException {
      return new ServiceApiSettings(getOrBuildChannel(),
                                    channelProvider.shouldAutoClose(),
                                    getOrBuildExecutor(),
                                    channelProvider.connectionSettings(),
                                    clientLibName,
                                    clientLibVersion,
                                    serviceGeneratorName,
                                    serviceGeneratorVersion);
    }
  }
}
