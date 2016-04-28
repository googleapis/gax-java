package com.google.api.gax.grpc;

import com.google.api.gax.core.ConnectionSettings;

import io.grpc.ManagedChannel;

import java.io.IOException;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * Provides an interface to hold and build the channel that will be used. If the channel does not
 * already exist, it will be constructed when {@link #getOrBuildChannel} is called.
 *
 * Implementations of {@link ChannelProvider} may choose to create a new {@link ManagedChannel} for
 * each call to {@link #getOrBuildChannel}, or may return a fixed {@link ManagedChannel} instance.
 * In cases where the same {@link ManagedChannel} instance is returned, for example by a
 * {@link ChannelProvider} created using the {@link ServiceApiSettings}
 * provideChannelWith(ManagedChannel, boolean) method, and shouldAutoClose returns true, the
 * {@link #getOrBuildChannel} method will throw an {@link IllegalStateException} if it is called
 * more than once. This is to prevent the same {@link ManagedChannel} being closed prematurely when
 * it is used by multiple client objects.
 */
public interface ChannelProvider {
  /**
   * Connection settings used to build the channel. If a channel is provided directly this will be
   * set to null.
   */
  @Nullable
  ConnectionSettings connectionSettings();

  /**
   * Indicates whether the channel should be closed by the containing API class.
   */
  boolean shouldAutoClose();

  /**
   * Get the channel to be used to connect to the service. The first time this is called, if the
   * channel does not already exist, it will be created. The {@link Executor} will only be used when
   * the channel is created. For implementations returning a fixed {@link ManagedChannel} object,
   * the executor is unused.
   *
   * If the {@link ChannelProvider} is configured to return a fixed {@link ManagedChannel} object
   * and to return shouldAutoClose as true, then after the first call to {@link #getOrBuildChannel},
   * subsequent calls should throw an {@link IllegalStateException}. See interface level docs for
   * {@link ChannelProvider} for more details.
   */
  ManagedChannel getOrBuildChannel(Executor executor) throws IOException;
}
