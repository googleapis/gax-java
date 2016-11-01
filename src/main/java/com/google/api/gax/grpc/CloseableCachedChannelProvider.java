package com.google.api.gax.grpc;

import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * CloseableCachedChannelProvider calls an inner ChannelProvider the first time getChannel()
 * or getChannel(Executor) is called and caches the channel, and then returns that cached
 * channel on subsequent calls.
 *
 * Note: close() must be called on this class to clean up the channel.
 */
public class CloseableCachedChannelProvider implements ChannelProvider {
  private final ChannelProvider innerProvider;
  private ManagedChannel cachedChannel;

  private CloseableCachedChannelProvider(ChannelProvider innerProvider, ManagedChannel cachedChannel) {
    this.innerProvider = innerProvider;
    this.cachedChannel = cachedChannel;
  }

  @Override
  public boolean shouldAutoClose() {
    return false;
  }

  @Override
  public boolean needsExecutor() {
    if (cachedChannel == null) {
      return innerProvider.needsExecutor();
    } else {
      return false;
    }
  }

  @Override
  public ManagedChannel getChannel() throws IOException {
    if (cachedChannel == null) {
      cachedChannel = innerProvider.getChannel();
    }
    return cachedChannel;
  }

  @Override
  public ManagedChannel getChannel(Executor executor) throws IOException {
    if (cachedChannel == null) {
      cachedChannel = innerProvider.getChannel(executor);
      return cachedChannel;
    } else {
      throw new IllegalStateException(
          "getChannel(Executor) called when needsExecutor() is false");
    }
  }

  @Override
  public Builder toBuilder() {
    return new Builder(this);
  }

  public void close() {
    if (cachedChannel != null) {
      cachedChannel.shutdown();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder implements ChannelProvider.Builder {
    private ChannelProvider innerProvider;
    private ManagedChannel cachedChannel;

    private Builder() {}

    private Builder(CloseableCachedChannelProvider provider) {
      this.innerProvider = provider.innerProvider;
      this.cachedChannel = provider.cachedChannel;
    }

    public Builder setInnerProvider(ChannelProvider innerProvider) {
      this.innerProvider = innerProvider;
      return this;
    }

    @Override
    public CloseableCachedChannelProvider build() {
      return new CloseableCachedChannelProvider(innerProvider, cachedChannel);
    }
  }
}
