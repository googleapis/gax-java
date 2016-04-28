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

/**
 * A base settings class to configure a service API class.
 *
 * <p>This base class includes settings that are applicable to all services, which includes
 * things like connection settings (or channel), executor, and identifiers for http
 * headers.
 *
 * <p>If no executor is provided, then a default one will be created.
 *
 * <p>There are two ways to configure the channel that will be used:
 *
 * <p><ol>
 * <li>Provide an instance of ConnectionSettings. In this case, an instance of
 *   ManagedChannel will be created automatically. The value of shouldAutoCloseChannel
 *   will then be true, indicating to the service API class that it should call shutdown()
 *   on the channel when it is close()'d. When this channel is created, it will use
 *   the executor of this settings class, which will either be a provided one or a default
 *   one.
 * <li>Provide a ManagedChannel directly, specifying the value of shouldAutoClose
 *   explicitly. When shouldAutoClose is true, the service API class will close the
 *   passed-in channel; when false, the service API class will not close it. Since the
 *   ManagedChannel is passed in, its executor may or might not have any relation
 *   to the executor in this settings class.
 * </ol>
 *
 * <p>The client lib header and generator header values are used to form a value that
 * goes into the http header of requests to the service.
*/
public abstract class ServiceApiSettings {

  private final ChannelProvider channelProvider;
  private final ExecutorProvider executorProvider;

  private final String generatorName;
  private final String generatorVersion;
  private final String clientLibName;
  private final String clientLibVersion;

  /**
   * Constructs an instance of ServiceApiSettings.
   */
  protected ServiceApiSettings(
      ChannelProvider channelProvider,
      ExecutorProvider executorProvider,
      String generatorName,
      String generatorVersion,
      String clientLibName,
      String clientLibVersion) {
    this.channelProvider = channelProvider;
    this.executorProvider = executorProvider;
    this.clientLibName = clientLibName;
    this.clientLibVersion = clientLibVersion;
    this.generatorName = generatorName;
    this.generatorVersion = generatorVersion;
  }

  /**
   * Return the channel provider. If no channel provider was set, the default channel provider will
   * be returned.
   */
  public final ChannelProvider getChannelProvider() {
    return channelProvider;
  }

  /**
   * Return the executor provider. It no executor provider was set, the default executor provider
   * will be returned.
   */
  public final ExecutorProvider getExecutorProvider() {
    return executorProvider;
  }

  public abstract static class Builder {

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

    protected Builder(ConnectionSettings connectionSettings) {
      this();
      channelProvider = createChannelProvider(connectionSettings);
    }

    /**
     * Create a builder from a ServiceApiSettings object.
     */
    protected Builder(ServiceApiSettings settings) {
      this();
      this.channelProvider = settings.channelProvider;
      this.executorProvider = settings.executorProvider;
      this.clientLibName = settings.clientLibName;
      this.clientLibVersion = settings.clientLibVersion;
      this.serviceGeneratorName = settings.generatorName;
      this.serviceGeneratorVersion = settings.generatorVersion;
    }

    private Builder() {
      clientLibName = DEFAULT_CLIENT_LIB_NAME;
      clientLibVersion = DEFAULT_VERSION;
      serviceGeneratorName = DEFAULT_GENERATOR_NAME;
      serviceGeneratorVersion = DEFAULT_VERSION;

      executorProvider =
          new ExecutorProvider() {
            @Override
            public ScheduledExecutorService getOrBuildExecutor() {
              return MoreExecutors.getExitingScheduledExecutorService(
                  new ScheduledThreadPoolExecutor(DEFAULT_EXECUTOR_THREADS));
            }

            @Override
            public boolean shouldAutoClose() {
              return true;
            }
          };
    }

    /**
     * Sets the executor to use for channels, retries, and bundling.
     *
     * If multiple Api objects will use this executor, shouldAutoClose must be set to false to
     * prevent the {@link ExecutorProvider} from throwing an {@link IllegalStateException}. See
     * {@link ExecutorProvider} for more details.
     */
    public Builder provideExecutorWith(
        final ScheduledExecutorService executor, final boolean shouldAutoClose) {
      executorProvider =
          new ExecutorProvider() {
            private volatile boolean executorProvided = false;

            @Override
            public ScheduledExecutorService getOrBuildExecutor() {
              if (executorProvided) {
                if (shouldAutoClose) {
                  throw new IllegalStateException(
                      "A fixed executor cannot be re-used when shouldAutoClose is set to true. "
                          + "Try calling provideExecutorWith with shouldAutoClose set to false "
                          + "or using the default executor.");
                }
              } else {
                executorProvided = true;
              }
              return executor;
            }

            @Override
            public boolean shouldAutoClose() {
              return shouldAutoClose;
            }
          };
      return this;
    }

    /**
     * Sets a channel for this ServiceApiSettings to use. This prevents a channel from being
     * created.
     *
     * See class documentation for more details on channels.
     *
     * If multiple Api objects will use this channel, shouldAutoClose must be set to false to
     * prevent the {@link ChannelProvider} from throwing an {@link IllegalStateException}. See
     * {@link ChannelProvider} for more details.
     */
    public Builder provideChannelWith(final ManagedChannel channel, final boolean shouldAutoClose) {
      channelProvider = createChannelProvider(channel, shouldAutoClose);
      return this;
    }

    /**
     * Provides the connection settings necessary to create a channel.
     */
    public Builder provideChannelWith(
        final ConnectionSettings settings) {
      channelProvider = createChannelProvider(settings);
      return this;
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

    public ChannelProvider getChannelProvider() {
      return channelProvider;
    }

    public ExecutorProvider getExecutorProvider() {
      return executorProvider;
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

    public abstract ServiceApiSettings build() throws IOException;

    private ChannelProvider createChannelProvider(final ConnectionSettings settings) {
      return new ChannelProvider() {
        @Override
        public ManagedChannel getOrBuildChannel(Executor executor) throws IOException {
          List<ClientInterceptor> interceptors = Lists.newArrayList();
          interceptors.add(new ClientAuthInterceptor(settings.getCredentials(), executor));
          interceptors.add(new HeaderInterceptor(serviceHeader()));

          return NettyChannelBuilder.forAddress(settings.getServiceAddress(), settings.getPort())
              .negotiationType(NegotiationType.TLS)
              .intercept(interceptors)
              .executor(executor)
              .build();
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
          return String.format(
              "%s/%s;%s/%s;gax/%s;java/%s",
              clientLibName,
              clientLibVersion,
              serviceGeneratorName,
              serviceGeneratorVersion,
              gaxVersion,
              javaVersion);
        }
      };
    }

    private ChannelProvider createChannelProvider(final ManagedChannel channel,
                                                  final boolean shouldAutoClose) {
      return new ChannelProvider() {
        private boolean channelProvided = false;

        @Override
        public ManagedChannel getOrBuildChannel(Executor executor) {
          if (channelProvided) {
            if (shouldAutoClose) {
              throw new IllegalStateException(
                  "A fixed channel cannot be re-used when shouldAutoClose is set to true. "
                      + "Try calling provideChannelWith with shouldAutoClose set to false, or "
                      + "using a channel created from a ConnectionSettings object.");
            }
          } else {
            channelProvided = true;
          }
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
    }
  }
}
