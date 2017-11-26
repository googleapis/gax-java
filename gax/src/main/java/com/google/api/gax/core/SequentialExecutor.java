/*
 * Copyright 2017, Google LLC All rights reserved.
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
 *     * Neither the name of Google LLC nor the names of its
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
package com.google.api.gax.core;

import com.google.api.core.InternalApi;
import com.google.common.base.Preconditions;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;

/**
 * Extracted from guava 23.4
 *
 * <p>Executor ensuring that all Runnables submitted are executed in order, using the provided
 * Executor, and sequentially such that no two will ever be running at the same time.
 *
 * <p>Tasks submitted to {@link #execute(Runnable)} are executed in FIFO order.
 *
 * <p>The execution of tasks is done by one thread as long as there are tasks left in the queue.
 * When a task is {@linkplain Thread#interrupt interrupted}, execution of subsequent tasks
 * continues. See {@link QueueWorker#workOnQueue} for details.
 *
 * <p>{@code RuntimeException}s thrown by tasks are simply logged and the executor keeps trucking.
 * If an {@code Error} is thrown, the error will propagate and execution will stop until it is
 * restarted by a call to {@link #execute}.
 */
@InternalApi("For internal gax use only")
public final class SequentialExecutor implements Executor {
  private static final Logger log = Logger.getLogger(SequentialExecutor.class.getName());

  /** Underlying executor that all submitted Runnable objects are run on. */
  private final Executor executor;

  @GuardedBy("queue")
  private final Queue<Runnable> queue = new ArrayDeque<>();

  @GuardedBy("queue")
  private boolean isWorkerRunning = false;

  private final QueueWorker worker = new QueueWorker();

  public SequentialExecutor(Executor executor) {
    this.executor = Preconditions.checkNotNull(executor);
  }

  /**
   * Adds a task to the queue and makes sure a worker thread is running.
   *
   * <p>If this method throws, e.g. a {@code RejectedExecutionException} from the delegate executor,
   * execution of tasks will stop until a call to this method is made.
   */
  @Override
  public void execute(Runnable task) {
    synchronized (queue) {
      queue.add(task);
      if (isWorkerRunning) {
        return;
      }
      isWorkerRunning = true;
    }
    startQueueWorker();
  }

  /**
   * Starts a worker. This should only be called if:
   *
   * <ul>
   *   <li>{@code suspensions == 0}
   *   <li>{@code isWorkerRunning == true}
   *   <li>{@code !queue.isEmpty()}
   *   <li>the {@link #worker} lock is not held
   * </ul>
   */
  private void startQueueWorker() {
    boolean executionRejected = true;
    try {
      executor.execute(worker);
      executionRejected = false;
    } finally {
      if (executionRejected) {
        // The best we can do is to stop executing the queue, but reset the state so that
        // execution can be resumed later if the caller so wishes.
        synchronized (queue) {
          isWorkerRunning = false;
        }
      }
    }
  }

  /** Worker that runs tasks from {@link #queue} until it is empty. */
  private final class QueueWorker implements Runnable {
    @Override
    public void run() {
      try {
        workOnQueue();
      } catch (Error e) {
        synchronized (queue) {
          isWorkerRunning = false;
        }
        throw e;
        // The execution of a task has ended abnormally.
        // We could have tasks left in the queue, so should perhaps try to restart a worker,
        // but then the Error will get delayed if we are using a direct (same thread) executor.
      }
    }

    /**
     * Continues executing tasks from {@link #queue} until it is empty.
     *
     * <p>The thread's interrupt bit is cleared before execution of each task.
     *
     * <p>If the Thread in use is interrupted before or during execution of the tasks in {@link
     * #queue}, the Executor will complete its tasks, and then restore the interruption. This means
     * that once the Thread returns to the Executor that this Executor composes, the interruption
     * will still be present. If the composed Executor is an ExecutorService, it can respond to
     * shutdown() by returning tasks queued on that Thread after {@link #worker} drains the queue.
     */
    private void workOnQueue() {
      boolean interruptedDuringTask = false;

      try {
        while (true) {
          // Remove the interrupt bit before each task. The interrupt is for the "current task" when
          // it is sent, so subsequent tasks in the queue should not be caused to be interrupted
          // by a previous one in the queue being interrupted.
          interruptedDuringTask |= Thread.interrupted();
          Runnable task;
          synchronized (queue) {
            task = queue.poll();
            if (task == null) {
              isWorkerRunning = false;
              return;
            }
          }
          try {
            task.run();
          } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Exception while executing runnable " + task, e);
          }
        }
      } finally {
        // Ensure that if the thread was interrupted at all while processing the task queue, it
        // is returned to the delegate Executor interrupted so that it may handle the
        // interruption if it likes.
        if (interruptedDuringTask) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
