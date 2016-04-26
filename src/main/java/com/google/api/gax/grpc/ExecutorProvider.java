package com.google.api.gax.grpc;

import java.util.concurrent.ScheduledExecutorService;

import javax.naming.OperationNotSupportedException;

/**
 * Provides an interface to hold and create the Executor to be used. If the executor does not
 * already exist, it will be constructed when {@link #getExecutor} is called.
 *
 * Implementations of ExecutorProvider may choose to create a new {@link ScheduledExecutorService}
 * for each call to {@link #getExecutor}, or may return a fixed {@link ScheduledExecutorService}
 * instance. In cases where the same {@link ScheduledExecutorService} instance is returned, for
 * example by an {@link ExecutorProvider} created using the {@link ServiceApiSettings}
 * provideExecutorWith(ScheduledExecutorService, boolean) method, and shouldAutoClose returns true,
 * the {@link #getExecutor} method will throw an {@link OperationNotSupportedException} if it is
 * called more than once. This is to prevent the same {@link ScheduledExecutorService} being closed
 * prematurely when it is used by multiple client objects.
 */
public interface ExecutorProvider {
  /**
   * Indicates whether the channel should be closed by the containing API class.
   */
  boolean shouldAutoClose();

  /**
   * Get the executor to be used to connect to the service. The first time this is called, if the
   * executor does not already exist, it will be created.
   *
   * If the {@link ExecutorProvider} is configured to return a fixed
   * {@link ScheduledExecutorService} object and to return shouldAutoClose as true, then after the
   * first call to {@link #getExecutor}, subsequent calls should throw an {@link ExecutorProvider}.
   * See interface level docs for {@link ExecutorProvider} for more details.
   */
  ScheduledExecutorService getExecutor() throws OperationNotSupportedException;
}
