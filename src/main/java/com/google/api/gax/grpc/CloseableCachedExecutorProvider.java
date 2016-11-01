package com.google.api.gax.grpc;

import java.util.concurrent.ScheduledExecutorService;

public class CloseableCachedExecutorProvider implements ExecutorProvider {
  private final ExecutorProvider innerProvider;
  private ScheduledExecutorService cachedExecutor;

  private CloseableCachedExecutorProvider(ExecutorProvider innerProvider, ScheduledExecutorService cachedExecutor) {
    this.innerProvider = innerProvider;
    this.cachedExecutor = cachedExecutor;
  }

  @Override
  public boolean shouldAutoClose() {
    return false;
  }

  @Override
  public ScheduledExecutorService getExecutor() {
    if (cachedExecutor == null) {
      cachedExecutor = innerProvider.getExecutor();
    }
    return cachedExecutor;
  }

  @Override
  public Builder toBuilder() {
    return new Builder(this);
  }

  public void close() {
    if (cachedExecutor != null) {
      cachedExecutor.shutdown();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder implements ExecutorProvider.Builder {
    private ExecutorProvider innerProvider;
    private ScheduledExecutorService cachedExecutor;

    private Builder() {}

    private Builder(CloseableCachedExecutorProvider provider) {
      this.innerProvider = provider.innerProvider;
      this.cachedExecutor = provider.cachedExecutor;
    }

    public Builder setInnerProvider(ExecutorProvider innerProvider) {
      this.innerProvider = innerProvider;
      return this;
    }

    @Override
    public CloseableCachedExecutorProvider build() {
      return new CloseableCachedExecutorProvider(innerProvider, cachedExecutor);
    }
  }
}
